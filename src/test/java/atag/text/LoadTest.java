package atag.text;

import atag.atag.util.HttpServerExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.harness.junit.extension.Neo4jExtension;
import org.neo4j.internal.helpers.collection.Iterators;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LoadTest {
    @RegisterExtension
    static Neo4jExtension neo4j = Neo4jExtension.builder()
            .withDisabledServer()
            .withFunction(Load.class)
            .build();

    @RegisterExtension
    static HttpServerExtension httpServer = new HttpServerExtension();

    @Test
    public void testLoad(GraphDatabaseService db, HttpServerExtension.HttpServerInfo httpServerInfo) throws IOException {
        URI uri = httpServerInfo.getURI();
        String text = (String) db.executeTransactionally("""
                RETURN atag.text.load($uri + '/test.txt') AS text""",
                Map.of("uri", uri.toString()),
                result -> Iterators.single(result).get("text")
        );
        String expected = new String(Files.readAllBytes(Paths.get("src/test/resources/test.txt")));
        assertEquals(expected, text);
    }

}
