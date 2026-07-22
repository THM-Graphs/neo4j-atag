package atag.export.format.standoff;

import atag.export.Subgraph;
import atag.export.format.Exporter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders the {@link StandoffDocument} model as JSON: each anchor's own properties at
 * that level, its annotations under an {@code annotations} key, and its nested child
 * anchors under a {@code parts} key. Nested annotations appear under an
 * {@code annotations} key on their originating annotation.
 */
public class StandoffJsonExporter implements Exporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final StandoffModelBuilder builder = new StandoffModelBuilder();

    @Override
    public Map<String, Object> toValue(Subgraph subgraph) {
        Map<String, Object> root = toMap(builder.build(subgraph));
        if (((List<?>) root.get("annotations")).isEmpty()) {
            root.remove("annotations");
        }
        return root;
    }

    private Map<String, Object> toMap(StandoffDocument node) {
        Map<String, Object> result = new LinkedHashMap<>(node.properties());
        result.put("annotations", toAnnotations(node.annotations()));
        if (!node.children().isEmpty()) {
            result.put("parts", node.children().stream().map(this::toMap).collect(Collectors.toList()));
        }
        return result;
    }

    private List<Map<String, Object>> toAnnotations(List<StandoffAnnotation> annotations) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (StandoffAnnotation annotation : annotations) {
            Map<String, Object> map = new LinkedHashMap<>(annotation.properties());
            if (!annotation.children().isEmpty()) {
                map.put("annotations", toAnnotations(annotation.children()));
            }
            result.add(map);
        }
        return result;
    }

    @Override
    public String render(Subgraph subgraph) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(toValue(subgraph));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
