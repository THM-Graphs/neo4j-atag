package atag.text;

import atag.util.ResultTypes;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.neo4j.graphdb.*;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Stream;

public class Importer {

    @Context
    public Transaction tx;

    @Context
    public Log log;

    @Procedure(mode = Mode.WRITE, name = "atag.text.import.html")
    public Stream<ResultTypes.NodeResult> importHtml(
            @Name("startNode") Node startNode,
            @Name("propertyKey") String propertyKey,
            @Name(value = "label for annotation nodes", defaultValue = "Annotation") String label,
            @Name(value = "property name for plain text", defaultValue = "plainText") String plainTextProperty,
            @Name(value = "relationship type", defaultValue = "HAS_ANNOTATION") String relationshipTypeString ) {

        String htmlText = (String) startNode.getProperty(propertyKey);

        Document document = Jsoup.parse(htmlText);
        document.outputSettings().prettyPrint(false); // This line prevents Jsoup from adding line breaks

        StringBuilder plainTextBuilder = new StringBuilder();
        RelationshipType relationshipType = RelationshipType.withName(relationshipTypeString);
        long result = traverse(0, document.body(), 0l, plainTextBuilder, startNode, Label.label(label),
                relationshipType, plainTextProperty);
        startNode.setProperty(plainTextProperty, plainTextBuilder.toString());
        log.info("Result: plain {}, length {}", plainTextBuilder, result);

        return startNode.getRelationships(Direction.OUTGOING, relationshipType).stream()
                .map(Relationship::getEndNode)
                .sorted(Comparator.comparing(node -> (long) node.getProperty("startIndex")))
                .map(ResultTypes.NodeResult::new);
    }

    private long traverse(int depth, org.jsoup.nodes.Node node, long index, StringBuilder plainTextBuilder,
                          Node neo4jNode, Label label, RelationshipType relationshipType, String plainTextProperty) {
        if (node instanceof org.jsoup.nodes.Element element) {
            Node newNeo4jNode = null;
            if (depth>0){
                newNeo4jNode = tx.createNode(label);
                neo4jNode.createRelationshipTo(newNeo4jNode, relationshipType);
                newNeo4jNode.setProperty("startIndex", index);
                newNeo4jNode.setProperty("tag", element.nodeName());
            }

            log.debug(" ".repeat(depth) + "Depth: {}, Element: {}, index: {}", depth, element.nodeName(), index);
            StringBuilder localPlainTextBuilder = new StringBuilder();
            for (org.jsoup.nodes.Node child : element.childNodes()) {
                index = traverse(depth+1, child, index, localPlainTextBuilder, neo4jNode, label,
                        relationshipType, plainTextProperty);
            }

            if (depth>0){
                newNeo4jNode.setProperty("endIndex", index);
                newNeo4jNode.setProperty(plainTextProperty, localPlainTextBuilder.toString());
            }
            plainTextBuilder.append(localPlainTextBuilder);

        } else if (node instanceof org.jsoup.nodes.TextNode textNode) {
            plainTextBuilder.append(textNode.text());
            index += textNode.text().length();
            log.debug(" ".repeat(depth) + "Text: {}, index: {}", textNode.text(), index);
        } else {
            throw new IllegalArgumentException("Unknown node type: " + node);
        }
        return index;
    }
    @Procedure(mode = Mode.WRITE, name = "atag.text.import.xml")
    public Stream<ResultTypes.NodeResult> importXml(
            @Name("startNode") Node startNode,
            @Name("propertyKey") String propertyKey,
            @Name(value = "xpath expression", defaultValue = "/TEI/text/body//node()") String path,
            @Name(value = "label for annotation nodes", defaultValue = "Annotation") String labelString,
            @Name(value = "property name for plain text", defaultValue = "plainText") String plainTextProperty,
            @Name(value = "relationship type", defaultValue = "HAS_ANNOTATION") String relationshipTypeString ) {
        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            documentBuilderFactory.setIgnoringElementContentWhitespace(true);
            documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            documentBuilder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));

            String xml = (String) startNode.getProperty(propertyKey);
            org.w3c.dom.Document doc = documentBuilder.parse(new ByteArrayInputStream(xml.getBytes()));
            XPathFactory xPathFactory = XPathFactory.newInstance();

            XPath xPath = xPathFactory.newXPath();

            path = StringUtils.isEmpty(path) ? "/" : path;
            XPathExpression xPathExpression = xPath.compile(path);
            NodeList nodeList = (NodeList) xPathExpression.evaluate(doc, XPathConstants.NODESET);

            RelationshipType relationshipType = RelationshipType.withName(relationshipTypeString);
            Label label = Label.label(labelString);
            StringBuilder plainTextBuilder = new StringBuilder();

            List<Node> annotations = new ArrayList<>();

            for (int i=0; i<nodeList.getLength(); i++) {
                org.w3c.dom.Node item = nodeList.item(i);
//                int depth = getDepth(item);

                if (item instanceof Element element) {
                    Node newNeo4jNode = tx.createNode(label);
                    annotations.add(newNeo4jNode);
                    String textContent = element.getTextContent();
                    startNode.createRelationshipTo(newNeo4jNode, relationshipType);
                    newNeo4jNode.setProperty("tag", element.getNodeName());
                    newNeo4jNode.setProperty("startIndex", Integer.toUnsignedLong(plainTextBuilder.length()));
                    newNeo4jNode.setProperty("endIndex", Integer.toUnsignedLong(plainTextBuilder.length()+textContent.length()));
                    Map<String, Object> attributes = getAttributes(element);
                    attributes.forEach(newNeo4jNode::setProperty);
                    if (!textContent.isEmpty()) {
                        newNeo4jNode.setProperty(plainTextProperty, textContent);
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("{} Element: {}, attributes: {}, startIndex: {}, stopIndex: {}",
                                ".".repeat(getDepth(item)), element.getNodeName(), attributes,
                                plainTextBuilder.length(), plainTextBuilder.length()+textContent.length());
                    }
                } else if (item instanceof org.w3c.dom.Text text) {
                    if (log.isDebugEnabled()) {
                        log.debug("{} Text: {}", ".".repeat(getDepth(item)), text.getTextContent());
                    }
                    plainTextBuilder.append(text.getTextContent());
                } else {
                    throw new IllegalArgumentException("Unknown node type: " + item);
                }
            }
            String plainText = plainTextBuilder.toString();
            startNode.setProperty(plainTextProperty, plainText);
            return annotations.stream().map(ResultTypes.NodeResult::new);
        } catch (ParserConfigurationException | XPathExpressionException | SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String,Object> getAttributes(Element element) {
        NamedNodeMap attributes = element.getAttributes();
        Map<String,Object> result = new HashMap<>();
        for (int i=0; i<attributes.getLength(); i++) {
            org.w3c.dom.Node item = attributes.item(i);
            result.put(item.getNodeName(), item.getNodeValue());
        }
        return result;
    }

    private int getDepth(org.w3c.dom.Node item) {
        return getDepth(item, 0);
    }

    private int getDepth(org.w3c.dom.Node item, int depth) {
        org.w3c.dom.Node parent = item.getParentNode();
        return parent==null ? depth : getDepth(parent, depth+1);
    }
}