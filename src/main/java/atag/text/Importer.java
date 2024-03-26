package atag.text;

import atag.util.ResultTypes;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.neo4j.graphdb.*;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

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
                .map(ResultTypes.NodeResult::new);
    }

    private long traverse(int depth, org.jsoup.nodes.Node node, long index, StringBuilder plainTextBuilder,
                          Node neo4jNode, Label label, RelationshipType relationshipType, String plainTextProperty) {
        switch (node) {
            case org.jsoup.nodes.Element element:

                if (depth>0){
                    Node newNeo4jNode = tx.createNode(label);
                    neo4jNode.createRelationshipTo(newNeo4jNode, relationshipType);
                    newNeo4jNode.setProperty("startIndex", index);
                    newNeo4jNode.setProperty("tag", element.nodeName());
                    neo4jNode = newNeo4jNode;
                }

                log.debug(" ".repeat(depth) + "Depth: {}, Element: {}, index: {}", depth, element.nodeName(), index);
                StringBuilder localPlainTextBuilder = new StringBuilder();
                for (org.jsoup.nodes.Node child : element.childNodes()) {
                    index = traverse(depth+1, child, index, localPlainTextBuilder, neo4jNode, label,
                            relationshipType, plainTextProperty);
                }

                if (depth>0){
                    neo4jNode.setProperty("endIndex", index);
                    neo4jNode.setProperty(plainTextProperty, localPlainTextBuilder.toString());
                }
                plainTextBuilder.append(localPlainTextBuilder);
                break;
            case org.jsoup.nodes.TextNode textNode:
                plainTextBuilder.append(textNode.text());
                index += textNode.text().length();
                log.debug("Text: {}, index: {}", textNode.text(), index);
                break;
            default:
                throw new IllegalArgumentException("Unknown node type: " + node);
        }
        return index;
    }
}
