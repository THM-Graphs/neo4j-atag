package atag.export;

import apoc.result.ObjectResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.graphdb.Entity;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.internal.helpers.collection.Iterators;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.procedure.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.Files.writeString;

public class ExporterProcedures {

    @Context
    public GraphDatabaseAPI graphDatabaseAPI;

    @Procedure
    @Description("export a graph into JGF format and write to a file")
    public Stream<ObjectResult> jgfFile(@Name("nodes") List<Node> nodes, @Name("relationships") List<Relationship> relationships, @Name("filename") String fileName) {
        Config config = graphDatabaseAPI.getDependencyResolver().resolveDependency(Config.class);
        Path folder = config.get(GraphDatabaseSettings.load_csv_file_url_root);

        if (fileName.contains(File.separator)) {
            throw new IllegalArgumentException("File name must not contain path separators");
        }
        Path outputPath = folder.resolve(fileName);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = toJgf(mapper, nodes, relationships);
        try {
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
            writeString(outputPath, json);
            return Stream.of(new ObjectResult(fileName));
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
