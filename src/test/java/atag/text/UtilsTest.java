package atag.text;

import atag.util.HttpServerExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.QueryExecutionException;
import org.neo4j.harness.junit.extension.Neo4jExtension;
import org.neo4j.internal.helpers.collection.Iterators;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.xmlunit.assertj3.XmlAssert.assertThat;

public class UtilsTest {
    @RegisterExtension
    static Neo4jExtension neo4j = Neo4jExtension.builder()
            .withDisabledServer()
            .withFunction(Utils.class)
            .build();

    @RegisterExtension
    static HttpServerExtension httpServer = new HttpServerExtension();

    @Test
    public void testLoadHttp(GraphDatabaseService db, HttpServerExtension.HttpServerInfo httpServerInfo) throws IOException {
        URI uri = httpServerInfo.getURI();
        String text = (String) db.executeTransactionally("""
                RETURN atag.text.load($uri + '/test.txt') AS text""",
                Map.of("uri", uri.toString()),
                result -> Iterators.single(result).get("text")
        );
        String expected = new String(Files.readAllBytes(Paths.get("src/test/resources/test.txt")));
        assertEquals(expected, text);
    }

    @Test
    public void testLoadFile(GraphDatabaseService db, HttpServerExtension.HttpServerInfo httpServerInfo) throws IOException {
        String text = (String) db.executeTransactionally("""
                RETURN atag.text.load($uri + '/test.txt') AS text""",
                Map.of("uri", httpServerInfo.getURI().toString()),
                result -> Iterators.single(result).get("text")
        );
        String expected = new String(Files.readAllBytes(Paths.get("src/test/resources/test.txt")));
        assertEquals(expected, text);
    }

    @Test
    public void testLoadNonExistentFile(GraphDatabaseService db, HttpServerExtension.HttpServerInfo httpServerInfo) {
        QueryExecutionException exception = Assertions.assertThrows(QueryExecutionException.class, () -> {
            URI uri = httpServerInfo.getURI();
            db.executeTransactionally("""
                            RETURN atag.text.load($uri + '/doesnotexist.txt') AS text""",
                    Map.of("uri", uri.toString()),
                    result -> Iterators.single(result).get("text")
            );
        });
        ;
        assertEquals("could not find resource " + httpServerInfo.getURI() + "/doesnotexist.txt", getRootCause(exception).getMessage());
    }

    @Test
    public void testXsltIdentity(GraphDatabaseService db, HttpServerExtension.HttpServerInfo httpServerInfo) throws IOException {
        URI uri = httpServerInfo.getURI();
        String text = (String) db.executeTransactionally("""
                RETURN atag.text.xslt(atag.text.load($uri + '/patzig.xml'), atag.text.load($uri + '/identity.xslt')) AS text""",
                Map.of("uri", uri.toString()),
                result -> Iterators.single(result).get("text")
        );
        String expected = new String(Files.readAllBytes(Paths.get("src/test/resources/patzig.xml")));

        assertThat(text).and(expected).ignoreWhitespace().areIdentical();
    }

    @Test
    public void testXsltWhitespace(GraphDatabaseService db, HttpServerExtension.HttpServerInfo httpServerInfo) throws IOException {
        URI uri = httpServerInfo.getURI();
        String xml = """
                <TEI>
                    <text>
                        <body>
                            <p>
                            abc </p>
                        </body>
                    </text>
                </TEI>""";
        String text = (String) db.executeTransactionally("""
                RETURN atag.text.xslt($xml, atag.text.load($uri + '/standoff_property.xslt')) AS text""",
                Map.of("uri", uri.toString(), "xml", xml),
                result -> Iterators.single(result).get("text")
        );

        String expected = """
                <TEI>
                   <text>
                      <body>
                         <p>abc</p>
                      </body>
                   </text>
                </TEI>
                """;

        assertEquals(expected, text);
    }


    private static final String CORPUS = """
            <teiCorpus xmlns="http://www.tei-c.org/ns/1.0">
              <teiHeader><fileDesc><titleStmt><title>Letters</title></titleStmt></fileDesc></teiHeader>
              <TEI xml:id="l-1" n="first"><text><body><p>one</p></body></text></TEI>
              <TEI xml:id="l-2"><text><body><p>two</p></body></text></TEI>
            </teiCorpus>""";

    @SuppressWarnings("unchecked")
    private static List<String> xpath(GraphDatabaseService db, String xpath) {
        return (List<String>) db.executeTransactionally(
                "RETURN atag.text.xpath($xml, $xpath) AS result",
                Map.of("xml", CORPUS, "xpath", xpath),
                result -> Iterators.single(result).get("result"));
    }

    @Test
    public void xpathCutsOutSubtreesInAnyNamespace(GraphDatabaseService db) {
        List<String> letters = xpath(db, "/*:teiCorpus/*:TEI");
        assertEquals(2, letters.size());
        assertThat(letters.get(0)).and("""
                <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id="l-1" n="first"><text><body><p>one</p></body></text></TEI>""")
                .areIdentical();
        assertTrue(letters.get(0).startsWith("<TEI"), "no XML declaration: " + letters.get(0));
    }

    @Test
    public void xpathReturnsAttributesAndAtomicValuesAsStrings(GraphDatabaseService db) {
        assertEquals(List.of("first"), xpath(db, "/*:teiCorpus/*:TEI[1]/@n"));
        assertEquals(List.of("Letters"), xpath(db, "//*:title/string()"));
        assertEquals(List.of("2"), xpath(db, "count(//*:TEI)"));
        assertEquals(List.of(), xpath(db, "/*:teiCorpus/*:TEI/@missing"));
    }

    @Test
    public void xpathRejectsAnInvalidExpression(GraphDatabaseService db) {
        QueryExecutionException exception = Assertions.assertThrows(QueryExecutionException.class,
                () -> xpath(db, "/*:teiCorpus/["));
        assertTrue(getRootCause(exception).getMessage().contains("Expected an expression"), getRootCause(exception).getMessage());
    }
}
