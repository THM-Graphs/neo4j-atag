package atag.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.harness.Neo4jBuilders;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DeterministicUUIDTest {
    private static final String LABEL = "ad Fontem Capellae";
    private static final String LABEL_UUID = "4446ef3e-d079-3fa0-8192-7f44932accc2";

    private final DeterministicUUID function = new DeterministicUUID();

    @ParameterizedTest
    @CsvSource({
            LABEL + ", " + LABEL_UUID,
            "'', d41d8cd9-8f00-3204-a980-0998ecf8427e",
            "Grüße aus Köln, 2a21eb25-aeed-3377-9432-adca96b0d031"
    })
    void returnsExpectedUuid(String input, String expectedUuid) {
        assertEquals(expectedUuid, function.deterministicUUID(input));
    }

    @Test
    void returnsSameUuidForRepeatedInput() {
        assertEquals(function.deterministicUUID(LABEL), function.deterministicUUID(LABEL));
    }

    @Test
    void includesLetterCaseInUuid() {
        assertNotEquals(function.deterministicUUID(LABEL), function.deterministicUUID("ad Fontem capellae"));
    }

    @Test
    void storesUuidInCypherSet(@TempDir Path directory) {
        try (var neo4j = Neo4jBuilders.newInProcessBuilder(directory)
                .withDisabledServer()
                .withConfig(BoltConnector.enabled, false)
                .withFunction(DeterministicUUID.class)
                .withFixture("CREATE (:Entity {label: 'ad Fontem Capellae'})")
                .build()) {
            var db = neo4j.defaultDatabaseService();
            db.executeTransactionally("""
                    MATCH (n:Entity) WHERE n.label = 'ad Fontem Capellae'
                    SET n.uuid = atag.deterministicUUID(n.label)
                    RETURN n
                    """);

            try (var tx = db.beginTx();
                 var rows = tx.execute("MATCH (n:Entity {label: 'ad Fontem Capellae'}) RETURN n.uuid AS uuid")) {
                assertEquals(LABEL_UUID, rows.next().get("uuid"));
            }
        }
    }
}
