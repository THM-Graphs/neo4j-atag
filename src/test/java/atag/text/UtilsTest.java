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
import java.util.Map;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCause;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

}
