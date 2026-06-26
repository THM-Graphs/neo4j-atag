package atag.export;

import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StandoffJsonFormatter implements GraphExportFormatter {

    private static final Label ANNOTATION_LABEL = Label.label("Annotation");

    @Override
    public Map<String, Object> format(List<Node> nodes, List<Relationship> relationships) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Root properties come from the start node (fromNode scope) or the first
        // non-Annotation node found (list scope).
        Node documentNode = nodes.stream()
                .filter(n -> !n.hasLabel(ANNOTATION_LABEL))
                .findFirst()
                .orElse(null);

        if (documentNode != null) {
            documentNode.getAllProperties().forEach(result::put);
        }

        List<Map<String, Object>> annotations = nodes.stream()
                .filter(n -> n.hasLabel(ANNOTATION_LABEL))
                .map(n -> new LinkedHashMap<>(n.getAllProperties()))
                .collect(Collectors.toList());

        result.put("properties", annotations);
        return result;
    }
}
