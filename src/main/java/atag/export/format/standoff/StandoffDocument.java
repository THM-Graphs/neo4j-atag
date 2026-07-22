package atag.export.format.standoff;

import java.util.List;
import java.util.Map;

/**
 * Serialization-neutral representation of a standoff export. Each node is an anchor
 * (a {@code Text} or {@code Collection} node) carrying its own annotations and its
 * nested child anchors, mirroring the {@code PART_OF} hierarchy of the graph. Built
 * once and then rendered by a format-specific serializer, so JSON and XML share the
 * same interpretation of the ATAG schema.
 */
public record StandoffDocument(String name,
                               Map<String, Object> properties,
                               List<StandoffAnnotation> annotations,
                               List<StandoffDocument> children) {
}
