package atag.importer;

import apoc.util.collection.Iterators;
import atag.export.ExporterProcedures;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.harness.junit.extension.Neo4jExtension;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ImportProceduresTest {

    @RegisterExtension
    static Neo4jExtension neo4j = Neo4jExtension.builder()
            .withProcedure(ExporterProcedures.class)
            .withProcedure(ImportProcedures.class)
            .withFunction(ExporterProcedures.class)
            .withConfig(GraphDatabaseSettings.procedure_unrestricted, List.of("atag.*"))
            .withDisabledServer()
            .build();

    @BeforeEach
    void setup(GraphDatabaseService db) {
        db.executeTransactionally("MATCH (n) DETACH DELETE n");
    }

    @Test
    void testImportFromJgfFile(GraphDatabaseService db) {
        copyJgfFileToImportFolder((GraphDatabaseAPI) db);
        db.executeTransactionally("""
            CALL atag.import.jgfFile('test_export.json')
            YIELD nodeCount, relationshipCount
            RETURN *
            """, Map.of(), result -> {
            Map<String, Object> row = Iterators.single(result);
            assertEquals(2L, row.get("nodeCount"));
            assertEquals(1L, row.get("relationshipCount"));
            return null;
        });

        // Verify imported data
        db.executeTransactionally("""
            MATCH (a:Person {name: 'Alice'})-[r:KNOWS]->(b:Person {name: 'Bob'})
            RETURN count(r) as count
            """, Map.of(),  result -> {
            Map<String, Object> row = Iterators.single(result);
            assertEquals(1L, row.get("count"));
            return null;
        });
    }

    @Test
    void testImportUsingMerge(GraphDatabaseService db) {
        copyJgfFileToImportFolder((GraphDatabaseAPI) db);

        db.executeTransactionally("""
            CALL atag.import.jgfFile('test_export.json', {propertyKey: 'uuid', labels: ['Person']})
            YIELD nodeCount, relationshipCount
            RETURN *
            """, Map.of(), result -> {
            Map<String, Object> row = Iterators.single(result);
            assertEquals(2L, row.get("nodeCount"));
            assertEquals(1L, row.get("relationshipCount"));
            return null;
        });

        // rerun import - expecting no change
        db.executeTransactionally("""
            CALL atag.import.jgfFile('test_export.json', {propertyKey: 'uuid', labels: ['Person']})
            YIELD nodeCount, relationshipCount
            RETURN *
            """, Map.of(), result -> {
            Map<String, Object> row = Iterators.single(result);
            assertEquals(0L, row.get("nodeCount"));
            assertEquals(0L, row.get("relationshipCount"));
            return null;
        });

        // Verify imported data
        db.executeTransactionally("""
            MATCH (a:Person {name: 'Alice'})-[r:KNOWS]->(b:Person {name: 'Bob'})
            RETURN count(r) as count
            """, Map.of(),  result -> {
            Map<String, Object> row = Iterators.single(result);
            assertEquals(1L, row.get("count"));
            return null;
        });

    }

    @Test
    void testRoundTripKeepsPointsAndTypedArrays(GraphDatabaseService db) throws JsonProcessingException {
        db.executeTransactionally("""
            CREATE (:Regesta {uuid: 'r1', nLatLong: point({latitude: 48.76667, longitude: 4.81667}),
                              years: [885, 886], weights: [0.5, 1.5], flags: [true, false], tags: ['a', 'b']})
            """);
        Object exported = db.executeTransactionally("""
            MATCH (n:Regesta) RETURN atag.export.jgf([n], []) AS jgf
            """, Map.of(), result -> Iterators.single(result).get("jgf"));
        String jgf = new ObjectMapper().writeValueAsString(exported);
        db.executeTransactionally("MATCH (n) DETACH DELETE n");

        db.executeTransactionally("CALL atag.import.jgf($json) YIELD nodeCount RETURN nodeCount",
                Map.of("json", jgf), result -> Iterators.single(result));

        db.executeTransactionally("""
            MATCH (n:Regesta {uuid: 'r1'})
            RETURN n.nLatLong.latitude AS lat, n.nLatLong.longitude AS lon, n.nLatLong.srid AS srid,
                   n.years AS years, n.weights AS weights, n.flags AS flags, n.tags AS tags
            """, Map.of(), result -> {
            Map<String, Object> row = Iterators.single(result);
            assertEquals(48.76667, (double) row.get("lat"), 1e-9);
            assertEquals(4.81667, (double) row.get("lon"), 1e-9);
            assertEquals(4326, ((Number) row.get("srid")).intValue());
            assertArrayEquals(new long[]{885, 886}, (long[]) row.get("years"));
            assertArrayEquals(new double[]{0.5, 1.5}, (double[]) row.get("weights"));
            assertArrayEquals(new boolean[]{true, false}, (boolean[]) row.get("flags"));
            assertArrayEquals(new String[]{"a", "b"}, (String[]) row.get("tags"));
            return null;
        });
    }

    private void copyJgfFileToImportFolder(GraphDatabaseAPI db) {
        try {
            GraphDatabaseAPI api = db;
            Config config = api.getDependencyResolver().resolveDependency(Config.class);
            Path path = config.get(GraphDatabaseSettings.load_csv_file_url_root);

            Path sourceFile = Path.of(getClass().getResource("/jgf_sample.json").toURI());
            Path targetFile = path.resolve("test_export.json");
            Files.copy(sourceFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException|URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}

