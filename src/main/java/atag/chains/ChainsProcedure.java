package atag.chains;

import atag.util.EmptyPath;
import atag.util.ResultTypes;
import org.neo4j.graphalgo.impl.util.PathImpl;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.traversal.Evaluation;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class ChainsProcedure {

    public static final String PROPERTY_START_INDEX = "startIndex";
    public static final String PROPERTY_END_INDEX = "endIndex";
    public static final RelationshipType REL_NEXT_CHARACTER = RelationshipType.withName("NEXT_CHARACTER");
    public static final RelationshipType REL_NEXT_TOKEN = RelationshipType.withName("NEXT_TOKEN");
    public static final RelationshipType REL_TOKEN_START = RelationshipType.withName("TOKEN_START");
    public static final RelationshipType REL_TOKEN_END = RelationshipType.withName("TOKEN_END");
    @Context
    public Transaction tx;

    @Context
    public Log log;

    @Procedure(mode = Mode.WRITE)
    public Stream<ResultTypes.PathResult> characterChain(
            @Name("string to be used for building a chain of nodes") String text,
            @Name(value = "whether to add startIndex/endIndex properties", defaultValue = "true") boolean applyIndexProperties) {
        Path chain = characterChainInternal(text, applyIndexProperties);
        return asPathResult(chain);
    }

    private Path characterChainInternal(String text, boolean applyIndexProperties) {
        return chainInternal(text, "", "Character", REL_NEXT_CHARACTER.name(), applyIndexProperties);
    }

    @Procedure(mode = Mode.WRITE)
    public Stream<ResultTypes.PathResult> tokenChain(
            @Name("string to be used for building a chain of nodes") String text,
            @Name(value = "whether to add startIndex/endIndex properties", defaultValue = "true") boolean applyIndexProperties) {
        Path chain = tokenChainInternal(text, applyIndexProperties);
        return asPathResult(chain);
    }

    private Path tokenChainInternal(String text, boolean applyIndexProperties) {
        return chainInternal(text, "(?U)((?<=\\W)|(?=\\W))", "Token", REL_NEXT_TOKEN.name(), applyIndexProperties);
    }

    @Procedure(mode = Mode.WRITE)
    public void fullChain(
            @Name("start node holding the text in a property") Node start,
            @Name("property key") String propertyKey,
            @Name(value = "whether to add startIndex/endIndex properties to character nodes", defaultValue = "true") boolean applyIndexProperties) {

        String text = (String) start.getProperty(propertyKey);
        List<Node> characterChain = Iterables.asList(characterChainInternal(text, applyIndexProperties).nodes());
        Iterable<Node> tokenChain = tokenChainInternal(text, true).nodes();

        start.createRelationshipTo(characterChain.get(0), REL_NEXT_CHARACTER);

        Node first = null;
        for (Node token : tokenChain) {
            if (first == null) {
                first = token;
                start.createRelationshipTo(first, REL_NEXT_TOKEN);
            }

            int startIndex = (int) token.getProperty(PROPERTY_START_INDEX);
            int endIndex = (int) token.getProperty(PROPERTY_END_INDEX);
            token.createRelationshipTo(characterChain.get(startIndex), REL_TOKEN_START);
            token.createRelationshipTo(characterChain.get(endIndex), REL_TOKEN_END);
        }

    }

    @Procedure(mode = Mode.WRITE)
    public Stream<ResultTypes.PathResult> chain(
            @Name("string to be used for building a chain of nodes") String text,
            @Name("separator regex pattern") String regex,
            @Name("label for new nodes") String labelString,
            @Name("relationship type used for the chain") String relType,
            @Name("whether to add startIndex/endIndex properties") boolean applyIndexProperties) {
        Path build = chainInternal(text, regex, labelString, relType, applyIndexProperties);
        return asPathResult(build);
    }

    private Path chainInternal(String text, String regex, String labelString, String relType, boolean applyIndexProperties) {
        Label label = Label.label(labelString);
        RelationshipType relationshipType = RelationshipType.withName(relType);
        PathImpl.Builder builder = null;

        int index = 0;
        Node previousNode = null;
        for (String s : text.split(regex)) {
            Node node = tx.createNode(label);
            node.setProperty("text", s);

            if (applyIndexProperties) {
                node.setProperty(PROPERTY_START_INDEX, index);
            }
            index+=s.length();
            if (applyIndexProperties) {
                node.setProperty(PROPERTY_END_INDEX, index-1);
            }

            if (previousNode==null) {
                builder=new PathImpl.Builder(node);
            } else {
                Relationship rel = previousNode.createRelationshipTo(node, relationshipType);
                builder = builder.push(rel);
            }
            previousNode = node;
        }
        Path build = builder.build();
        return build;
    }

    @Procedure(mode = Mode.WRITE)
    public Stream<ResultTypes.PathResult> update(
            @Name("uuid of text node") String uuidText,
            @Name("uuid of chain element before update") String uuidBefore,
            @Name("uuid of chain element after update") String uuidAfter,
            @Name("chain fragment to replace everything between uuidBefore and uuidAfter") List<Map<String, Object>> replacement,
            @Name(value = "configuration", defaultValue = "{}") Map<String, String> config) {
        String uuidProperty = "uuid";
        Label textLabel = Label.label(config.getOrDefault("textLabel", "Text"));
        Label characterLabel = Label.label(config.getOrDefault("elementLabel", "Character"));
        RelationshipType relationshipType = RelationshipType.withName(config.getOrDefault("relationshipType", "NEXT_CHARACTER"));
        Node textNode = tx.findNode(textLabel, uuidProperty, uuidText);
        Node beforeNode = uuidBefore == null ? null : tx.findNode(characterLabel, uuidProperty, uuidBefore);
        Node afterNode = uuidBefore == null ? null : tx.findNode(characterLabel, uuidProperty, uuidAfter);

        Map<String, Node> existingNodes = getExistingNodes(uuidBefore, uuidAfter, beforeNode, afterNode, relationshipType, uuidProperty);

        Node currentNode = beforeNode == null ? textNode : beforeNode;
        PathImpl.Builder builder = null;
        boolean isFirst = true;

        for (Map<String, Object> data: replacement) {
            String uuid = Optional.ofNullable(data.get("uuid")).orElseThrow(() -> new IllegalArgumentException("uuid property is required")).toString();

            Node existingNode = existingNodes.remove(uuid);
            Relationship currentRelationship;
            if (existingNode == null) {
                Node newNode = tx.createNode(characterLabel);
                currentRelationship = currentNode.createRelationshipTo(newNode, relationshipType);
                currentNode = newNode;
            } else {
                Relationship existingRelationship = existingNode.getSingleRelationship(relationshipType, Direction.INCOMING);
                Node existingPrevious = existingRelationship.getStartNode();
                if (existingPrevious.equals(currentNode)) {
                    currentRelationship = existingRelationship;
                } else {
                    existingRelationship.delete();
                    currentRelationship = currentNode.createRelationshipTo(existingNode, relationshipType);
                }
                currentNode = existingNode;
            }
            for (Map.Entry<String, Object> e: data.entrySet()) {
                currentNode.setProperty(e.getKey(), e.getValue());
            }

            if (isFirst) {
                builder = new PathImpl.Builder(currentNode);
                isFirst = false;
            } else {
                builder = builder.push(currentRelationship);
            }
        }

        // remove leftover nodes
        for (Node n: existingNodes.values()) {
            n.getRelationships().forEach(Relationship::delete);
            n.delete();
        }

        // ensure last node is connected to afterNode
        if ((!currentNode.hasRelationship(Direction.OUTGOING, relationshipType)) && (afterNode!=null)) {
            Relationship r = afterNode.getSingleRelationship(relationshipType, Direction.INCOMING);
            if (r!=null) {
                r.delete();
            }
            currentNode.createRelationshipTo(afterNode, relationshipType);
        }


        return asPathResult(builder == null ? new EmptyPath() : builder.build());
    }

    private Map<String, Node> getExistingNodes(String uuidBefore, String uuidAfter, Node beforeNode, Node afterNode, RelationshipType relationshipType, String uuidProperty) {
        Map<String, Node> existingNodes = new HashMap<>();
        for (Node node: getExistingPathBetween(beforeNode, afterNode, relationshipType).nodes()) {
            String uuidValue = (String) node.getProperty(uuidProperty);
            if (!(uuidValue.equals(uuidBefore) || (uuidValue.equals(uuidAfter)))) {
                existingNodes.put(uuidValue, node);
            }
        }
        return existingNodes;
    }

    private Path getExistingPathBetween(Node beforeNode, Node afterNode, RelationshipType relationshipType) {
        if ((beforeNode == null) && (afterNode == null)) {
            return new EmptyPath();
        }
        Traverser traverser = tx.traversalDescription()
                .expand(PathExpanders.forTypeAndDirection(relationshipType, Direction.OUTGOING))
                .evaluator(path -> {
                    Node last = path.endNode();
                    if ((last != null ) && (last.equals(afterNode))) {
                        return Evaluation.INCLUDE_AND_PRUNE;
                    } else {
                        return Evaluation.EXCLUDE_AND_CONTINUE;
                    }
                }).traverse(beforeNode);
        return Iterables.single(traverser);
    }

    private static Stream<ResultTypes.PathResult> asPathResult(Path path) {
        return Stream.of(new ResultTypes.PathResult(path));
    }


}
