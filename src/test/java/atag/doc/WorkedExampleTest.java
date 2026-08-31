package atag.doc;

import atag.export.ExporterProcedures;
import atag.model.ModelProcedures;
import atag.text.Importer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.harness.junit.extension.Neo4jExtension;
import org.neo4j.internal.helpers.collection.Iterators;
import org.xmlunit.assertj3.XmlAssert;

import javax.xml.transform.stream.StreamSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The walkthrough of <em>src/site/markdown/worked-example.md</em>, executed: a project
 * model written to the graph, a TEI letter imported through it, and the same letter
 * exported again in both serializations. The documented Cypher statements and their
 * results are asserted here, so the documentation cannot drift away from what the
 * procedures actually do.
 */
class WorkedExampleTest {

    private static final String TEI = """
            <TEI xmlns="http://www.tei-c.org/ns/1.0">
              <teiHeader><fileDesc><titleStmt><title>Letter to Berthold</title></titleStmt></fileDesc></teiHeader>
              <text><body><ab xml:id="letter-1"><s xml:id="s-1"><persName xml:id="p-1" ref="#hildegard">Hildegard</persName> writes to <persName xml:id="p-2" ref="#berthold">Berthold</persName>.</s> <s xml:id="s-2">She sends greetings.</s></ab></body></text>
              <standOff>
                <listAnnotation>
                  <annotation xml:id="l-1" target="#string-range(letter-1,0,33)" type="line"/>
                  <annotation xml:id="l-2" target="#string-range(letter-1,34,16)" type="line"/>
                  <annotation xml:id="c-1" target="#p-1" type="commentary" note="uncertain reading"/>
                </listAnnotation>
                <list type="entity">
                  <item xml:id="hildegard" n="Hildegard von Bingen" type="Person" wikidataId="Q70991"/>
                  <item xml:id="berthold" n="Berthold von Zwiefalten" type="Person"/>
                </list>
              </standOff>
            </TEI>
            """;

    private static final Map<String, Object> MODEL = Map.of("model", Map.of(
            "collection", List.of("Manuscript"),
            "content", List.of("Transcript"),
            "annotation", List.of("Annotation"),
            "entity", List.of("Entity")));

    private static final Map<String, Object> DICTIONARY =
            Map.of("elements", Map.of("persName", "person-reference", "s", "sentence"));

    private static final Map<String, Object> IMPORT_PROFILE = Map.of(
            "model", "meta",
            "dictionary", DICTIONARY,
            "createMissingEntities", true);

    private static final Map<String, Object> EXPORT_PROFILE = Map.of(
            "model", "meta",
            "dictionary", DICTIONARY,
            "ignoreProperties", List.of("xml"));

    private static final String PLAIN_TEXT = "Hildegard writes to Berthold. She sends greetings.";

    private static final String HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<TEI xmlns=\"http://www.tei-c.org/ns/1.0\"><teiHeader><fileDesc><titleStmt>"
            + "<title>%s</title></titleStmt><publicationStmt>"
            + "<p>exported from a Neo4j property graph by neo4j-atag</p></publicationStmt>"
            + "<sourceDesc><p>born-digital graph data</p></sourceDesc></fileDesc></teiHeader>";

    private static final String ENTITIES = "<list type=\"entity\">"
            + "<item xml:id=\"berthold\" n=\"Berthold von Zwiefalten\" type=\"Entity,Person\"/>"
            + "<item xml:id=\"hildegard\" n=\"Hildegard von Bingen\" type=\"Entity,Person\" wikidataId=\"Q70991\"/>"
            + "</list></standOff></TEI>";

    private static final String INLINE_TEXT = "<seg xml:id=\"l-1\" type=\"line\"><s xml:id=\"s-1\">"
            + "<persName xml:id=\"p-1\" ref=\"#hildegard\">Hildegard</persName> writes to "
            + "<persName xml:id=\"p-2\" ref=\"#berthold\">Berthold</persName>.</s> She</seg>"
            + " <seg xml:id=\"l-2\" type=\"line\">sends greetings.</seg>";

    private static final String INLINE_STANDOFF = "<standOff><listAnnotation>"
            + "<annotation target=\"#string-range(letter-1,30,20)\" xml:id=\"s-2\" type=\"sentence\"/>"
            + "<annotation target=\"#p-1\" xml:id=\"c-1\" note=\"uncertain reading\" type=\"commentary\"/>"
            + "</listAnnotation>";

    @RegisterExtension
    static Neo4jExtension neo4j = Neo4jExtension.builder()
            .withProcedure(ModelProcedures.class)
            .withProcedure(Importer.class)
            .withProcedure(ExporterProcedures.class)
            .withConfig(GraphDatabaseSettings.procedure_unrestricted, List.of("atag.*"))
            .withDisabledServer()
            .build();

    @Test
    void walkthrough(GraphDatabaseService db) {
        db.executeTransactionally("CALL atag.model.meta.write($config) YIELD value RETURN value",
                Map.of("config", MODEL));

        db.executeTransactionally("""
                CREATE (m:Manuscript {uuid: 'ms-1', label: 'Cod. Sang. 963'})
                CREATE (t:Transcript {uuid: 'letter-1', xml: $xml})
                CREATE (t)-[:PART_OF]->(m)
                """, Map.of("xml", TEI));

        long imported = db.executeTransactionally("""
                MATCH (t:Transcript {uuid: 'letter-1'})
                CALL atag.text.import.tei(t, 'xml', $profile) YIELD node
                RETURN count(node) AS count
                """, Map.of("profile", IMPORT_PROFILE), r -> (Long) Iterators.single(r).get("count"));
        assertEquals(7, imported, "four inline elements, two stand-off lines and the commentary");

        assertEquals(PLAIN_TEXT, db.executeTransactionally(
                "MATCH (t:Transcript {uuid: 'letter-1'}) RETURN t.plainText AS plainText", Map.of(),
                r -> (String) Iterators.single(r).get("plainText")));

        assertEquals(List.of(
                        "l-1 | line | null | 0 | 33",
                        "s-1 | sentence | s | 0 | 29",
                        "p-1 | person-reference | persName | 0 | 9",
                        "p-2 | person-reference | persName | 20 | 28",
                        "s-2 | sentence | s | 30 | 50",
                        "l-2 | line | null | 34 | 50"),
                rows(db, """
                        MATCH (:Transcript {uuid: 'letter-1'})-[:HAS_ANNOTATION]->(a:Annotation)
                        RETURN a.uuid + ' | ' + a.type + ' | ' + coalesce(a.tag, 'null')
                               + ' | ' + a.startIndex + ' | ' + a.endIndex AS row
                        ORDER BY a.startIndex, a.endIndex DESC
                        """),
                "an inline element and a stand-off annotation become the same kind of node");

        assertEquals(List.of(
                        "p-1 | hildegard | Hildegard von Bingen | Q70991",
                        "p-2 | berthold | Berthold von Zwiefalten | null"),
                rows(db, """
                        MATCH (:Transcript {uuid: 'letter-1'})-[:HAS_ANNOTATION]->(a:Annotation)-[:REFERS_TO]->(e:Entity)
                        RETURN a.uuid + ' | ' + e.uuid + ' | ' + e.label
                               + ' | ' + coalesce(e.wikidataId, 'null') AS row
                        ORDER BY a.uuid
                        """));

        assertEquals(List.of("p-1 | c-1 | commentary | uncertain reading"),
                rows(db, """
                        MATCH (:Transcript {uuid: 'letter-1'})-[:HAS_ANNOTATION]->(a:Annotation)
                        MATCH (a)-[:HAS_ANNOTATION]->(c:Annotation)
                        RETURN a.uuid + ' | ' + c.uuid + ' | ' + c.type + ' | ' + c.note AS row
                        """),
                "an annotation targeting an annotation hangs off that annotation, not off the text");

        String inline = exportTei(db, "letter-1", EXPORT_PROFILE);
        assertEquals(HEADER.formatted("ATAG export")
                        + "<text><body><ab xml:id=\"letter-1\">" + INLINE_TEXT + "</ab></body></text>"
                        + INLINE_STANDOFF + ENTITIES,
                inline,
                "the second sentence crosses the first line, so it cannot be nested and moves to standOff");

        String standoff = exportTei(db, "letter-1", with("serialization", "standoff"));
        assertEquals(HEADER.formatted("ATAG export")
                        + "<text><body><ab xml:id=\"letter-1\">" + PLAIN_TEXT + "</ab></body></text>"
                        + "<standOff><listAnnotation>"
                        + "<annotation target=\"#string-range(letter-1,0,33)\" xml:id=\"l-1\" type=\"line\"/>"
                        + "<annotation target=\"#string-range(letter-1,0,29)\" xml:id=\"s-1\" type=\"sentence\"/>"
                        + "<annotation target=\"#string-range(letter-1,0,9)\" xml:id=\"p-1\" ref=\"#hildegard\" type=\"person-reference\"/>"
                        + "<annotation target=\"#p-1\" xml:id=\"c-1\" note=\"uncertain reading\" type=\"commentary\"/>"
                        + "<annotation target=\"#string-range(letter-1,20,8)\" xml:id=\"p-2\" ref=\"#berthold\" type=\"person-reference\"/>"
                        + "<annotation target=\"#string-range(letter-1,30,20)\" xml:id=\"s-2\" type=\"sentence\"/>"
                        + "<annotation target=\"#string-range(letter-1,34,16)\" xml:id=\"l-2\" type=\"line\"/>"
                        + "</listAnnotation>" + ENTITIES,
                standoff);

        String fromCollection = exportTei(db, "ms-1", EXPORT_PROFILE);
        assertEquals(HEADER.formatted("Cod. Sang. 963")
                        + "<text><body><div xml:id=\"ms-1\" label=\"Cod. Sang. 963\">"
                        + "<ab xml:id=\"letter-1\">" + INLINE_TEXT + "</ab></div></body></text>"
                        + INLINE_STANDOFF + ENTITIES,
                fromCollection,
                "starting at the manuscript keeps the collection as a div around its transcripts");

        db.executeTransactionally("CREATE (t:Transcript {uuid: 'letter-2', xml: $xml})", Map.of("xml", inline));
        db.executeTransactionally("""
                MATCH (t:Transcript {uuid: 'letter-2'})
                CALL atag.text.import.tei(t, 'xml', $profile) YIELD node
                RETURN count(node) AS count
                """, Map.of("profile", IMPORT_PROFILE));

        assertEquals(rows(db, spans("letter-1")), rows(db, spans("letter-2")),
                "the roundtrip is semantic: every annotated range comes back with its type, "
                        + "no matter which of the two encodings carried it");
    }

    @Test
    void everyExportOfTheWalkthroughIsValidTei(GraphDatabaseService db) {
        db.executeTransactionally("CALL atag.model.meta.write($config) YIELD value RETURN value",
                Map.of("config", MODEL));
        db.executeTransactionally("""
                CREATE (m:Manuscript {uuid: 'ms-2', label: 'Cod. Sang. 963'})
                CREATE (t:Transcript {uuid: 'letter-3', xml: $xml})
                CREATE (t)-[:PART_OF]->(m)
                """, Map.of("xml", TEI));
        db.executeTransactionally("""
                MATCH (t:Transcript {uuid: 'letter-3'})
                CALL atag.text.import.tei(t, 'xml', $profile) YIELD node
                RETURN count(node) AS count
                """, Map.of("profile", IMPORT_PROFILE));

        assertValidTei(exportTei(db, "letter-3", EXPORT_PROFILE));
        assertValidTei(exportTei(db, "letter-3", with("serialization", "standoff")));
        assertValidTei(exportTei(db, "ms-2", EXPORT_PROFILE));
    }

    private void assertValidTei(String tei) {
        XmlAssert.assertThat(tei).isValidAgainst(
                new StreamSource(WorkedExampleTest.class.getResourceAsStream("/tei-atag-export.xsd")));
    }

    private String exportTei(GraphDatabaseService db, String uuid, Map<String, Object> profile) {
        return db.executeTransactionally("""
                MATCH (n {uuid: $uuid})
                CALL atag.export.tei.fromNode(n, $profile) YIELD value
                RETURN value
                """, Map.of("uuid", uuid, "profile", profile),
                r -> (String) Iterators.single(r).get("value"));
    }

    private static String spans(String uuid) {
        return """
                MATCH (:Transcript {uuid: '%s'})-[:HAS_ANNOTATION]->(a:Annotation)
                RETURN a.type + ' | ' + a.startIndex + ' | ' + a.endIndex AS row
                ORDER BY a.startIndex, a.endIndex DESC
                """.formatted(uuid);
    }

    private List<String> rows(GraphDatabaseService db, String query) {
        return db.executeTransactionally(query, Map.of(),
                r -> Iterators.asList(r).stream().map(row -> (String) row.get("row")).toList());
    }

    private static Map<String, Object> with(String key, Object value) {
        Map<String, Object> profile = new LinkedHashMap<>(EXPORT_PROFILE);
        profile.put(key, value);
        return profile;
    }
}
