package atag.doc;

import atag.export.ExporterProcedures;
import atag.importer.ImportProcedures;
import atag.profile.ProfileProcedures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Label;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.spatial.Point;
import org.neo4j.harness.junit.extension.Neo4jExtension;
import org.neo4j.internal.helpers.collection.Iterators;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The walkthrough of <em>src/site/markdown/worked-example-regesta.md</em>, executed: a
 * register of regests is sampled - one regest per department - exported as JGF files,
 * and imported into an empty database, where the result is compared with the original.
 * The documented statements and results are asserted here, so the documentation cannot
 * drift away from what the procedures do.
 */
class RegestaExampleTest {

    /**
     * The profile of the register: the project model and the traversal scope that
     * {@code atag.export.jgf.fromNode} would use. Stored once, referred to by name.
     */
    private static final Map<String, Object> PROFILE = Map.of(
            "name", "regesta",
            "model", Map.of(
                    "collection", List.of("Collection"),
                    "content", List.of("Content"),
                    "annotation", List.of("Annotation"),
                    "entity", List.of("Entity")),
            "export", Map.of(
                    "followIncoming", List.of("PART_OF"),
                    "followOutgoing", List.of("HAS_ANNOTATION", "REFERS_TO"),
                    "includeCharacterChain", false));

    private static final String FIXTURE = """
            CREATE (d1:Collection:Department {uuid: 'dep-1', label: 'RI01 - Karolinger', type: 'department'})
            CREATE (v1:Collection:Volume {uuid: 'vol-1', label: '1 - Karolinger 751-918', type: 'volume'})-[:PART_OF]->(d1)
            CREATE (r1:Collection:Regesta {uuid: 'reg-1', label: 'Karl III - RI I n. 1707 885 iuni 21, Stirpiaco villa',
                    sorting: '001_0000001707', identifier: 'RI I n. 1707', isoStartDate: '0885-06-21',
                    laufendenummer: 3856, latitude: '48.76667', hasImage: true, volume: ['1 - Karolinger 751-918'],
                    nLatLong: point({latitude: 48.76667, longitude: 4.81667})})-[:PART_OF]->(v1)
            CREATE (r2:Collection:Regesta {uuid: 'reg-2', label: 'Karl III - RI I n. 1708 885 iulii 2',
                    sorting: '001_0000001708', identifier: 'RI I n. 1708', isoStartDate: '0885-07-02'})-[:PART_OF]->(v1)
            CREATE (s1:Content:Text:Summary {uuid: 'txt-1', label: 'Summary',
                    text: 'Karl III bestätigt dem Kloster Lorsch seinen Besitz.',
                    htmlText: '<p>Karl III bestätigt dem Kloster Lorsch seinen Besitz.</p>'})-[:PART_OF]->(r1)
            CREATE (i1:Content:Text:Identifier {uuid: 'txt-2', label: 'Identifier', text: 'RI I n. 1707'})-[:PART_OF]->(r1)
            CREATE (t2:Content:Text:Title {uuid: 'txt-3', label: 'Title', text: 'Karl III'})-[:PART_OF]->(r2)
            CREATE (i1)-[:HAS_ANNOTATION]->(:Annotation:Next {uuid: 'ann-1', label: 'next', type: 'next'})-[:REFERS_TO]->(s1)
            CREATE (r1)-[:HAS_ANNOTATION]->(:Annotation:Next {uuid: 'ann-2', label: 'next', type: 'next'})-[:REFERS_TO]->(r2)
            CREATE (pl:Entity:Place {uuid: 'ent-1', label: 'Étrépy'})
            CREATE (r1)-[:HAS_ANNOTATION]->(:Annotation:RegestaAnnotation {uuid: 'ann-3', label: 'placeOfIssue',
                    type: 'ri:placeOfIssue', original: 'Stirpiaco villa'})-[:REFERS_TO]->(pl)
            CREATE (pe:Entity:Person {uuid: 'ent-2', label: 'Karl III'})
            CREATE (pe)-[:HAS_ANNOTATION]->(:Annotation {uuid: 'ann-4', type: 'mention', startIndex: 0, endIndex: 8})-[:REFERS_TO]->(s1)
            CREATE (d2:Collection:Department {uuid: 'dep-2', label: 'RI02 - Sächsisches Haus', type: 'department'})
            CREATE (v2:Collection:Volume {uuid: 'vol-2', label: '2 - Heinrich I.', type: 'volume'})-[:PART_OF]->(d2)
            CREATE (r3:Collection:Regesta {uuid: 'reg-3', label: 'Heinrich I. - RI II n. 1', sorting: '002_0000000001',
                    identifier: 'RI II n. 1', isoStartDate: '0919-05-24', laufendenummer: 1,
                    nLatLong: point({latitude: 50.0, longitude: 10.0})})-[:PART_OF]->(v2)
            CREATE (:Content:Text:Summary {uuid: 'txt-4', label: 'Summary', text: 'Heinrich I. wird zum König gewählt.'})-[:PART_OF]->(r3)
            CREATE (r3)-[:HAS_ANNOTATION]->(:Annotation:RegestaAnnotation {uuid: 'ann-5', label: 'placeOfIssue',
                    type: 'ri:placeOfIssue', original: 'Fritzlar'})-[:REFERS_TO]->(pl)
            """;

    /** one regest per department, with everything that belongs to it */
    private static final String COLLECT = """
            MATCH (d:Department)
            CALL (d) {
              MATCH (d)<-[pv:PART_OF]-(v:Volume)<-[pr:PART_OF]-(r:Regesta)
              RETURN v, r, pv, pr ORDER BY r.sorting LIMIT 1
            }
            CALL (r) {
              OPTIONAL MATCH (c:Content)-[pc:PART_OF]->(r)
              RETURN collect(c) AS contents, collect(pc) AS contentRels
            }
            WITH d, v, r, pv, pr, contents, contentRels, [r] + contents AS texts
            CALL (texts) {
              UNWIND texts AS t
              OPTIONAL MATCH (t)-[h:HAS_ANNOTATION]->(a:Annotation)
              RETURN collect(DISTINCT a) AS ownAnn, collect(DISTINCT h) AS ownRels
            }
            CALL (texts) {
              UNWIND texts AS t
              OPTIONAL MATCH (e:Entity)-[h:HAS_ANNOTATION]->(a:Annotation)-[:REFERS_TO]->(t)
              RETURN collect(DISTINCT e) AS ents, collect(DISTINCT a) AS entAnn, collect(DISTINCT h) AS entRels
            }
            WITH d, v, r, pv, pr, contents, contentRels, ownAnn, ownRels, ents, entAnn, entRels,
                 ownAnn + entAnn AS anns
            CALL (anns) {
              UNWIND anns AS a
              OPTIONAL MATCH (a)-[rt:REFERS_TO]->(t)
              RETURN collect(DISTINCT t) AS targets, collect(DISTINCT rt) AS referRels
            }
            WITH d, r,
                 [x IN [d, v, r] + contents + ownAnn + entAnn + ents + targets | x] AS allNodes,
                 [x IN [pv, pr] + contentRels + ownRels + entRels + referRels | x] AS allRels
            CALL (allNodes) { UNWIND allNodes AS n RETURN collect(DISTINCT n) AS nodes }
            CALL (allRels) { UNWIND allRels AS e RETURN collect(DISTINCT e) AS rels }
            """;

    private static final String EXPORT = COLLECT + """
            CALL atag.export.jgf.list(nodes, rels, {profile: 'regesta', fileName: 'regest_' + d.uuid + '.json'})
            YIELD value
            RETURN d.uuid + ' | ' + r.uuid + ' | ' + size(nodes) + ' | ' + size(rels) AS row, value
            ORDER BY row
            """;

    private static final String MERGE_CONFIG = """
            {propertyKey: 'uuid',
             labels: ['Department', 'Volume', 'Regesta', 'Content', 'Annotation', 'Entity'],
             overwrite: false}""";

    @RegisterExtension
    static Neo4jExtension neo4j = Neo4jExtension.builder()
            .withProcedure(ExporterProcedures.class)
            .withFunction(ExporterProcedures.class)
            .withProcedure(ImportProcedures.class)
            .withProcedure(ProfileProcedures.class)
            .withFunction(ProfileProcedures.class)
            .withConfig(GraphDatabaseSettings.procedure_unrestricted, List.of("atag.*"))
            .withDisabledServer()
            .build();

    @Test
    void walkthrough(GraphDatabaseService db) {
        // 1. the profile
        db.executeTransactionally("CALL atag.profile.write($profile) YIELD value RETURN value",
                Map.of("profile", PROFILE));

        // 2. the register
        db.executeTransactionally(FIXTURE);
        assertEquals(List.of("Annotation 5", "Collection 7", "Content 4", "Department 2", "Entity 2", "Regesta 3", "Volume 2"),
                rows(db, """
                        MATCH (n) UNWIND [l IN labels(n) WHERE l IN ['Annotation', 'Collection', 'Content', 'Department', 'Entity', 'Regesta', 'Volume']] AS label
                        WITH label, count(*) AS n RETURN label + ' ' + n AS row ORDER BY row
                        """));
        Snapshot before = snapshot(db);

        // 3. why the traversal of the profile is not used for this sample
        List<String> traversed = db.executeTransactionally("""
                MATCH (r:Regesta {uuid: 'reg-1'})
                CALL atag.export.jgf.fromNode(r, {profile: 'regesta'}) YIELD value
                UNWIND keys(value.graph.nodes) AS k
                RETURN value.graph.nodes[k].metadata.uuid AS uuid ORDER BY uuid
                """, Map.of(), r -> Iterators.asList(r).stream().map(row -> (String) row.get("uuid")).toList());
        assertTrue(traversed.contains("txt-3"), "REFERS_TO leads into the neighbouring regest and its texts");
        assertFalse(traversed.contains("vol-1"), "PART_OF is followed downwards only: volume and department are missing");

        // 4. what the sample consists of
        assertEquals(List.of("dep-1 | reg-1 | 12 | 12", "dep-2 | reg-3 | 6 | 5"), rows(db, COLLECT + """
                RETURN d.uuid + ' | ' + r.uuid + ' | ' + size(nodes) + ' | ' + size(rels) AS row ORDER BY row
                """));

        // 5. export
        List<Map<String, Object>> exported = db.executeTransactionally(EXPORT, Map.of(), Iterators::asList);
        assertEquals(List.of("dep-1 | reg-1 | 12 | 12", "dep-2 | reg-3 | 6 | 5"),
                exported.stream().map(row -> (String) row.get("row")).toList());
        assertTrue(exported.stream().allMatch(
                row -> ((String) row.get("value")).matches("\\d+ bytes written to regest_dep-[12]\\.json")));

        // 6. import into an empty database
        db.executeTransactionally("MATCH (n) DETACH DELETE n");
        assertEquals(0L, count(db, "MATCH (n) RETURN count(n) AS count"));

        assertEquals(List.of("regest_dep-1.json | 12 | 12", "regest_dep-2.json | 5 | 5"),
                List.of(importFile(db, "regest_dep-1.json"), importFile(db, "regest_dep-2.json")),
                "the place that both files contain is the same node");
        assertEquals(List.of("regest_dep-1.json | 0 | 0", "regest_dep-2.json | 0 | 0"),
                List.of(importFile(db, "regest_dep-1.json"), importFile(db, "regest_dep-2.json")),
                "importing again changes nothing");

        // 7. the comparison
        Snapshot after = snapshot(db);
        Set<String> notExported = new TreeSet<>(before.nodes.keySet());
        notExported.removeAll(after.nodes.keySet());
        assertEquals(Set.of("txt-3"), notExported, "the text of the neighbouring regest was not part of the sample");
        assertEquals(17, after.nodes.size());
        assertEquals(17, after.relationships.size());

        Set<String> lostRelationships = new TreeSet<>(before.relationships);
        lostRelationships.removeAll(after.relationships);
        assertEquals(Set.of("reg-2 -PART_OF-> vol-1", "txt-3 -PART_OF-> reg-2"), lostRelationships,
                "the neighbouring regest came along as a stub, without its own relationships");

        List<String> differences = new java.util.ArrayList<>();
        after.nodes.forEach((uuid, properties) -> properties.forEach((key, value) -> {
            String original = before.nodes.get(uuid).get(key);
            if (!value.equals(original)) {
                differences.add(uuid + "." + key + ": " + original + " -> " + value);
            }
        }));
        assertEquals(List.of("reg-1.isoStartDate: String:0885-06-21 -> Date:0885-06-21",
                        "reg-2.isoStartDate: String:0885-07-02 -> Date:0885-07-02",
                        "reg-3.isoStartDate: String:0919-05-24 -> Date:0919-05-24"),
                differences.stream().sorted().toList(),
                "labels, numbers, arrays and points survive; a string that is an ISO date does not");

        assertEquals(List.of("Point:4326[4.81667, 48.76667]", "String[]:[1 - Karolinger 751-918]", "Boolean:true", "Integer:3856"),
                List.of(after.nodes.get("reg-1").get("nLatLong"), after.nodes.get("reg-1").get("volume"),
                        after.nodes.get("reg-1").get("hasImage"), after.nodes.get("reg-1").get("laufendenummer")));
    }

    // ---- the graph

    private String importFile(GraphDatabaseService db, String fileName) {
        return db.executeTransactionally("CALL atag.import.jgfFile($file, " + MERGE_CONFIG + ") YIELD nodeCount, relationshipCount RETURN *",
                Map.of("file", fileName),
                r -> {
                    Map<String, Object> row = Iterators.single(r);
                    return fileName + " | " + row.get("nodeCount") + " | " + row.get("relationshipCount");
                });
    }

    private long count(GraphDatabaseService db, String query) {
        return db.executeTransactionally(query, Map.of(), r -> (Long) Iterators.single(r).get("count"));
    }

    private List<String> rows(GraphDatabaseService db, String query) {
        return db.executeTransactionally(query, Map.of(),
                r -> Iterators.asList(r).stream().map(row -> (String) row.get("row")).toList());
    }

    /** the nodes by uuid with their labels and typed property values, and the relationships by uuid; the stored profile has no uuid and is not part of the data */
    private record Snapshot(Map<String, Map<String, String>> nodes, Set<String> relationships) {
    }

    private Snapshot snapshot(GraphDatabaseService db) {
        Map<String, Map<String, String>> nodes = new TreeMap<>();
        Set<String> relationships = new TreeSet<>();
        try (var tx = db.beginTx()) {
            tx.getAllNodes().stream().filter(node -> node.hasProperty("uuid")).forEach(node -> {
                Map<String, String> properties = new TreeMap<>();
                node.getAllProperties().forEach((key, value) -> properties.put(key, render(value)));
                properties.put("@labels", node.getLabels().iterator().hasNext()
                        ? new TreeSet<>(Iterators.asList(node.getLabels().iterator()).stream().map(Label::name).toList()).toString()
                        : "[]");
                nodes.put((String) node.getProperty("uuid"), properties);
            });
            tx.getAllRelationships().stream()
                    .filter(relationship -> relationship.getStartNode().hasProperty("uuid") && relationship.getEndNode().hasProperty("uuid"))
                    .forEach(relationship -> relationships.add(describe(relationship)));
        }
        return new Snapshot(nodes, relationships);
    }

    private static String describe(Relationship relationship) {
        Node start = relationship.getStartNode();
        Node end = relationship.getEndNode();
        return start.getProperty("uuid") + " -" + relationship.getType().name() + "-> " + end.getProperty("uuid");
    }

    private static String render(Object value) {
        if (value instanceof String s) {
            return "String:" + s;
        } else if (value instanceof Long || value instanceof Integer) {
            return "Integer:" + value;
        } else if (value instanceof Double d) {
            return "Double:" + d;
        } else if (value instanceof Boolean b) {
            return "Boolean:" + b;
        } else if (value instanceof LocalDate d) {
            return "Date:" + d;
        } else if (value instanceof Point p) {
            return "Point:" + p.getCRS().getCode() + Arrays.toString(p.getCoordinate().getCoordinate());
        } else if (value instanceof String[] a) {
            return "String[]:" + Arrays.toString(a);
        } else if (value instanceof long[] a) {
            return "long[]:" + Arrays.toString(a);
        } else if (value instanceof double[] a) {
            return "double[]:" + Arrays.toString(a);
        } else if (value instanceof boolean[] a) {
            return "boolean[]:" + Arrays.toString(a);
        }
        throw new IllegalArgumentException(value.getClass().getName());
    }
}
