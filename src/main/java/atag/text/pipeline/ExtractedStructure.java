package atag.text.pipeline;

import java.util.List;

/**
 * Result of phase 2: the plain text of the source document plus the elements that refer
 * to ranges within it. Still expressed in the source vocabulary - the translation into
 * the project's own vocabulary is phase 3.
 *
 * @param header the document's header verbatim, or {@code null} when the profile keeps none
 */
public record ExtractedStructure(String plainText,
                                 List<ExtractedElement> elements,
                                 List<ExtractedEntity> entities,
                                 String header) {

    public ExtractedStructure(String plainText, List<ExtractedElement> elements) {
        this(plainText, elements, List.of(), null);
    }
}
