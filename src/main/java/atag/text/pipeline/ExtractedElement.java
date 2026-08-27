package atag.text.pipeline;

import java.util.Map;

/**
 * One element found by phase 2, still in the vocabulary of the source document: its
 * markup name, its attributes as written, and the character range it covers in the
 * extracted plain text.
 *
 * @param text the element's own textual content, or {@code null} if it has none to record
 */
public record ExtractedElement(String name,
                               Map<String, String> attributes,
                               long startIndex,
                               long endIndex,
                               String text) {
}
