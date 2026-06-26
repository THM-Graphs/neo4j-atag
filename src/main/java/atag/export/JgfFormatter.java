package atag.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.neo4j.graphdb.Entity;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.internal.helpers.collection.Iterators;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JgfFormatter implements GraphExportFormatter {

    @Override
    public Map<String, Object> format(List<Node> nodes, List<Relationship> relationships) {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.convertValue(buildObjectNode(mapper, nodes, relationships), Map.class);
    }

    private ObjectNode buildObjectNode(ObjectMapper mapper, List<Node> nodes, List<Relationship> relationships) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode graph = root.putObject("graph");
        graph.put("label", OffsetDateTime.now().toString());
        graph.put("directed", true);

        ObjectNode jsonNodes = graph.putObject("nodes");
        ArrayNode jsonEdges = graph.putArray("edges");

        for (Node node : nodes) {
            ObjectNode jsonNode = jsonNodes.putObject(node.getElementId());
            jsonNode.put("label", Iterators.stream(node.getLabels().iterator())
                    .map(Label::name).collect(Collectors.joining(",")));
            addProperties(node, jsonNode);
        }

        for (Relationship rel : relationships) {
            ObjectNode edgeObj = jsonEdges.addObject();
            edgeObj.put("source", rel.getStartNode().getElementId());
            edgeObj.put("target", rel.getEndNode().getElementId());
            edgeObj.put("relation", rel.getType().name());
            addProperties(rel, edgeObj);
        }
        return root;
    }

    private void addProperties(Entity entity, ObjectNode jsonNode) {
        Map<String, Object> allProperties = entity.getAllProperties();
        if (allProperties.isEmpty()) return;

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
