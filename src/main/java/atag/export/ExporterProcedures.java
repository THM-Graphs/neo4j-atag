package atag.export;

import apoc.result.ObjectResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.graphdb.*;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.procedure.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static java.nio.file.Files.writeString;

public class ExporterProcedures {

    @Context
    public GraphDatabaseAPI graphDatabaseAPI;

    @UserFunction
    @Description("export a graph into JGF format and return as a map")
    public Map<String, Object> jgf(@Name("nodes") List<Node> nodes, @Name("relationships") List<Relationship> relationships) {
        return new JgfFormatter().format( nodes, relationships);
    }

    @Procedure(name = "atag.export.jgf.list", mode = Mode.READ)
    @Description("export a list of nodes and relationships into JGF format")
    public Stream<ObjectResult> jgfList(@Name("nodes") List<Node> nodes, @Name("relationships") List<Relationship> relationships, @Name("config") Map<String, Object> config) {
        Map<String, Object> result = new JgfFormatter().format(nodes, relationships);
        return outputResult(result, (String) config.get("fileName"));
    }

    @Procedure(name = "atag.export.jgf.fromNode", mode = Mode.READ)
    @Description("traverse from a start node and export the subgraph as JGF")
    public Stream<ObjectResult> jgfFromNode(@Name("startNode") Node startNode, @Name("config") Map<String, Object> config) {
        TraversalResult traversal = traverseFromNode(startNode, config);
        Map<String, Object> result = new JgfFormatter().format(traversal.nodes, traversal.relationships);
        return outputResult(result, (String) config.get("fileName"));
    }

    @Procedure(name = "atag.export.standoff_json.list", mode = Mode.READ)
    @Description("export a list of nodes and relationships as standoff JSON")
    public Stream<ObjectResult> standoffJsonList(@Name("nodes") List<Node> nodes, @Name("relationships") List<Relationship> relationships, @Name("config") Map<String, Object> config) {
        Map<String, Object> result = new StandoffJsonFormatter().format( nodes, relationships);
        return outputResult(result, (String) config.get("fileName"));
    }

    @Procedure(name = "atag.export.standoff_json.fromNode", mode = Mode.READ)
    @Description("traverse from a start node and export the subgraph as standoff JSON")
    public Stream<ObjectResult> standoffJsonFromNode(@Name("startNode") Node startNode, @Name("config") Map<String, Object> config) {
        TraversalResult traversal = traverseFromNode(startNode, config);
        Map<String, Object> result = new StandoffJsonFormatter().format(traversal.nodes, traversal.relationships);
        return outputResult(result, (String) config.get("fileName"));
    }

    @SuppressWarnings("unchecked")
    private TraversalResult traverseFromNode(Node startNode, Map<String, Object> config) {
        boolean includeCharacterChain = !Boolean.FALSE.equals(config.get("includeCharacterChain"));
        List<String> annotationTypes = (List<String>) config.get("annotationTypes");
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
        return new TraversalResult(nodes, relationships);
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

    private Stream<ObjectResult> outputResult(Map<String, Object> result, String fileName) {
        if (fileName != null) {
            return asFile(fileName, result);
        }
        return Stream.of(new ObjectResult(result));
    }

    private Stream<ObjectResult> asFile(String fileName, Map<String, Object> data) {
        Config config = graphDatabaseAPI.getDependencyResolver().resolveDependency(Config.class);
        Path folder = config.get(GraphDatabaseSettings.load_csv_file_url_root);

        if (fileName.contains(File.separator)) {
            throw new IllegalArgumentException("File name must not contain path separators");
        }
        Path outputPath = folder.resolve(fileName);

        try {
            String json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(data);
            writeString(outputPath, json);
            long size = outputPath.toFile().length();
            return Stream.of(new ObjectResult(String.format("%d bytes written to %s", size, fileName)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private record TraversalResult(List<Node> nodes, List<Relationship> relationships) {}
}
