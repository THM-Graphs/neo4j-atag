package atag.export;

import apoc.result.ObjectResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.graphdb.*;
import org.neo4j.internal.helpers.collection.Iterators;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.procedure.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.Files.writeString;

public class ExporterProcedures {

    @Context
    public GraphDatabaseAPI graphDatabaseAPI;

    @Procedure
    @Description("export a graph into JGF format and write to a file")
    public Stream<ObjectResult> jgfFile(@Name("nodes") List<Node> nodes, @Name("relationships") List<Relationship> relationships, @Name("filename") String fileName) {
        ObjectMapper mapper = new ObjectMapper();
        return asFile(fileName, toJgf(mapper, nodes, relationships), mapper);
    }

    private Stream<ObjectResult> asFile(String fileName, ObjectNode jgf, ObjectMapper mapper) {
        Config config = graphDatabaseAPI.getDependencyResolver().resolveDependency(Config.class);
        Path folder = config.get(GraphDatabaseSettings.load_csv_file_url_root);

        if (fileName.contains(File.separator)) {
            throw new IllegalArgumentException("File name must not contain path separators");
        }
        Path outputPath = folder.resolve(fileName);

        try {
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jgf);
            writeString(outputPath, json);
            long size = outputPath.toFile().length();
            return Stream.of(new ObjectResult(String.format("%d bytes written to %s", size, fileName)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @UserFunction
    @Description("export a graph into JGF format and return as a string")
    public Map<String,Object> jgf(@Name("nodes") List<Node> nodes, @Name("relationships") List<Relationship> relationships) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = toJgf(mapper, nodes, relationships);
        return mapper.convertValue(root, Map.class);
    }

    @Procedure(name = "atag.export.jgf.fromNode", mode = Mode.READ)
    @Description("traverse from a start node and export the subgraph as JGF")
    @SuppressWarnings("unchecked")
    public Stream<ObjectResult> jgfFromNode(@Name("startNode") Node startNode, @Name("config") Map<String, Object> config) {
        boolean includeCharacterChain = !Boolean.FALSE.equals(config.get("includeCharacterChain"));
        List<String> annotationTypes = (List<String>) config.get("annotationTypes");
        String fileName = (String) config.get("fileName");
        Label annotationLabel = Label.label("Annotation");

        RelationshipType[] incomingTypes = { RelationshipType.withName("PART_OF") };

        List<RelationshipType> outgoing = new ArrayList<>(List.of(
                RelationshipType.withName("HAS_ANNOTATION"),
                RelationshipType.withName("NEXT_TOKEN"),
                RelationshipType.withName("REFERS_TO")
        ));
        if (includeCharacterChain) {
            outgoing.addAll(List.of(
                    RelationshipType.withName("NEXT_CHARACTER"),
                    RelationshipType.withName("TOKEN_START"),
                    RelationshipType.withName("TOKEN_END"),
                    RelationshipType.withName("STANDOFF_START"),
                    RelationshipType.withName("STANDOFF_END"),
                    RelationshipType.withName("CHARACTER_HAS_ANNOTATION")
            ));
        }
        RelationshipType[] outgoingTypes = outgoing.toArray(RelationshipType[]::new);

        Set<String> visitedNodeIds = new HashSet<>();
        Set<String> visitedRelIds = new HashSet<>();
        List<Node> nodes = new ArrayList<>();
        List<Relationship> relationships = new ArrayList<>();
        Queue<Node> queue = new LinkedList<>();

        visitedNodeIds.add(startNode.getElementId());
        nodes.add(startNode);
        queue.add(startNode);

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            for (Relationship rel : current.getRelationships(Direction.INCOMING, incomingTypes)) {
                visitNeighbor(rel, current, annotationTypes, annotationLabel, visitedRelIds, relationships, visitedNodeIds, nodes, queue);
            }
            for (Relationship rel : current.getRelationships(Direction.OUTGOING, outgoingTypes)) {
                visitNeighbor(rel, current, annotationTypes, annotationLabel, visitedRelIds, relationships, visitedNodeIds, nodes, queue);
            }
        }

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = toJgf(mapper, nodes, relationships);
        if (fileName != null) {
            return asFile(fileName, root, mapper);
        } else {
            return Stream.of(new ObjectResult(mapper.convertValue(root, Map.class)));
        }
    }

    private void visitNeighbor(Relationship rel, Node current, List<String> annotationTypes, Label annotationLabel,
                               Set<String> visitedRelIds, List<Relationship> relationships,
                               Set<String> visitedNodeIds, List<Node> nodes, Queue<Node> queue) {
        Node other = rel.getOtherNode(current);
        if (annotationTypes != null && other.hasLabel(annotationLabel)) {
            String type = (String) other.getProperty("type", null);
            if (type == null || !annotationTypes.contains(type)) {
                return;
            }
        }
        if (visitedRelIds.add(rel.getElementId())) {
            relationships.add(rel);
        }
        if (visitedNodeIds.add(other.getElementId())) {
            nodes.add(other);
            queue.add(other);
        }
    }

    private ObjectNode toJgf(ObjectMapper mapper, List<Node> nodes, List<Relationship> relationships) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode graph = root.putObject("graph");

        //root.put("graph", "exported_graph");
        graph.put("label", OffsetDateTime.now().toString());
//            graph.put("type", "graph");
        //root.put("label", "Neo4j Export");
        graph.put("directed", true);

        ObjectNode jsonNodes = graph.putObject("nodes");
        ArrayNode jsonRelationships = graph.putArray("edges");

        for (Node node : nodes) {
            ObjectNode jsonNode = jsonNodes.putObject(node.getElementId());
/*
                // use json array for labels - note compliant with JGF spec
                ArrayNode labels = jsonNode.putArray("label");
                node.getLabels().forEach(l-> labels.add(l.name()));
*/
            jsonNode.put("label", Iterators.stream(node.getLabels().iterator()).map(Label::name).collect(Collectors.joining(",")));
            addPropertiesToJsonNode(node, jsonNode);
        }

        for (Relationship rel : relationships) {
            ObjectNode edgeObj = jsonRelationships.addObject();
            edgeObj.put("source", rel.getStartNode().getElementId());
            edgeObj.put("target", rel.getEndNode().getElementId());
            edgeObj.put("relation", rel.getType().name());
            addPropertiesToJsonNode(rel, edgeObj);
        }
        return root;
    }

    private void addPropertiesToJsonNode(Entity entity, ObjectNode jsonNode) {
        Map<String, Object> allProperties = entity.getAllProperties();
        if (!allProperties.isEmpty()) {
            ObjectNode metadata = jsonNode.putObject("metadata");
            allProperties.forEach((key, value) -> {
                if (value instanceof String str) {
                    metadata.put(key, str);
                } else if (value instanceof Integer i) {
                    metadata.put(key, i);
                } else if (value instanceof Long l) {
                    metadata.put(key, l);
                } else if (value instanceof Double d) {
                    metadata.put(key, d);
                } else if (value instanceof Boolean b) {
                    metadata.put(key, b);
                } else if (value instanceof LocalDate d) {
                    metadata.put(key, d.toString());
                } else if (value instanceof String[] arr) {
                    ArrayNode arrayNode = metadata.putArray(key);
                    for (String s : arr) {
                        arrayNode.add(s);
                    }
                } else {
                    throw new IllegalArgumentException("Unsupported property type: " + value.getClass().getName());
                }
            });
        }
    }

}
