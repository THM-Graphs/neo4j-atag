package thm;

import org.neo4j.graphalgo.impl.util.PathImpl;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.List;
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

    @Procedure(mode= Mode.WRITE)
    public Stream<PathResult> characterChain(
            @Name("string to be used for building a chain of nodes") String text) {
        Path chain = characterChainInternal(text);
        return asPathResult(chain);
    }

    private Path characterChainInternal(String text) {
        return chainInternal(text, "", "Character", REL_NEXT_CHARACTER.name(), false);
    }

    @Procedure(mode= Mode.WRITE)
    public Stream<PathResult> tokenChain(
            @Name("string to be used for building a chain of nodes") String text) {
        Path chain = tokenChainInternal(text);
        return asPathResult(chain);
    }

    private Path tokenChainInternal(String text) {
        return chainInternal(text, "((?<=\\W)|(?=\\W))", "Token", REL_NEXT_TOKEN.name(), true);
    }

    @Procedure(mode= Mode.WRITE)
    public void fullChain(
            @Name("start node holding the text in a property") Node start,
            @Name("property key") String propertyKey) {

        String text = (String) start.getProperty(propertyKey);
        List<Node> characterChain = Iterables.asList(characterChainInternal(text).nodes());
        Iterable<Node> tokenChain = tokenChainInternal(text).nodes();

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
    public Stream<PathResult> chain(
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

    private static Stream<PathResult> asPathResult(Path build) {
        return Stream.of(new PathResult(build));
    }

    public static class PathResult {
        public final Path path;

        public PathResult(Path path) {
            this.path = path;
        }
    }

}
