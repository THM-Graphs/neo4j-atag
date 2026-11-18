package atag.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One XPath dialect for profiles, two engines underneath.
 * <p>
 * The document structure is selected with Saxon, which knows the {@code *:name} wildcard
 * of XPath 2.0 and later; the text phases use the JDK's XPath 1.0 engine, which does not.
 * Rather than asking a profile to spell the same selection two ways, the expressions
 * handed to the older engine are rewritten: {@code *:TEI} becomes
 * {@code *[local-name()='TEI']}, and {@code @*:id} becomes {@code @*[local-name()='id']}.
 * An expression written with {@code local-name()} in the first place is left alone.
 */
public final class XPathSyntax {

    private static final Pattern WILDCARD = Pattern.compile("(@?)\\*:([A-Za-z_][\\w.-]*)");
    private static final Pattern STRING_LITERAL = Pattern.compile("'[^']*'|\"[^\"]*\"");

    private XPathSyntax() {
    }

    /** Rewrite the namespace wildcards of an expression for the XPath 1.0 engine. */
    public static String expand(String xpath) {
        if (xpath == null || !xpath.contains("*:")) {
            return xpath;
        }
        StringBuilder result = new StringBuilder();
        int position = 0;
        // string literals are copied through: what looks like a wildcard inside one is text
        Matcher literal = STRING_LITERAL.matcher(xpath);
        while (literal.find()) {
            result.append(expandOutsideLiterals(xpath.substring(position, literal.start())));
            result.append(literal.group());
            position = literal.end();
        }
        result.append(expandOutsideLiterals(xpath.substring(position)));
        return result.toString();
    }

    private static String expandOutsideLiterals(String fragment) {
        Matcher matcher = WILDCARD.matcher(fragment);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(matcher.group(1) + "*[local-name()='" + matcher.group(2) + "']"));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
