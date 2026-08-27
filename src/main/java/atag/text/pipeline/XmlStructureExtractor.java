package atag.text.pipeline;

import atag.profile.ImportProfile;
import atag.profile.StandoffVocabulary;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 2 for XML sources. The profile's XPath selects the nodes that make up the text:
 * text nodes are appended to the plain text, elements become ranges over it. Because the
 * selected nodes are visited in document order, an element's range starts at the length
 * of the plain text collected so far.
 * <p>
 * Where the profile also selects stand-off annotations, their {@code @target} pointers
 * are resolved against the same plain text, so an annotation encoded inline and the same
 * annotation encoded as stand-off markup arrive at phase 3 in identical shape.
 */
public class XmlStructureExtractor implements StructureExtractor<Document> {

    @Override
    public ExtractedStructure extract(Document document, ImportProfile profile) {
        XPath xPath = XPathFactory.newInstance().newXPath();
        StringBuilder plainText = new StringBuilder();
        List<ExtractedElement> elements = new ArrayList<>();

        String path = profile.xpath().isEmpty() ? "/" : profile.xpath();
        for (Node item : select(xPath, document, path)) {
            if (item instanceof Element element) {
                String textContent = element.getTextContent();
                long startIndex = plainText.length();
                elements.add(new ExtractedElement(element.getNodeName(), attributesOf(element),
                        startIndex, startIndex + textContent.length(),
                        textContent.isEmpty() ? null : textContent));
            } else if (item instanceof Text text) {
                plainText.append(text.getTextContent());
            } else {
                throw new IllegalArgumentException("Unknown node type: " + item);
            }
        }

        elements.addAll(standoff(xPath, document, profile));
        return new ExtractedStructure(plainText.toString(), elements, entities(xPath, document, profile));
    }

    private List<ExtractedElement> standoff(XPath xPath, Document document, ImportProfile profile) {
        List<ExtractedElement> result = new ArrayList<>();
        if (profile.standoffXPath().isEmpty()) {
            return result;
        }
        for (Node item : select(xPath, document, profile.standoffXPath())) {
            if (!(item instanceof Element element)) {
                continue;
            }
            Map<String, String> attributes = attributesOf(element);
            String target = attributes.remove(StandoffVocabulary.TARGET_ATTRIBUTE);
            if (target == null) {
                throw new IllegalArgumentException(
                        String.format("stand-off annotation <%s> has no target", element.getNodeName()));
            }
            result.add(resolve(nameOf(element), attributes, target));
        }
        return result;
    }

    /**
     * The markup name of a stand-off element, or {@code null} for the generic
     * {@code <annotation>} of the stand-off vocabulary: that name says that the
     * annotation was written outside the text, not what it annotates, so it must not
     * end up as the element name of a later inline serialization.
     */
    private String nameOf(Element element) {
        String name = element.getLocalName() != null ? element.getLocalName() : element.getNodeName();
        return StandoffVocabulary.ANNOTATION.equals(name) ? null : element.getNodeName();
    }

    /**
     * Resolve a stand-off target: a {@code string-range(id, start, length)} pointer
     * denotes a range of the text, a plain {@code #id} pointer denotes another annotation.
     */
    private ExtractedElement resolve(String name, Map<String, String> attributes, String target) {
        long[] range = StandoffVocabulary.parseStringRange(target);
        if (range != null) {
            return new ExtractedElement(name, attributes, range[0], range[1], null);
        }
        if (target.startsWith("#")) {
            return new ExtractedElement(name, attributes, null, null, null, target.substring(1));
        }
        throw new IllegalArgumentException("cannot resolve stand-off target: " + target);
    }

    private List<ExtractedElement> entities(XPath xPath, Document document, ImportProfile profile) {
        List<ExtractedElement> result = new ArrayList<>();
        if (profile.entityXPath().isEmpty()) {
            return result;
        }
        for (Node item : select(xPath, document, profile.entityXPath())) {
            if (item instanceof Element element) {
                result.add(new ExtractedElement(element.getNodeName(), attributesOf(element), null, null, null, null));
            }
        }
        return result;
    }

    private List<Node> select(XPath xPath, Document document, String path) {
        try {
            NodeList nodeList = (NodeList) xPath.compile(path).evaluate(document, XPathConstants.NODESET);
            List<Node> result = new ArrayList<>(nodeList.getLength());
            for (int i = 0; i < nodeList.getLength(); i++) {
                result.add(nodeList.item(i));
            }
            return result;
        } catch (XPathExpressionException e) {
            throw new RuntimeException("invalid xpath expression: " + path, e);
        }
    }

    private Map<String, String> attributesOf(Element element) {
        NamedNodeMap attributes = element.getAttributes();
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String name = attribute.getNodeName();
            if (!"xmlns".equals(name) && !name.startsWith("xmlns:")) {
                result.put(name, attribute.getNodeValue());
            }
        }
        return result;
    }
}
