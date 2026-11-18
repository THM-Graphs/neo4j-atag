package atag.importer;

import atag.export.format.PropertyValues;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.graphdb.*;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;
import org.neo4j.values.storable.CoordinateReferenceSystem;
import org.neo4j.values.storable.PointValue;
import org.neo4j.values.storable.Values;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class ImportProcedures {

    @Context
    public GraphDatabaseAPI graphDatabaseAPI;

    @Context
    public Transaction tx;

    @Context
    public Log log;

    public static class GraphResult {
        public long nodeCount;
        public long relationshipCount;

        public GraphResult(long nodeCount, long relationshipCount) {
            this.nodeCount = nodeCount;
            this.relationshipCount = relationshipCount;
        }
    }

    private record JsonImportConfig(String propertyKey, List<String> labels, boolean overwrite) {
    }

    @Procedure(value = "atag.import.jgfFile", mode = Mode.WRITE)
    @Description("Import a graph from a JGF file")
    public Stream<GraphResult> jgfFile(@Name("filename") String fileName, @Name(value = "config", defaultValue = "{}") Map<String, Object> config) {
        try {
            Config neo4jConfig = graphDatabaseAPI.getDependencyResolver().resolveDependency(Config.class);
            Path folder = neo4jConfig.get(GraphDatabaseSettings.load_csv_file_url_root);
            Path inputPath = folder.resolve(fileName);
            return Stream.of(importJgf(Files.newInputStream(inputPath), config));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Procedure(value = "atag.import.jgf", mode = Mode.WRITE)
    @Description("Import a graph from a JGF string")
    public Stream<GraphResult> jgf(@Name("json") String json,  @Name(value = "config", defaultValue = "{}") Map<String, Object> config) {
        InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        GraphResult graphResult = importJgf(inputStream, config);
        return Stream.of(graphResult);
    }

    private GraphResult importJgf(InputStream inputStream, Map<String, Object> config) {
        try {
            ObjectMapper mapper = new ObjectMapper();

            config.putIfAbsent("overwrite", false); // default behaviour
            JsonImportConfig jsonImportConfig = mapper.convertValue(config, JsonImportConfig.class);
            List<String> configLabels = jsonImportConfig.labels() == null ? List.of() : jsonImportConfig.labels();

            JsonNode root = mapper.readTree(inputStream);

            JsonNode graph = root.get("graph");
            JsonNode nodes = graph.get("nodes");
            JsonNode edges = graph.get("edges");

            Map<String, Node> nodeMap = new HashMap<>();
            AtomicLong createdNodes = new AtomicLong(0);
            AtomicLong createdRelationships = new AtomicLong(0);

            // Create nodes
            nodes.fields().forEachRemaining(entry -> {
                String nodeId = entry.getKey();
                JsonNode nodeData = entry.getValue();

                List<String> labels = Arrays.asList(nodeData.get("label").asText().split(","));
                JsonNode metadata = nodeData.get("metadata");

                Optional<Label> mergeLabel = configLabels.stream().filter(labels::contains).map(Label::label).findFirst();

                Node node = null;
                boolean nodeCreated = false;
                if (mergeLabel.isPresent()) {
                    TextNode valueNode = getProperty(metadata, jsonImportConfig.propertyKey);
                    String value = valueNode.asText();
                    node = tx.findNode(mergeLabel.get(), jsonImportConfig.propertyKey, value);
                    if (node == null) {
                        log.info("couldn't find node with label {} and {} = {}", mergeLabel, jsonImportConfig.propertyKey, value);
                    }
                }
                if (node == null) {
                    node = tx.createNode();
                    nodeCreated = true;
                    createdNodes.incrementAndGet();
                }

                if (nodeCreated || jsonImportConfig.overwrite) {
                    for (String label : labels) {
                        node.addLabel(Label.label(label));
                    }
                    addProperties(node, metadata);
                }
                nodeMap.put(nodeId, node);
            });

            // Create relationships
            edges.forEach(edge -> {
                String sourceId = edge.get("source").asText();
                String targetId = edge.get("target").asText();
                String relationType = edge.get("relation").asText();

                Node sourceNode = nodeMap.get(sourceId);
                Node targetNode = nodeMap.get(targetId);
                RelationshipType relationshipType = RelationshipType.withName(relationType);

                Relationship rel = relationshipBetween(sourceNode, targetNode, relationshipType);
                if (rel == null) {
                    rel = sourceNode.createRelationshipTo(targetNode, relationshipType);
                    createdRelationships.incrementAndGet();
                }
                if (edge.has("metadata")) {
                    addProperties(rel, edge.get("metadata"));
                }
            });
            return new GraphResult(createdNodes.get(), createdRelationships.get());
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse JGF input", e);
        }
    }

    /**
     * find first relationship between nodes efficiently
     * traverses from less connected node to more connected node
     * @param sourceNode
     * @param targetNode
     * @param relationshipType
     * @return
     */
    private Relationship relationshipBetween(Node sourceNode, Node targetNode, RelationshipType relationshipType) {
        int sourceDegree = sourceNode.getDegree(relationshipType, Direction.OUTGOING);
        int targetDegree = targetNode.getDegree(relationshipType, Direction.INCOMING);

        Node from = sourceDegree <= targetDegree ? sourceNode : targetNode;
        Node to = sourceDegree <= targetDegree ? targetNode : sourceNode;
        Direction direction = sourceDegree <= targetDegree ? Direction.OUTGOING : Direction.INCOMING;

        return from.getRelationships(direction, relationshipType).stream()
                .filter(relationship -> relationship.getOtherNode(from).equals(to))
                .findFirst().orElse(null);
    }

    private TextNode getProperty(JsonNode nodeData, String propertyKey) {
        return (TextNode) nodeData.get(propertyKey);
    }

    private void addProperties(Entity entity, JsonNode metadata) {
        metadata.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();

            if (value.isTextual()) {
                try {
                    LocalDate date = LocalDate.parse(value.asText());
                    entity.setProperty(key, date);
                } catch (Exception e) {
                    entity.setProperty(key, value.asText());
                }
            } else if (value.isInt()) {
                entity.setProperty(key, value.asInt());
            } else if (value.isLong()) {
                entity.setProperty(key, value.asLong());
            } else if (value.isDouble()) {
                entity.setProperty(key, value.asDouble());
            } else if (value.isBoolean()) {
                entity.setProperty(key, value.asBoolean());
            } else if (value.isArray()) {
                entity.setProperty(key, toArray(value));
            } else if (isPoint(value)) {
                entity.setProperty(key, toPoint(value));
            }
        });
    }

    private boolean isPoint(JsonNode value) {
        return value.isObject()
                && PropertyValues.POINT_TYPE.equals(value.path(PropertyValues.POINT_TYPE_KEY).asText())
                && value.path(PropertyValues.POINT_COORDINATES_KEY).isArray();
    }

    private PointValue toPoint(JsonNode value) {
        double[] coordinates = StreamSupport.stream(value.get(PropertyValues.POINT_COORDINATES_KEY).spliterator(), false)
                .mapToDouble(JsonNode::asDouble).toArray();
        int srid = value.path(PropertyValues.POINT_SRID_KEY).asInt();
        return Values.pointValue(CoordinateReferenceSystem.get(srid), coordinates);
    }

    /**
     * Arrays keep their element type: all integers give {@code long[]}, numbers with a
     * fraction {@code double[]}, booleans {@code boolean[]}; anything else, including an
     * empty array, is stored as {@code String[]}.
     */
    private Object toArray(JsonNode array) {
        List<JsonNode> elements = StreamSupport.stream(array.spliterator(), false).toList();
        if (!elements.isEmpty() && elements.stream().allMatch(JsonNode::isIntegralNumber)) {
            return elements.stream().mapToLong(JsonNode::asLong).toArray();
        }
        if (!elements.isEmpty() && elements.stream().allMatch(JsonNode::isNumber)) {
            return elements.stream().mapToDouble(JsonNode::asDouble).toArray();
        }
        if (!elements.isEmpty() && elements.stream().allMatch(JsonNode::isBoolean)) {
            boolean[] booleans = new boolean[elements.size()];
            for (int i = 0; i < booleans.length; i++) {
                booleans[i] = elements.get(i).asBoolean();
            }
            return booleans;
        }
        return elements.stream().map(JsonNode::asText).toArray(String[]::new);
    }
}

