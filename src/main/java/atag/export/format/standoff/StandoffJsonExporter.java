package atag.export.format.standoff;

import atag.export.format.DocumentExporter;
import atag.export.map.MappedExport;
import atag.export.map.MappedExport.MappedAnnotation;
import atag.export.map.MappedExport.MappedDocument;
import atag.profile.ExportProfile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders the mapped document model as JSON: each anchor's own properties at that level,
 * its annotations under an {@code annotations} key, and its nested child anchors under a
 * {@code parts} key. Nested annotations appear under an {@code annotations} key on their
 * originating annotation.
 */
public class StandoffJsonExporter extends DocumentExporter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected Map<String, Object> value(MappedExport export, ExportProfile profile) {
        Map<String, Object> root = toMap(export.root());
        if (((List<?>) root.get("annotations")).isEmpty()) {
            root.remove("annotations");
        }
        return root;
    }

    @Override
    protected String serialize(MappedExport export, ExportProfile profile) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value(export, profile));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> toMap(MappedDocument node) {
        Map<String, Object> result = new LinkedHashMap<>(node.properties());
        result.put("annotations", toAnnotations(node.annotations()));
        if (!node.children().isEmpty()) {
            result.put("parts", node.children().stream().map(this::toMap).collect(Collectors.toList()));
        }
        return result;
    }

    private List<Map<String, Object>> toAnnotations(List<MappedAnnotation> annotations) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (MappedAnnotation annotation : annotations) {
            Map<String, Object> map = new LinkedHashMap<>(annotation.properties());
            if (!annotation.children().isEmpty()) {
                map.put("annotations", toAnnotations(annotation.children()));
            }
            result.add(map);
        }
        return result;
    }
}
