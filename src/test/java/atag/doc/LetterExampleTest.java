package atag.doc;

import atag.export.ExporterProcedures;
import atag.model.ModelProcedures;
import atag.text.Importer;
import atag.text.Utils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.harness.junit.extension.Neo4jExtension;
import org.neo4j.internal.helpers.collection.Iterators;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xmlunit.assertj3.XmlAssert;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The walkthrough of <em>src/site/markdown/worked-example-letter.md</em>, executed: a
 * letter from a real digital edition - a TEI corpus with a register of entities, a cover
 * letter and two witnesses - goes into the graph and comes out again, and the export is
 * compared with the source element by element. The documented statements and results are
 * asserted here, so the documentation cannot drift away from what the procedures do.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LetterExampleTest {

    private static final String RESOURCE = "/import-export/LETTER_MAIN_ed_kbj_wfw_xmb.xml";
    private static final String TEI_NS = "http://www.tei-c.org/ns/1.0";
    private static final Map<String, String> NAMESPACES = Map.of("tei", TEI_NS, "xml", XMLConstants.XML_NS_URI);

    /** the attributes the profile declares as entity references */
    private static final Set<String> REFERENCE_ATTRIBUTES = Set.of("corresp", "sameAs");

    /**
     * The profile of the edition: the project model, how the corpus is taken apart, where
     * its registers are, and how references are read and written. Stored once, referred
     * to by name afterwards.
     */
    private static final Map<String, Object> PROFILE = Map.ofEntries(
            Map.entry("name", "sozinianer"),
            Map.entry("model", Map.of(
                    "collection", List.of("Corpus", "Letter"),
                    "content", List.of("Witness"),
                    "annotation", List.of("Annotation"),
                    "entity", List.of("Entity"))),
            Map.entry("documents", List.of(
                    Map.of("xpath", "/*:teiCorpus", "concept", "collection", "label", "Corpus",
                            "id", "sozinianer", "headerXPath", "/*:teiCorpus/*:teiHeader"),
                    Map.of("xpath", "/*:teiCorpus/*:TEI", "concept", "collection", "label", "Letter"),
                    Map.of("xpath", "/*:TEI/*:TEI", "concept", "content", "label", "Witness"))),
            Map.entry("registers", List.of(
                    Map.of("xpath", "/*:teiCorpus/*:standOff/*:listPerson/*:person", "labels", List.of("Person")),
                    Map.of("xpath", "/*:teiCorpus/*:standOff/*:listPlace/*:place", "labels", List.of("Place")),
                    Map.of("xpath", "/*:teiCorpus/*:standOff/*:list[@n='terms']/*:item", "labels", List.of("Term")))),
            Map.entry("import", Map.of(
                    "rootElement", "teiCorpus",
                    "xpath", "/*:TEI/*:text/*:body//node()[not(self::*:ab)]",
                    "referenceAttributes", List.of("corresp", "sameAs"),
                    "entityLabelXPath", "normalize-space((.//*[@type='reg'])[1])",
                    "entitySourceProperty", "tei",
                    "addUuid", false)),
            Map.entry("export", Map.of(
                    "referenceAttribute", "corresp",
                    "entitySourceProperty", "tei",
                    "ignoreProperties", List.of("xml"))));

    /** every call refers to the stored profile, and none repeats it */
    private static final Map<String, Object> USE_PROFILE = Map.of("profile", "sozinianer");

    @RegisterExtension
    static Neo4jExtension neo4j = Neo4jExtension.builder()
            .withProcedure(ModelProcedures.class)
            .withProcedure(Importer.class)
            .withProcedure(ExporterProcedures.class)
            .withProcedure(atag.profile.ProfileProcedures.class)
            .withFunction(atag.profile.ProfileProcedures.class)
            .withFunction(Utils.class)
            .withConfig(GraphDatabaseSettings.procedure_unrestricted, List.of("atag.*"))
            .withDisabledServer()
            .build();

    private static String xml;
    private static Document source;

    @BeforeAll
    static void loadTheLetter() throws Exception {
        xml = IOUtils.toString(Objects.requireNonNull(LetterExampleTest.class.getResourceAsStream(RESOURCE)),
                StandardCharsets.UTF_8);
        source = parse(xml);
    }

    @Test
    @Order(1)
    void walkthrough(GraphDatabaseService db) throws Exception {
        // 1. the profile of the edition
        db.executeTransactionally("CALL atag.profile.write($profile) YIELD value RETURN value",
                Map.of("profile", PROFILE));

        // 2. the corpus, in one call
        assertEquals(List.of("Corpus | sozinianer | null | null",
                        "Letter | MAIN_ed_kbj_wfw_xmb | letter | cover_letter",
                        "Witness | ed_kbj_wfw_xmb | letter | reference_witness",
                        "Witness | ed_abg_zbc_nlb | letter | null"),
                rows(db, """
                        CALL atag.text.import.corpus($xml, $profile) YIELD node
                        RETURN head(labels(node)) + ' | ' + node.uuid + ' | ' + coalesce(node.type, 'null')
                               + ' | ' + coalesce(node.n, 'null') AS row
                        """, Map.of("xml", xml, "profile", USE_PROFILE)),
                "the profile's documents section describes the hierarchy the import builds");

        assertEquals(List.of("ed_abg_zbc_nlb | MAIN_ed_kbj_wfw_xmb | sozinianer",
                        "ed_kbj_wfw_xmb | MAIN_ed_kbj_wfw_xmb | sozinianer"),
                rows(db, """
                        MATCH (w:Witness)-[:PART_OF]->(l:Letter)-[:PART_OF]->(c:Corpus)
                        RETURN w.uuid + ' | ' + l.uuid + ' | ' + c.uuid AS row ORDER BY row
                        """));

        // 3. what the registers produced
        assertEquals(List.of("Boulliau | person | Boulliau Ismaël | Entity, Person",
                        "Paris | place | Paris | Entity, Place",
                        "ed_vnp_dyc_ydb | item | Komet | Entity, Term"),
                rows(db, """
                        MATCH (e:Entity) WHERE e.uuid IN ['Boulliau', 'Paris', 'ed_vnp_dyc_ydb']
                        RETURN e.uuid + ' | ' + e.tag + ' | ' + e.label + ' | ' + reduce(s = '', l IN labels(e) | s + CASE s WHEN '' THEN '' ELSE ', ' END + l) AS row
                        ORDER BY e.uuid
                        """));
        assertEquals(83, count(db, "MATCH (e:Entity)-[:PART_OF]->(:Corpus) RETURN count(e) AS count"));

        // 4. and the texts
        assertEquals(List.of("ed_abg_zbc_nlb | 23", "ed_kbj_wfw_xmb | 509"),
                rows(db, """
                        MATCH (w:Witness)-[:HAS_ANNOTATION]->(a:Annotation)
                        WITH w.uuid AS witness, count(a) AS annotations
                        RETURN witness + ' | ' + annotations AS row ORDER BY row
                        """));

        // 5. what the graph looks like
        for (Element witness : witnesses(source)) {
            String id = witness.getAttributeNS(XMLConstants.XML_NS_URI, "id");
            String plainText = db.executeTransactionally("MATCH (w:Witness {uuid: $id}) RETURN w.plainText AS text",
                    Map.of("id", id), r -> (String) Iterators.single(r).get("text"));
            assertEquals(body(witness).getTextContent(), plainText, "plain text of " + id);
        }
        assertEquals(List.of("subst | 227 | 234 | 5 | null |  quibus",
                        "del | 227 | 227 | 6 | null | ",
                        "add | 228 | 234 | 6 | superlinear | quibus"),
                rows(db, """
                        MATCH (w:Witness {uuid: 'ed_kbj_wfw_xmb'})-[:HAS_ANNOTATION]->(a:Annotation)
                        WHERE a.startIndex >= 227 AND a.endIndex <= 234
                        RETURN a.tag + ' | ' + a.startIndex + ' | ' + a.endIndex + ' | ' + a.depth + ' | '
                               + coalesce(a.place, 'null') + ' | '
                               + substring(w.plainText, a.startIndex, a.endIndex - a.startIndex) AS row
                        ORDER BY a.startIndex, a.depth
                        """),
                "a deletion without text and a substitution over the same range: only the depth tells them apart");

        assertEquals(List.of("seg | comment | null | 306 | 690 | 5",
                        "note | null | nd14_nmz_m4b | 450 | 690 | 6",
                        "rs | person | null | 500 | 518 | 7",
                        "bibl | ref | null | 611 | 635 | 7",
                        "bibl | ref | null | 649 | 689 | 7"),
                rows(db, """
                        MATCH (w:Witness {uuid: 'ed_kbj_wfw_xmb'})-[:HAS_ANNOTATION]->(n:Annotation {uuid: 'nd14_nmz_m4b'})
                        MATCH (w)-[:HAS_ANNOTATION]->(a:Annotation)
                        WHERE a.startIndex <= n.startIndex AND n.endIndex <= a.endIndex AND a.depth >= n.depth - 1
                           OR n.startIndex <= a.startIndex AND a.endIndex <= n.endIndex AND a.depth = n.depth + 1
                        RETURN a.tag + ' | ' + coalesce(a.type, 'null') + ' | ' + coalesce(a.uuid, 'null') + ' | '
                               + a.startIndex + ' | ' + a.endIndex + ' | ' + a.depth AS row
                        ORDER BY a.startIndex, a.depth
                        """),
                "an editorial note sits inside the text it comments on, with its own references");

        assertEquals(List.of("place | de rerum Polonicarum statu | Polen | Polen | Place",
                        "person | Johann II. Kasimir | ed_pq1_cqm_ndb | Johann II. Kasimir Wasa, Kg. von Polen und Schweden, Gfs. von Litauen | Person",
                        "place | regno | Polen | Polen | Place",
                        "term | Senatoresque | Senator | Senator | Term"),
                rows(db, """
                        MATCH (w:Witness {uuid: 'ed_kbj_wfw_xmb'})-[:HAS_ANNOTATION]->(a:Annotation {tag: 'rs'})-[:REFERS_TO]->(e:Entity)
                        RETURN a.type + ' | ' + substring(w.plainText, a.startIndex, a.endIndex - a.startIndex) + ' | '
                               + e.uuid + ' | ' + e.label + ' | ' + [l IN labels(e) WHERE l <> 'Entity'][0] AS row
                        ORDER BY a.startIndex LIMIT 4
                        """));

        assertEquals(List.of("rs | letter | ed_bjj_5dw_xmb | null | 14",
                        "rs | letter | ed_wwh_p2w_xmb | null | 21 decursi Februarii",
                        "bibl | ref | null | zotero-2065617-MIBGRW3W | Wyczański, Adelsrepublik"),
                rows(db, """
                        MATCH (w:Witness {uuid: 'ed_kbj_wfw_xmb'})-[:HAS_ANNOTATION]->(a:Annotation)
                        WHERE a.corresp IS NOT NULL OR a.sameAs IS NOT NULL
                        RETURN a.tag + ' | ' + a.type + ' | ' + coalesce(a.corresp, 'null') + ' | ' + coalesce(a.sameAs, 'null')
                               + ' | ' + substring(w.plainText, a.startIndex, a.endIndex - a.startIndex) AS row
                        ORDER BY a.startIndex LIMIT 3
                        """),
                "pointers to other letters and to a bibliography stay in the attribute they were written in");

        assertEquals(List.of("532 | 18 | 138 | 38"),
                rows(db, """
                        MATCH (:Witness)-[:HAS_ANNOTATION]->(a:Annotation)
                        WITH count(a) AS annotations, count(a.uuid) AS identified,
                             sum(CASE WHEN exists { (a)-[:REFERS_TO]->() } THEN 1 ELSE 0 END) AS linked,
                             count(a.corresp) + count(a.sameAs) AS kept
                        RETURN annotations + ' | ' + identified + ' | ' + linked + ' | ' + kept AS row
                        """));

        assertEquals(List.of("rs 168, del 80, add 67, subst 27, abbr 24, choice 24, expan 24, p 21, note 18, orig 18, seg 18,"
                        + " ref 9, bibl 8, hi 6, date 4, pb 4, dateline 3, div 2, opener 2, salute 2, closer 1, lb 1, unclear 1"),
                rows(db, """
                        MATCH (:Witness)-[:HAS_ANNOTATION]->(a:Annotation)
                        WITH a.tag AS tag, count(*) AS n ORDER BY n DESC, tag
                        RETURN reduce(s = '', t IN collect(tag + ' ' + n) | s + CASE s WHEN '' THEN '' ELSE ', ' END + t) AS row
                        """));

        assertEquals(List.of("Frankreich | Frankreich | Place", "ed_cph_zjt_32b | Sejm (polnischer Reichstag) | Term"),
                rows(db, """
                        MATCH (e:Entity) WHERE NOT (e)<-[:REFERS_TO]-()
                        RETURN e.uuid + ' | ' + e.label + ' | ' + [l IN labels(e) WHERE l <> 'Entity'][0] AS row
                        ORDER BY e.uuid
                        """),
                "two entities are referenced only from the header's abstract, which travels verbatim");

        // 6. export
        String tei = exportTei(db, "sozinianer", USE_PROFILE);
        assertValidTei(tei);
        Document export = parse(tei);

        // 7. the proof
        XmlAssert exported = XmlAssert.assertThat(tei).withNamespaceContext(NAMESPACES);
        exported.doesNotHaveXPath("//tei:listAnnotation");
        exported.nodesByXPath("//tei:standOff").hasSize(1);
        exported.valueByXPath("/tei:TEI/tei:TEI/@xml:id").isEqualTo("MAIN_ed_kbj_wfw_xmb");
        exported.nodesByXPath("/tei:TEI/tei:TEI/tei:TEI").hasSize(2);

        assertSimilar(child(source.getDocumentElement(), "teiHeader"), child(export.getDocumentElement(), "teiHeader"));
        Element sourceLetter = child(source.getDocumentElement(), "TEI");
        Element exportedLetter = child(export.getDocumentElement(), "TEI");
        assertSimilar(child(sourceLetter, "teiHeader"), child(exportedLetter, "teiHeader"));
        assertEquals(attributes(sourceLetter, true), attributes(exportedLetter, true));

        List<Element> sourceWitnesses = children(sourceLetter, "TEI");
        List<Element> exportedWitnesses = children(exportedLetter, "TEI");
        assertEquals(List.of("ed_kbj_wfw_xmb", "ed_abg_zbc_nlb"), ids(sourceWitnesses));
        assertEquals(List.of("ed_abg_zbc_nlb", "ed_kbj_wfw_xmb"), ids(exportedWitnesses),
                "the graph does not order the parts of a collection: the export orders them by identifier");
        for (Element sourceWitness : sourceWitnesses) {
            String id = sourceWitness.getAttributeNS(XMLConstants.XML_NS_URI, "id");
            Element exportedWitness = exportedWitnesses.get(ids(exportedWitnesses).indexOf(id));
            assertEquals(attributes(sourceWitness, true), attributes(exportedWitness, true));
            assertSimilar(child(sourceWitness, "teiHeader"), child(exportedWitness, "teiHeader"));

            Element sourceBody = body(sourceWitness);
            Element exportedText = child(child(child(exportedWitness, "text"), "body"), "ab");
            assertEquals(sourceBody.getTextContent(), exportedText.getTextContent());
            List<Element> expected = descendants(sourceBody);
            List<Element> actual = descendants(exportedText);
            assertEquals(expected.size(), actual.size(), "elements in the body of " + sourceWitness.getAttribute("xml:id"));
            for (int e = 0; e < expected.size(); e++) {
                Element original = expected.get(e);
                Element written = actual.get(e);
                assertEquals(original.getLocalName(), written.getLocalName(), "element " + e);
                assertEquals(original.getTextContent(), written.getTextContent(), "text of element " + e);
                assertEquals(attributes(original, true), attributes(written, original.hasAttribute("xml:id")),
                        "attributes of element " + e + " <" + original.getLocalName() + ">");
            }
        }

        Map<String, Element> declared = declarations(source.getDocumentElement());
        Map<String, Element> written = declarations(export.getDocumentElement());
        assertEquals(declared.keySet(), written.keySet());
        assertEquals(83, written.size());
        declared.forEach((id, declaration) -> assertSimilar(declaration, written.get(id)));

        // 8. reading the export back
        assertEquals(List.of("ed_abg_zbc_nlb | 23", "ed_kbj_wfw_xmb | 509"),
                rows(db, """
                        UNWIND atag.text.xpath($tei, '/*:TEI/*:TEI/*:TEI') AS witnessXml
                        CREATE (w:Witness {uuid: 'again-' + atag.text.xpath(witnessXml, '/*:TEI/@xml:id')[0], xml: witnessXml})
                        WITH w
                        CALL atag.text.import.tei(w, 'xml', {profile: 'sozinianer', rootElement: 'TEI'}) YIELD node
                        WITH substring(w.uuid, 6) AS witness, count(node) AS annotations
                        RETURN witness + ' | ' + annotations AS row
                        """, Map.of("tei", tei)));
        for (String id : List.of("ed_kbj_wfw_xmb", "ed_abg_zbc_nlb")) {
            assertEquals(rows(db, spans(id)), rows(db, spans("again-" + id)), "annotations of " + id);
            assertEquals(rows(db, references(id)), rows(db, references("again-" + id)), "references of " + id);
        }
    }

    @Test
    @Order(2)
    void exportsStartedBelowTheCorpusAreValidToo(GraphDatabaseService db) {
        assertValidTei(exportTei(db, "MAIN_ed_kbj_wfw_xmb", USE_PROFILE));
        assertValidTei(exportTei(db, "ed_abg_zbc_nlb", USE_PROFILE));
    }

    // ---- the graph

    private long count(GraphDatabaseService db, String query) {
        return count(db, query, Map.of());
    }

    private long count(GraphDatabaseService db, String query, Map<String, Object> parameters) {
        return db.executeTransactionally(query, parameters, r -> (Long) Iterators.single(r).get("count"));
    }

    private List<String> rows(GraphDatabaseService db, String query) {
        return rows(db, query, Map.of());
    }

    private List<String> rows(GraphDatabaseService db, String query, Map<String, Object> parameters) {
        return db.executeTransactionally(query, parameters,
                r -> Iterators.asList(r).stream().map(row -> (String) row.get("row")).toList());
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
                MATCH (:Witness {uuid: '%s'})-[:HAS_ANNOTATION]->(a:Annotation)
                RETURN a.tag + ' | ' + a.startIndex + ' | ' + a.endIndex + ' | ' + coalesce(a.type, '') AS row
                ORDER BY row
                """.formatted(uuid);
    }

    private static String references(String uuid) {
        return """
                MATCH (:Witness {uuid: '%s'})-[:HAS_ANNOTATION]->(a:Annotation)-[:REFERS_TO]->(e:Entity)
                RETURN a.startIndex + ' | ' + a.endIndex + ' | ' + e.uuid AS row
                ORDER BY row
                """.formatted(uuid);
    }

    // ---- the documents

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<Element> children(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && localName.equals(element.getLocalName())) {
                result.add(element);
            }
        }
        return result;
    }

    private static Element child(Element parent, String localName) {
        List<Element> children = children(parent, localName);
        assertEquals(1, children.size(), "<" + localName + "> in <" + parent.getLocalName() + ">");
        return children.get(0);
    }

    private static List<String> ids(List<Element> elements) {
        return elements.stream().map(element -> element.getAttributeNS(XMLConstants.XML_NS_URI, "id")).toList();
    }

    private static List<Element> witnesses(Document document) {
        return children(child(document.getDocumentElement(), "TEI"), "TEI");
    }

    private static Element body(Element witness) {
        return child(child(witness, "text"), "body");
    }

    /** every element below the given one, in document order */
    private static List<Element> descendants(Element root) {
        NodeList all = root.getElementsByTagNameNS("*", "*");
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < all.getLength(); i++) {
            result.add((Element) all.item(i));
        }
        return result;
    }

    /** the entity declarations of a document by identifier: persons, places and terms */
    private static Map<String, Element> declarations(Element root) {
        Map<String, Element> result = new TreeMap<>();
        for (Element element : descendants(child(root, "standOff"))) {
            if (Set.of("person", "place", "item").contains(element.getLocalName())
                    && element.hasAttributeNS(XMLConstants.XML_NS_URI, "id")) {
                result.put(element.getAttributeNS(XMLConstants.XML_NS_URI, "id"), element);
            }
        }
        return result;
    }

    /**
     * The attributes of an element as the comparison sees them: namespace declarations
     * are not content, a pointer is the same with or without its leading {@code #}, and
     * an identifier the export had to invent is not held against it.
     */
    private static Map<String, String> attributes(Element element, boolean withId) {
        Map<String, String> result = new TreeMap<>();
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String name = attribute.getNodeName();
            if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) {
                continue;
            }
            if (name.equals("xml:id") && !withId) {
                continue;
            }
            String value = attribute.getNodeValue();
            if (REFERENCE_ATTRIBUTES.contains(name)) {
                value = Arrays.stream(value.trim().split("\\s+"))
                        .map(pointer -> pointer.startsWith("#") ? pointer.substring(1) : pointer)
                        .collect(Collectors.joining(" "));
            }
            result.put(name, value);
        }
        return result;
    }

    private static void assertSimilar(Element expected, Element actual) {
        XmlAssert.assertThat(actual).and(expected).ignoreWhitespace().areSimilar();
    }

    private void assertValidTei(String tei) {
        XmlAssert.assertThat(tei).isValidAgainst(
                new StreamSource(LetterExampleTest.class.getResourceAsStream("/tei-atag-export.xsd")));
    }
}
