package atag.importer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.graphdb.*;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.procedure.*;

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

public class ImportProcedures {

    @Context
    public GraphDatabaseAPI graphDatabaseAPI;

    @Context
    public Transaction tx;

    public static class GraphResult {
        public long nodeCount;
        public long relationshipCount;

        public GraphResult(long nodeCount, long relationshipCount) {
            this.nodeCount = nodeCount;
            this.relationshipCount = relationshipCount;
        }
    }

    @Procedure(value = "atag.import.jgfFile", mode = Mode.WRITE)
    @Description("Import a graph from a JGF file")
    public Stream<GraphResult> jgfFile(@Name("filename") String fileName) {
        try {
            Config config = graphDatabaseAPI.getDependencyResolver().resolveDependency(Config.class);
            Path folder = config.get(GraphDatabaseSettings.load_csv_file_url_root);
            Path inputPath = folder.resolve(fileName);
            return Stream.of(importJgf(Files.newInputStream(inputPath)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Procedure(value = "atag.import.jgf", mode = Mode.WRITE)
    @Description("Import a graph from a JGF string")
    public Stream<GraphResult> jgf(@Name("json") String json) {
        InputStream inputStream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        GraphResult graphResult = importJgf(inputStream);
        return Stream.of(graphResult);
    }

    private GraphResult importJgf(InputStream inputStream) {
        try {
            ObjectMapper mapper = new ObjectMapper();
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

                Node node = tx.createNode();
                nodeMap.put(nodeId, node);
                createdNodes.incrementAndGet();

                // Add labels
                String labels = nodeData.get("label").asText();
                Arrays.stream(labels.split(","))
                        .forEach(label -> node.addLabel(Label.label(label)));

                // Add properties
                if (nodeData.has("metadata")) {
                    addProperties(node, nodeData.get("metadata"));
                }
            });

            // Create relationships
            edges.forEach(edge -> {
                String sourceId = edge.get("source").asText();
                String targetId = edge.get("target").asText();
                String relationType = edge.get("relation").asText();

                Node sourceNode = nodeMap.get(sourceId);
                Node targetNode = nodeMap.get(targetId);

                Relationship rel = sourceNode.createRelationshipTo(targetNode, RelationshipType.withName(relationType));
                createdRelationships.incrementAndGet();

                if (edge.has("metadata")) {
                    addProperties(rel, edge.get("metadata"));
                }
            });
            return new GraphResult(createdNodes.get(), createdRelationships.get());
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse JGF input", e);
        }
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
                List<String> list = new ArrayList<>();
                value.elements().forEachRemaining(element -> list.add(element.asText()));
                entity.setProperty(key, list.toArray(new String[0]));
            }
        });
    }
}

