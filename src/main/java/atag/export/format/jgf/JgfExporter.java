package atag.export.format.jgf;

import atag.export.Subgraph;
import atag.export.format.Exporter;
import atag.export.format.PropertyValues;
import atag.profile.ExportProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.neo4j.graphdb.Entity;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.internal.helpers.collection.Iterators;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Schema-agnostic export into <a href="https://github.com/jsongraph/json-graph-specification">JSON Graph Format</a>.
 * Serializes whatever nodes and edges the subgraph contains, without interpreting labels:
 * a graph-oriented exchange format keeps the graph as it is, so the profile only selects
 * the scope and has no vocabulary to contribute here.
 */
public class JgfExporter implements Exporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> toValue(Subgraph subgraph, ExportProfile profile) {
        return MAPPER.convertValue(buildRoot(subgraph), Map.class);
    }

    @Override
    public String render(Subgraph subgraph, ExportProfile profile) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(buildRoot(subgraph));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private ObjectNode buildRoot(Subgraph subgraph) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode graph = root.putObject("graph");
        graph.put("label", java.time.OffsetDateTime.now().toString());
        graph.put("directed", true);

        ObjectNode jsonNodes = graph.putObject("nodes");
        ArrayNode jsonEdges = graph.putArray("edges");

        for (Node node : subgraph.nodes()) {
            ObjectNode jsonNode = jsonNodes.putObject(node.getElementId());
            jsonNode.put("label", Iterators.stream(node.getLabels().iterator())
                    .map(Label::name).collect(Collectors.joining(",")));
            addMetadata(node, jsonNode);
        }

        for (Relationship rel : subgraph.relationships()) {
            ObjectNode edge = jsonEdges.addObject();
            edge.put("source", rel.getStartNode().getElementId());
            edge.put("target", rel.getEndNode().getElementId());
            edge.put("relation", rel.getType().name());
            addMetadata(rel, edge);
        }
        return root;
    }

    private void addMetadata(Entity entity, ObjectNode target) {
        if (entity.getAllProperties().isEmpty()) {
            return;
        }
        PropertyValues.writeJson(entity, target.putObject("metadata"));
    }
}
