package atag.text.pipeline;

import java.util.Map;

/**
 * One entity declaration found by phase 2, still in the vocabulary of the source: the
 * element it was written as, its attributes, and - where the profile asks for them - a
 * display name and the declaration itself, verbatim.
 *
 * @param label  display name the profile's {@code entityLabelXPath} produced, or {@code null}
 * @param source the declaration serialized as it was written, or {@code null} when not kept
 */
public record ExtractedEntity(String name,
                              Map<String, String> attributes,
                              String label,
                              String source) {
}
