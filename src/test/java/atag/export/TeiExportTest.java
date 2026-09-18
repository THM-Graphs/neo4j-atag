package atag.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.harness.junit.extension.Neo4jExtension;
import org.neo4j.internal.helpers.collection.Iterators;
import org.xmlunit.assertj3.XmlAssert;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The text is {@code 'Hildegard writes to Berthold.'}, annotated by a person reference
 * on {@code Hildegard} [0,9), a sentence spanning the whole text [0,29), a phrase [5,20)
 * that overlaps the person reference, and a commentary on the person reference.
 * <p>
 * Every export is validated against {@code tei-atag-export.xsd}, the TEI subset the
 * exporter is allowed to produce, and then inspected with XPath - so a test fails on a
 * document that is merely well-formed, and the assertions describe the document rather
 * than the string it happens to be serialized into.
 */
class TeiExportTest {

    private static final String TEI_NS = "http://www.tei-c.org/ns/1.0";
    private static final Map<String, String> NAMESPACES =
            Map.of("tei", TEI_NS, "xml", XMLConstants.XML_NS_URI);

    private static final String TEXT = "Hildegard writes to Berthold.";

    private static final Map<String, Object> DICTIONARY = Map.of("dictionary", Map.of(
            "elements", Map.of("persName", "person-reference", "s", "sentence", "phr", "phrase")));

    @RegisterExtension
    static Neo4jExtension neo4j = Neo4jExtension.builder()
            .withProcedure(ExporterProcedures.class)
            .withConfig(GraphDatabaseSettings.procedure_unrestricted, List.of("atag.*"))
            .withDisabledServer()
            .withFixture("""
                CREATE (letter:Collection {uuid: 'letter-1', label: 'Letter R86'})
                CREATE (t:Text {uuid: 'text-1', text: 'Hildegard writes to Berthold.'})
                CREATE (t)-[:PART_OF]->(letter)

                CREATE (hildegard:Entity:Person {uuid: 'hildegard', label: 'Hildegard von Bingen', wikidataId: 'Q70991'})

                CREATE (a1:Annotation {uuid: 'a-1', type: 'person-reference', startIndex: 0, endIndex: 9})
                CREATE (t)-[:HAS_ANNOTATION]->(a1)
                CREATE (a1)-[:REFERS_TO]->(hildegard)
                CREATE (a2:Annotation {uuid: 'a-2', type: 'sentence', startIndex: 0, endIndex: 29})
                CREATE (t)-[:HAS_ANNOTATION]->(a2)
                CREATE (a3:Annotation {uuid: 'a-3', type: 'phrase', startIndex: 5, endIndex: 20})
                CREATE (t)-[:HAS_ANNOTATION]->(a3)
                CREATE (c1:Annotation {uuid: 'c-1', type: 'commentary', note: 'uncertain reading'})
                CREATE (a1)-[:HAS_ANNOTATION]->(c1)

                // a corpus whose parts carry headers, with same-range annotations that only depth tells apart
                CREATE (corpus:Collection {uuid: 'corpus-1', teiHeader: '<teiHeader><fileDesc><titleStmt><title>Corpus</title></titleStmt></fileDesc></teiHeader>'})
                CREATE (w1:Text {uuid: 'w-1', n: 'first', text: 'Reipub. abc', teiHeader: '<teiHeader xmlns="http://www.tei-c.org/ns/1.0"><fileDesc><titleStmt><title>Witness</title></titleStmt></fileDesc></teiHeader>'})
                CREATE (w1)-[:PART_OF]->(corpus)
                CREATE (w2:Text {uuid: 'w-2', text: 'plain'})
                CREATE (w2)-[:PART_OF]->(corpus)
                CREATE (boulliau:Entity:Person {uuid: 'boulliau', tag: 'person', tei: '<person xml:id="boulliau"><persName>Boulliau</persName></person>'})
                CREATE (boulliau)-[:PART_OF]->(corpus)
                CREATE (del:Annotation {tag: 'del', rendition: '#s', startIndex: 0, endIndex: 7, depth: 4})
                CREATE (subst:Annotation {tag: 'subst', startIndex: 0, endIndex: 7, depth: 3})
                CREATE (w1)-[:HAS_ANNOTATION]->(del)
                CREATE (w1)-[:HAS_ANNOTATION]->(subst)
                CREATE (rs:Annotation {tag: 'rs', corresp: 'letter-7', startIndex: 8, endIndex: 11, depth: 3})
                CREATE (w1)-[:HAS_ANNOTATION]->(rs)
                CREATE (rs)-[:REFERS_TO]->(boulliau)
                CREATE (note:Annotation {tag: 'note', type: 'commentary'})
                CREATE (rs)-[:HAS_ANNOTATION]->(note)
                """)
            .build();

    private String exportTei(GraphDatabaseService db, String uuid, Map<String, Object> config) {
        return db.executeTransactionally("""
                MATCH (n {uuid: $uuid})
                CALL atag.export.tei.fromNode(n, $config) YIELD value
                RETURN value
                """, Map.of("uuid", uuid, "config", config),
                r -> (String) Iterators.single(r).get("value"));
    }

    /**
     * Exports and validates in one step: every assertion in this test class starts from a
     * document that is known to conform to the export schema.
     */
    private XmlAssert exportValidTei(GraphDatabaseService db, String uuid, Map<String, Object> config) {
        String tei = exportTei(db, uuid, config);
        XmlAssert assertion = XmlAssert.assertThat(tei).withNamespaceContext(NAMESPACES);
        assertion.isValidAgainst(new StreamSource(
                TeiExportTest.class.getResourceAsStream("/tei-atag-export.xsd")));
        return assertion;
    }

    @Test
    void nonOverlappingAnnotationsAreWrittenInline(GraphDatabaseService db) {
        XmlAssert tei = exportValidTei(db, "text-1", DICTIONARY);

        tei.valueByXPath("/tei:TEI/tei:text/tei:body/tei:ab/@xml:id").isEqualTo("text-1");
        tei.valueByXPath("//tei:ab").isEqualTo(TEXT);
        tei.valueByXPath("//tei:ab/tei:s/@xml:id").isEqualTo("a-2");
        tei.valueByXPath("//tei:s/tei:persName/@xml:id").isEqualTo("a-1");
        tei.valueByXPath("//tei:persName").isEqualTo("Hildegard");
        tei.valueByXPath("//tei:persName/@ref").isEqualTo("#hildegard");
        tei.doesNotHaveXPath("//tei:persName[@type]");
    }

    @Test
    void overlappingAnnotationsAreWrittenAsStandoff(GraphDatabaseService db) {
        XmlAssert tei = exportValidTei(db, "text-1", DICTIONARY);

        tei.doesNotHaveXPath("//tei:ab//tei:phr");
        tei.valueByXPath("//tei:standOff/tei:listAnnotation/tei:annotation[@xml:id='a-3']/@target")
                .isEqualTo("#string-range(text-1,5,15)");
        tei.valueByXPath("//tei:annotation[@xml:id='a-3']/@type").isEqualTo("phrase");
    }

    @Test
    void annotationsOnAnnotationsPointAtTheirAnnotation(GraphDatabaseService db) {
        XmlAssert tei = exportValidTei(db, "text-1", DICTIONARY);

        tei.valueByXPath("//tei:annotation[@xml:id='c-1']/@target").isEqualTo("#a-1");
        tei.valueByXPath("//tei:annotation[@xml:id='c-1']/@note").isEqualTo("uncertain reading");
        tei.hasXPath("//*[@xml:id='a-1']");
    }

    @Test
    void entitiesAreDeclaredInStandoff(GraphDatabaseService db) {
        XmlAssert tei = exportValidTei(db, "text-1", DICTIONARY);

        tei.valueByXPath("//tei:standOff/tei:list[@type='entity']/tei:item/@xml:id").isEqualTo("hildegard");
        tei.valueByXPath("//tei:item[@xml:id='hildegard']/@n").isEqualTo("Hildegard von Bingen");
        tei.valueByXPath("//tei:item[@xml:id='hildegard']/@wikidataId").isEqualTo("Q70991");
        tei.hasXPath("//tei:item[@xml:id = substring-after(//tei:persName/@ref, '#')]");
    }

    @Test
    void standoffSerializationKeepsEveryAnnotationOutOfTheText(GraphDatabaseService db) {
        XmlAssert tei = exportValidTei(db, "text-1", Map.of("serialization", "standoff"));

        tei.valueByXPath("//tei:ab").isEqualTo(TEXT);
        tei.nodesByXPath("//tei:ab/*").hasSize(0);
        tei.nodesByXPath("//tei:standOff//tei:annotation").hasSize(4);
    }

    @Test
    void collectionsBecomeDivisions(GraphDatabaseService db) {
        XmlAssert tei = exportValidTei(db, "letter-1", DICTIONARY);

        tei.valueByXPath("//tei:teiHeader//tei:title").isEqualTo("Letter R86");
        tei.hasXPath("/tei:TEI/tei:text/tei:body/tei:div[@xml:id='letter-1']/tei:ab[@xml:id='text-1']");
    }

    @Test
    void annotationsWithoutADictionaryEntryKeepTheirType(GraphDatabaseService db) {
        XmlAssert tei = exportValidTei(db, "text-1", Collections.emptyMap());

        tei.valueByXPath("//tei:seg[@xml:id='a-2']/@type").isEqualTo("sentence");
        tei.valueByXPath("//tei:ab").isEqualTo(TEXT);
    }

    private static final Map<String, Object> CORPUS_PROFILE = Map.of(
            "referenceAttribute", "corresp", "entitySourceProperty", "tei");

    @Test
    void anchorsWithAHeaderBecomeNestedDocuments(GraphDatabaseService db) {
        XmlAssert tei = exportValidTei(db, "corpus-1", CORPUS_PROFILE);

        tei.valueByXPath("/tei:TEI/@xml:id").isEqualTo("corpus-1");
        tei.valueByXPath("/tei:TEI/tei:teiHeader//tei:title").isEqualTo("Corpus");
        tei.valueByXPath("/tei:TEI/tei:TEI/@xml:id").isEqualTo("w-1");
        tei.valueByXPath("/tei:TEI/tei:TEI/@n").isEqualTo("first");
        tei.valueByXPath("/tei:TEI/tei:TEI/tei:teiHeader//tei:title").isEqualTo("Witness");
        tei.valueByXPath("/tei:TEI/tei:TEI/tei:text/tei:body/tei:ab").isEqualTo("Reipub. abc");
        tei.doesNotHaveXPath("/tei:TEI/tei:TEI/tei:text/tei:body/tei:ab/@xml:id");
        tei.hasXPath("/tei:TEI/tei:text/tei:body/tei:ab[@xml:id='w-2']");
        tei.nodesByXPath("//tei:standOff").hasSize(1);
        tei.doesNotHaveXPath("//tei:TEI//tei:teiHeader/@teiHeader");
    }

    @Test
    void sameRangeAnnotationsNestByTheirDepth(GraphDatabaseService db) {
        XmlAssert tei = exportValidTei(db, "corpus-1", CORPUS_PROFILE);

        tei.valueByXPath("//tei:ab/tei:subst/tei:del/@rendition").isEqualTo("#s");
        tei.doesNotHaveXPath("//tei:del/tei:subst");
        tei.doesNotHaveXPath("//tei:listAnnotation/tei:annotation[@target = '#string-range(atag-1,0,7)']");
    }

    @Test
    void identifiersAreOnlyWrittenWhereTheSourceHadOneOrAPointerNeedsOne(GraphDatabaseService db) {
        XmlAssert tei = exportValidTei(db, "corpus-1", CORPUS_PROFILE);

        tei.doesNotHaveXPath("//tei:subst/@xml:id");
        tei.doesNotHaveXPath("//tei:del/@xml:id");
        tei.valueByXPath("//tei:rs/@xml:id").isEqualTo("atag-1");
        tei.valueByXPath("//tei:annotation[@type='commentary']/@target").isEqualTo("#atag-1");
    }

    @Test
    void resolvedAndUnresolvedReferencesShareTheReferenceAttribute(GraphDatabaseService db) {
        XmlAssert tei = exportValidTei(db, "corpus-1", CORPUS_PROFILE);

        tei.valueByXPath("//tei:rs/@corresp").isEqualTo("#boulliau letter-7");
        tei.doesNotHaveXPath("//tei:rs/@ref");
    }

    @Test
    void verbatimEntityDeclarationsAreWrittenIntoTheirTeiList(GraphDatabaseService db) {
        XmlAssert tei = exportValidTei(db, "corpus-1", CORPUS_PROFILE);

        tei.valueByXPath("//tei:standOff/tei:listPerson/tei:person/@xml:id").isEqualTo("boulliau");
        tei.valueByXPath("//tei:standOff/tei:listPerson/tei:person/tei:persName").isEqualTo("Boulliau");
        tei.doesNotHaveXPath("//tei:standOff/tei:list");
    }
}
