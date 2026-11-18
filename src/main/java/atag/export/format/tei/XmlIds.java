package atag.export.format.tei;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Maps graph identifiers to values valid for {@code xml:id}, which is an NCName, and keeps
 * them unique. A given graph identifier always yields the same value, so the pointers
 * to an element and the element itself agree.
 */
final class XmlIds {

    private final Map<String, String> assigned = new HashMap<>();
    private final Set<String> taken = new HashSet<>();
    private int generated;

    String of(String raw) {
        String known = assigned.get(raw);
        if (known != null) {
            return known;
        }
        String base = sanitize(raw);
        String candidate = base;
        for (int suffix = 2; taken.contains(candidate); suffix++) {
            candidate = base + "_" + suffix;
        }
        taken.add(candidate);
        assigned.put(raw, candidate);
        return candidate;
    }

    /** An identifier no graph identifier has been mapped to so far, and none will be mapped to later. */
    String generate() {
        String candidate;
        do {
            candidate = "atag-" + (++generated);
        } while (taken.contains(candidate));
        taken.add(candidate);
        assigned.put(candidate, candidate);
        return candidate;
    }

    static String sanitize(String raw) {
        StringBuilder builder = new StringBuilder(raw.length() + 1);
        raw.codePoints().forEach(c -> builder.appendCodePoint(isNameChar(c) ? c : '_'));
        if (builder.isEmpty() || !isNameStart(builder.codePointAt(0))) {
            builder.insert(0, '_');
        }
        return builder.toString();
    }

    private static boolean isNameStart(int c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isNameChar(int c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.';
    }
}
