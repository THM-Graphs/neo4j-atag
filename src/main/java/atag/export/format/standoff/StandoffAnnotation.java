package atag.export.format.standoff;

import java.util.List;
import java.util.Map;

/**
 * An annotation in the standoff model. Besides its own properties it may carry nested
 * annotations - annotations that hang off this one via {@code HAS_ANNOTATION} (e.g. a
 * commentary on another annotation) - so they are rendered together with their
 * originating annotation rather than floating up to the document root.
 */
public record StandoffAnnotation(Map<String, Object> properties,
                                 List<StandoffAnnotation> children) {
}
