package atag.text.pipeline;

import atag.profile.ImportProfile;
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
        return new ExtractedStructure(plainText.toString(), elements);
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
