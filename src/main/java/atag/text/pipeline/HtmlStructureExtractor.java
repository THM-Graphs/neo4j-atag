package atag.text.pipeline;

import atag.profile.ImportProfile;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 2 for HTML sources. The document tree is walked depth first: text nodes extend
 * the plain text, every element below the body becomes a range over it. Each element
 * also keeps the text of its own subtree, which is what makes a nested element's range
 * verifiable on its own.
 */
public class HtmlStructureExtractor implements StructureExtractor<Document> {

    @Override
    public ExtractedStructure extract(Document document, ImportProfile profile) {
        StringBuilder plainText = new StringBuilder();
        List<ExtractedElement> elements = new ArrayList<>();
        traverse(0, document.body(), 0L, plainText, elements);
        return new ExtractedStructure(plainText.toString(), elements);
    }

    private long traverse(int depth, org.jsoup.nodes.Node node, long index,
                          StringBuilder plainText, List<ExtractedElement> elements) {
        if (node instanceof Element element) {
            // the element is added before its children are visited, so that the extracted
            // elements stay in document order even though the range is only known afterwards
            int position = elements.size();
            long startIndex = index;
            if (depth > 0) {
                elements.add(null);
            }

            StringBuilder elementText = new StringBuilder();
            for (org.jsoup.nodes.Node child : element.childNodes()) {
                index = traverse(depth + 1, child, index, elementText, elements);
            }

            if (depth > 0) {
                elements.set(position, new ExtractedElement(element.nodeName(), attributesOf(element),
                        startIndex, index, elementText.toString()));
            }
            plainText.append(elementText);
        } else if (node instanceof TextNode textNode) {
            plainText.append(textNode.text());
            index += textNode.text().length();
        } else {
            throw new IllegalArgumentException("Unknown node type: " + node);
        }
        return index;
    }

    private Map<String, String> attributesOf(Element element) {
        Map<String, String> result = new LinkedHashMap<>();
        element.attributes().forEach(attribute -> result.put(attribute.getKey(), attribute.getValue()));
        return result;
    }
}
