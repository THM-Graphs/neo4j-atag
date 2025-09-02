package atag.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.neo4j.graphdb.Entity;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.internal.helpers.collection.Iterators;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserFunction;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ExporterProcedures {

    @UserFunction
    @Description("export a graph into JGF format")
    public Map<String,Object> jgf(@Name("nodes") List<Node> nodes, @Name("relationships") List<Relationship> relationships) {
        ObjectMapper mapper = new ObjectMapper();
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

        Map<String, Object> result = mapper.convertValue(root, Map.class);
        return result;
    }

    private void addPropertiesToJsonNode(Entity entity, ObjectNode jsonNode) {
        Map<String, Object> allProperties = entity.getAllProperties();
        if (!allProperties.isEmpty()) {
            ObjectNode metadata = jsonNode.putObject("metadata");
            allProperties.forEach((key, value) -> {
                switch (value) {
                    case String str -> metadata.put(key, str);
                    case Integer i -> metadata.put(key, i);
                    case Long l -> metadata.put(key, l);
                    case Double d -> metadata.put(key, d);
                    case Boolean b -> metadata.put(key, b);
                    case LocalDate d -> metadata.put(key, d.toString());
                    case String[] arr -> {
                        ArrayNode arrayNode = metadata.putArray(key);
                        for (String s : arr) {
                            arrayNode.add(s);
                        }
                    }
                    default -> throw new IllegalArgumentException("Unsupported property type: " + value.getClass().getName());
                }
            });
        }
    }

}
