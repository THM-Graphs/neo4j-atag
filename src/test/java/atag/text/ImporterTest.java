package atag.text;

import org.apache.commons.io.IOUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.ResourceIterator;
import org.neo4j.harness.junit.extension.Neo4jExtension;
import org.neo4j.internal.helpers.collection.Iterators;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ImporterTest {

/*    @SystemStub
    static private EnvironmentVariables environmentVariables = new EnvironmentVariables()
            .set("apoc.import.file.enabled", "true")
            .set("apoc.import.file.use_neo4j_config", "false");

    @RegisterExtension
    @Order(1)
    static SystemStubsExtension systemStubsExtension = new SystemStubsExtension();*/

    @RegisterExtension
    @Order(2)
    static Neo4jExtension neo4j = Neo4jExtension.builder()
            .withDisabledServer()
            .withFixture("""
            CREATE (t:Text {id:1, text: 'This <em>is</em> a <em>emphasized test with a <a href=\\'ref\\'>link</a></em>. We also have a <br/> line break.'})
            """)
            .withFixture(db -> {
                        try {
                            String xml = IOUtils.toString(ImporterTest.class.getResourceAsStream("/patzig.xml"), StandardCharsets.UTF_8);
                            db.executeTransactionally("""
                                    CREATE (t:Text {id:2, text:$xml})
                                    """, Map.of("xml", xml));
                            return null;
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
            )
            .withProcedure(Importer.class)
//            .withProcedure(Xml.class)
            .build();

    @Test
    public void testAnnotate(GraphDatabaseService db) {
        // validate direct annotations
        db.executeTransactionally("""
                        MATCH (t:Text{id: 1})
                        CALL atag.text.import.html(t, 'text', 'Annotation', 'myPlainText') YIELD node
                        RETURN node.startIndex as startIndex, node.endIndex as endIndex, node.tag as tag, node.myPlainText as myPlainText""",
                Collections.emptyMap(), result -> {
                    List<Map<String, Object>> list = Iterators.asList(result);
                    assertEquals(4, list.size());

                    assertThat(list.get(0), Matchers.<Map<String, Object>>allOf(
                            hasEntry("startIndex", 5L),
                            hasEntry("endIndex", 7L),
                            hasEntry("tag", "em"),
                            hasEntry("myPlainText", "is")
                    ));

                    assertThat(list.get(1), Matchers.<Map<String, Object>>allOf(
                            hasEntry("startIndex", 10L),
                            hasEntry("endIndex", 37L),
                            hasEntry("tag", "em"),
                            hasEntry("myPlainText", "emphasized test with a link")
                    ));

                    assertThat(list.get(2), Matchers.<Map<String, Object>>allOf(
                            hasEntry("startIndex", 33L),
                            hasEntry("endIndex", 37L),
                            hasEntry("tag", "a"),
                            hasEntry("myPlainText", "link")
                    ));

                    assertThat(list.get(3), Matchers.<Map<String, Object>>allOf(
                            hasEntry("startIndex", 54L),
                            hasEntry("endIndex", 54L),
                            hasEntry("tag", "br"),
                            hasEntry("myPlainText", "")
                    ));
                    return list.size();
                });

        String plainText = db.executeTransactionally("MATCH (t:Text{id: 1}) RETURN t.myPlainText as plainText",
                Collections.emptyMap(), result -> Iterators.single(result).get("plainText").toString());
        assertEquals("This is a emphasized test with a link. We also have a  line break.", plainText);
    }

    @Test
    public void testAnnotateXml(GraphDatabaseService db) throws IOException {
        String xml = IOUtils.toString(Objects.requireNonNull(this.getClass().getResourceAsStream("/patzig.xml")), UTF_8);
        db.executeTransactionally("""
                MATCH (t:Text{id: 2})
                CALL atag.text.import.xml(t, 'text') YIELD node
                RETURN properties(node) as node
                """, Map.of("xml", xml), result -> {
            ResourceIterator<Object> node = result.columnAs("node");
            List<Object> list = node.stream().toList();

//            list.forEach(System.out::println);
            assertEquals(70, list.size());

            assertThat((Map<String, Object>) list.get(63), Matchers.<Map<String, Object>>allOf(
                    hasEntry("startIndex", 1029L),
                    hasEntry("endIndex", 1045L),
                    hasEntry("tag", "choice"),
                    hasEntry("plainText", "TrabantTrabanten")
            ));
            return null;
        });

        db.executeTransactionally("MATCH (t:Text{id: 2}) RETURN t.plainText as plainText",
                Collections.emptyMap(), result -> {
                    String plainText = Iterators.single(result).get("plainText").toString();
                    assertEquals("TrabantTrabanten", plainText.substring(1029, 1045));
                    return null;
                });
    }

}
