package atag.text.pipeline;

import java.util.Map;

/**
 * One element found by phase 2, still in the vocabulary of the source document: its
 * markup name - {@code null} where the source has none to contribute - its attributes as written, and the character range it covers in the
 * extracted plain text. Whether it was written inline or as stand-off markup is already
 * resolved at this point - both end up as a range over the same text.
 *
 * @param startIndex start of the covered range, or {@code null} for an element that does
 *                   not refer to a range of text at all
 * @param text       the element's own textual content, or {@code null} if it has none to record
 * @param parentId   id of the annotation this one is attached to, for annotations that
 *                   target another annotation rather than a range of text
 * @param depth      nesting depth of the element in the source, or {@code null} where the
 *                   source has no hierarchy to record; the only thing that tells two
 *                   elements over the same range apart
 */
public record ExtractedElement(String name,
                               Map<String, String> attributes,
                               Long startIndex,
                               Long endIndex,
                               String text,
                               String parentId,
                               Long depth) {

    public ExtractedElement(String name, Map<String, String> attributes, long startIndex, long endIndex, String text) {
        this(name, attributes, startIndex, endIndex, text, null, null);
    }

    public ExtractedElement(String name, Map<String, String> attributes, long startIndex, long endIndex,
                            String text, long depth) {
        this(name, attributes, startIndex, endIndex, text, null, depth);
    }
}
