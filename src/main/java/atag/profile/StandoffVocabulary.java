package atag.profile;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The stand-off vocabulary shared by TEI export and TEI import: the element and
 * attribute names used for annotations that cannot be written inline, and the
 * {@code string-range()} pointer syntax that addresses a character range of the text.
 * <p>
 * Keeping both directions on the same constants is what makes the two pipelines fit
 * together - what the export writes as a pointer is exactly what the import resolves.
 */
public final class StandoffVocabulary {

    public static final String STAND_OFF = "standOff";
    public static final String LIST_ANNOTATION = "listAnnotation";
    public static final String ANNOTATION = "annotation";
    public static final String ENTITY_LIST = "list";
    public static final String ENTITY_ITEM = "item";
    public static final String ENTITY_LIST_TYPE = "entity";

    /** The TEI lists a verbatim entity declaration is written into, by declaring element. */
    private static final Map<String, String> ENTITY_LISTS = Map.of(
            "person", "listPerson",
            "personGrp", "listPerson",
            "place", "listPlace",
            "org", "listOrg",
            "event", "listEvent");
    /** The order in which those lists are written. */
    public static final List<String> ENTITY_LIST_ORDER = List.of("listPerson", "listPlace", "listOrg", "listEvent", ENTITY_LIST);

    public static final String TARGET_ATTRIBUTE = "target";
    /** Attribute carrying an entity's display name. */
    public static final String NAME_ATTRIBUTE = "n";
    /** Attribute carrying the labels that refine an entity, e.g. {@code Person}. */
    public static final String TYPE_ATTRIBUTE = "type";

    private static final Pattern STRING_RANGE =
            Pattern.compile("#?string-range\\(\\s*([^,]*?)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\)");

    private StandoffVocabulary() {
    }

    /** The list element a declaration written as {@code declaringElement} belongs in. */
    public static String listFor(String declaringElement) {
        return ENTITY_LISTS.getOrDefault(declaringElement, ENTITY_LIST);
    }

    /**
     * A pointer to the character range {@code [startIndex, endIndex)} of the text
     * identified by {@code id}.
     */
    public static String stringRange(String id, long startIndex, long endIndex) {
        return String.format("#string-range(%s,%d,%d)", id, startIndex, endIndex - startIndex);
    }

    /**
     * The range a {@code string-range()} pointer denotes as {@code {startIndex, endIndex}},
     * or {@code null} if the pointer is not a range pointer.
     */
    public static long[] parseStringRange(String pointer) {
        Matcher matcher = STRING_RANGE.matcher(pointer);
        if (!matcher.matches()) {
            return null;
        }
        long startIndex = Long.parseLong(matcher.group(2));
        return new long[]{startIndex, startIndex + Long.parseLong(matcher.group(3))};
    }
}
