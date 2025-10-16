package atag.importer;

import apoc.util.collection.Iterators;
import atag.export.ExporterProcedures;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImportProceduresTest {

    @RegisterExtension
    static Neo4jExtension neo4j = Neo4jExtension.builder()
            .withProcedure(ExporterProcedures.class)
            .withProcedure(ImportProcedures.class)
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

