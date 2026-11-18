package atag.text.pipeline;

import java.util.List;
import java.util.Map;

/**
 * Result of phase 3: the same structures as phase 2, but expressed in the project's own
 * vocabulary - property keys instead of attribute names, annotation types instead of
 * element names, and references to entities instead of pointer syntax. Nothing here is
 * specific to XML or HTML any more, which is why phase 4 can write it to the graph
 * without knowing where it came from.
 *
 * @param header the document's header verbatim, or {@code null}
 */
public record MappedStructure(String plainText,
                              List<MappedAnnotation> annotations,
                              List<MappedEntity> entities,
                              String header) {

    /**
     * @param id         the annotation's own identifier, if the source declared one
     * @param parentId   the annotation this one is attached to, or {@code null} when it is
     *                   attached to the content node itself
     * @param references the entities this annotation refers to
     */
    public record MappedAnnotation(String id,
                                   String parentId,
                                   Map<String, Object> properties,
                                   List<Reference> references) {
    }

    /**
     * A pointer at an entity, together with the attribute it was written in - so that a
     * pointer the graph cannot resolve can stay where it was.
     *
     * @param pointer the pointer as written, with or without a leading {@code #}
     */
    public record Reference(String attribute, String pointer) {

        public String id() {
            return pointer.startsWith("#") ? pointer.substring(1) : pointer;
        }
    }

    public record MappedEntity(String id,
                               List<String> labels,
                               Map<String, Object> properties) {
    }
}
