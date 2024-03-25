package atag.text;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.harness.junit.extension.Neo4jExtension;
import org.neo4j.internal.helpers.collection.Iterators;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ImporterTest {

    @RegisterExtension
    static Neo4jExtension neo4j = Neo4jExtension.builder()
            .withDisabledServer()
            .withFixture("CREATE (t:Text {id:1, text: 'This <em>is</em> a <em>emphasized test with a <a href=\\'ref\\'>link</a></em>. We also have a <br/> line break.'})")
            .withProcedure(Importer.class)
            .build();

    @Test
    public void testAnnotate(GraphDatabaseService db) {
        // validate direct annotations
        db.executeTransactionally("""
                        MATCH (t:Text{id: 1})
                        CALL atag.text.import.html(t, 'text', 'Annotation') YIELD node
                        RETURN node.startIndex as startIndex, node.endIndex as endIndex, node.tag as tag, node.text as text""",
                Collections.emptyMap(), result -> {
                    List<Map<String, Object>> list = Iterators.asList(result);
                    assertEquals(3, list.size());

                    assertThat(list.get(0), Matchers.<Map<String, Object>>allOf(
                            hasEntry("startIndex", 5L),
                            hasEntry("endIndex", 7L),
                            hasEntry("tag", "em"),
                            hasEntry("text", "is")
                    ));

                    assertThat(list.get(1), Matchers.<Map<String, Object>>allOf(
                            hasEntry("startIndex", 10L),
                            hasEntry("endIndex", 37L),
                            hasEntry("tag", "em"),
                            hasEntry("text", "emphasized test with a link")
                    ));

                    assertThat(list.get(2), Matchers.<Map<String, Object>>allOf(
                            hasEntry("startIndex", 54L),
                            hasEntry("endIndex", 54L),
                            hasEntry("tag", "br"),
                            hasEntry("text", "")
                    ));
                    return list.size();
                });

        String plainText = db.executeTransactionally("MATCH (t:Text{id: 1}) RETURN t.plainText as plainText",
                Collections.emptyMap(), result -> Iterators.single(result).get("plainText").toString());
        assertEquals("This is a emphasized test with a link. We also have a  line break.", plainText);

        // validate nested annotation
        db.executeTransactionally("MATCH (t:Text{id: 1})-[:HAS_ANNOTATION]->(:Annotation{startIndex:10})-[:HAS_ANNOTATION]->(node:Annotation) RETURN node.startIndex as startIndex, node.endIndex as endIndex, node.tag as tag, node.text as text",
                Collections.emptyMap(), result -> {
                    List<Map<String, Object>> list = Iterators.asList(result);
                    assertEquals(1, list.size());
                    assertThat(list.get(0), Matchers.<Map<String, Object>>allOf(
                            hasEntry("startIndex", 33L),
                            hasEntry("endIndex", 37L),
                            hasEntry("tag", "a"),
                            hasEntry("text", "link")
                    ));
                    return null;
                });
    }

}
