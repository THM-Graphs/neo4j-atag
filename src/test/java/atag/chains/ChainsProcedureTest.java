package atag.chains;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.neo4j.graphdb.*;
import org.neo4j.harness.junit.extension.Neo4jExtension;
import org.neo4j.internal.helpers.collection.Iterables;
import org.neo4j.internal.helpers.collection.Iterators;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ChainsProcedureTest {

    static Logger logger = Logger.getLogger(ChainsProcedureTest.class.getName());

    @RegisterExtension
    static Neo4jExtension neo4j = Neo4jExtension.builder()
            .withDisabledServer()
            .withProcedure(ChainsProcedure.class)
            /*.withFixture(db -> {
                try {

                    InputStream importFile = RouteProcedureNewModelTest.class.getClassLoader().getResourceAsStream("newmodel.cypher");
                    String allCmds = IOUtils.toString(importFile, Charset.defaultCharset());
                    for (String cmd: allCmds.split(";")) {
                        cmd = cmd.trim();
                        if (!cmd.isEmpty()) {
                            db.executeTransactionally(cmd);
                        }
                    }
                    return null;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            })*/
            .build();

    static Map<String, Object> configuration = Map.of(
            "textLabel", "Text",
            "elementLabel", "Token",
            "relationshipType", "NEXT_TOKEN"
    );

    public static Stream<Arguments> testTokenChain() {
        return Stream.of(
                Arguments.of("here's a comma, in this text", 13),
                Arguments.of("Ümlaute für den Spaß!", 7)
        );
    }

    private static Consumer<GraphDatabaseService> assertTokenCount(long expectedCount) {
        return db -> db.executeTransactionally("MATCH (t:Token) RETURN count(t) as count", Collections.emptyMap(), result -> {
            assertEquals(expectedCount, (long) Iterators.single(result).get("count"));
            return true;
        });
    }

    public static Stream<Arguments> testUpdateInitial() {

        return Stream.of(
                Arguments.of(Collections.emptyList(), 0, 0, assertTokenCount(0)),
                Arguments.of(List.of(Map.of("uuid", uuid())), 0, 1, assertTokenCount(1)),
                Arguments.of(List.of(Map.of("uuid", uuid()), Map.of("uuid", uuid())), 1, 2, assertTokenCount(2))
        );
    }

    private static String uuid() {
        return UUID.randomUUID().toString();
    }

    private static void validatePathLength(GraphDatabaseService db, String query, Map<String, Object> params,
                                           int expectedPathLength) {
        validatePathLength(db, query, params, expectedPathLength, (Path entities) -> {});
    }

    private static void validatePathLength(GraphDatabaseService db, String query, Map<String, Object> params,
                                           int expectedPathLength, Consumer<Path> assertion) {
        db.executeTransactionally(query,
                params,
                result -> {
                    Path path = (Path) Iterators.single(result).get("path");
                    if (logger.isLoggable(Level.INFO)) {
                        StringBuilder sb = new StringBuilder();
                        for (Node node: path.nodes()) {
                            sb.append("(").append(node.getProperty("uuid")).append(")");
                            if (!node.equals(path.endNode())) {
                               sb.append("->");
                            }

                        }
                        logger.info("path is %s".formatted(sb.toString()));
                    }
                    assertEquals(expectedPathLength, path.length(), "failed path length assertion for %s".formatted(query));
                    assertion.accept(path);
                    return true;
                });
    }

    @AfterEach
    void cleanup(GraphDatabaseService db) {
        db.executeTransactionally("MATCH (n) DETACH DELETE n");
    }

    @Test
    public void testChain(GraphDatabaseService db) {

        String text = "what a nice text";
        db.executeTransactionally("CALL atag.chains.chain($text, '((?<=\\\\s)|(?=\\\\s))', 'Character', 'NEXT_CHARACTER', false) YIELD path RETURN path", Map.of( "text", text), result -> {
//        db.executeTransactionally("CALL atag.chains.chain($text, '', 'Character', 'NEXT_CHARACTER', true) YIELD path RETURN path", Map.of( "text", text), result -> {

            Map<String, Object> map = Iterators.single(result);
            Path path = (Path) map.get("path");

            assertEquals(6, path.length());
            List<Node> nodes = Iterables.asList(path.nodes());
            assertEquals("what", nodes.get(0).getProperty("text"));
            assertEquals("text", nodes.get(nodes.size()-1).getProperty("text"));
            return true;
        });
    }

    @Test
    public void testCharacterChain(GraphDatabaseService db) {
        String text = "what a nice text";
        db.executeTransactionally("CALL atag.chains.characterChain($text) YIELD path RETURN path", Map.of( "text", text), result -> {
            Map<String, Object> map = Iterators.single(result);
            Path path = (Path) map.get("path");

            assertEquals(15, path.length());
            List<Node> nodes = Iterables.asList(path.nodes());

            for (int index=0; index<text.length(); index++) {
                assertEquals(Character.toString(text.charAt(index)), nodes.get(index).getProperty("text"));
                assertTrue(nodes.get(index).hasProperty(ChainsProcedure.PROPERTY_START_INDEX));
                assertTrue(nodes.get(index).hasProperty(ChainsProcedure.PROPERTY_START_INDEX));
            }
            return true;
        });
    }

    @Test
    public void testCharacterChainWithIndex(GraphDatabaseService db) {
        String text = "what a nice text";
        db.executeTransactionally("CALL atag.chains.characterChain($text, true) YIELD path RETURN path", Map.of( "text", text), result -> {
            Map<String, Object> map = Iterators.single(result);
            Path path = (Path) map.get("path");

            assertEquals(15, path.length());
            List<Node> nodes = Iterables.asList(path.nodes());
            assertEquals("w", nodes.get(0).getProperty("text"));
            assertEquals("t", nodes.get(nodes.size()-1).getProperty("text"));
            for (int i = 0; i < nodes.size(); i++) {
                assertEquals(i, nodes.get(i).getProperty(ChainsProcedure.PROPERTY_START_INDEX));
                assertEquals(i, nodes.get(i).getProperty(ChainsProcedure.PROPERTY_END_INDEX));
            }
            return true;
        });
    }

    @Test
    public void testFullChain(GraphDatabaseService db) {

        String text = "what a nice text";
        db.executeTransactionally("CREATE (s:Text{text:$text}) WITH s CALL atag.chains.fullChain(s, 'text') RETURN s", Map.of( "text", text), result -> {

            Map<String, Object> map = Iterators.single(result);
            Node startNode = (Node) map.get("s");

            assertNotNull(startNode.getSingleRelationship(ChainsProcedure.REL_NEXT_TOKEN, Direction.OUTGOING));
            assertNotNull(startNode.getSingleRelationship(ChainsProcedure.REL_NEXT_CHARACTER, Direction.OUTGOING));

            return true;
        });
    }

    @ParameterizedTest
    @MethodSource
    public void testTokenChain(String text, int expected, GraphDatabaseService db) {
        int pathLength = db.executeTransactionally("CALL atag.chains.tokenChain($text) YIELD path RETURN path",
                Map.of("text", text),
                result -> ((Path) Iterators.single(result).get("path")).length()
        );
        assertEquals(expected, pathLength);
    }

    public static Stream<Arguments> testChainUpdate() {

        return Stream.of(
                Arguments.of("removal of unbounded chain", 0, null, null, Collections.emptyList(), 0, 0, 0, null),
                Arguments.of("removal of unbounded chain", 1, null, null, Collections.emptyList(), 0, 0, 0, null),
                Arguments.of("removal of unbounded chain", 10, null, null, Collections.emptyList(), 0, 0, 0, null),
                Arguments.of("removal of bounded chain", 10, "0", "9", Collections.emptyList(), 0, 2, 2, null),
                Arguments.of("invalid uuid", 0, "invalid1", "invalid2", Collections.emptyList(), 0, 0, 0, IllegalArgumentException.class),

                Arguments.of("add to empty chain", 0, null, null, List.of(Map.of("uuid", "new0")), 0, 1, 1, null),
                Arguments.of("insert one node into existing chain", 5, "2", "3", List.of(Map.of("uuid", "new0")), 0, 6, 6, null),
                Arguments.of("insert multiple into existing chain", 5, "2", "3", List.of(Map.of("uuid", "new0"), Map.of("uuid", "new1")), 1, 7, 7, null),
                Arguments.of("before/afterUuid must be different", 5, "3", "3", List.of(Map.of("uuid", "new0")), 0, 6, 5, IllegalArgumentException.class),
                Arguments.of("replace one node with two new ones", 5, "1", "3", List.of(Map.of("uuid", "new0"), Map.of("uuid", "new1")), 1, 6, 6, null),

                Arguments.of("delete from start of chain", 5, null, "3", Collections.emptyList(), 0, 2, 2, null),
                Arguments.of("replace from start of chain", 5, null, "3", List.of(Map.of("uuid", "new0"), Map.of("uuid", "new1")), 1, 4, 4, null ),

                Arguments.of("delete to end of chain", 5, "3", null, Collections.emptyList(), 0, 4, 4, null),
                Arguments.of("replace to end of chain", 5, "3", null, List.of(Map.of("uuid", "new0"), Map.of("uuid", "new1")), 1, 6, 6, null)
        );
    }

    @ParameterizedTest(name = "{index}: {0} with fixture length {1}, boundaries ({2}, {3}")
    @MethodSource
    public void testChainUpdate(String name, int fixtureLength, String uuidBefore, String uuidAfter, List<Map<String,Object>> characterList,
                                     int expectedLength, int expectedQueryLength, int expectedNumberOfTokens, Class<Exception> exceptedException,
                                     GraphDatabaseService db) {
        String uuidText = "text";
        Map<String, Object> params = Map.of(
                "uuidText", uuidText,
                "uuidBefore", uuidBefore == null ? "" : uuidBefore,
                "uuidAfter", uuidAfter == null ? "" : uuidAfter,
                "characterList", characterList,
                "config", configuration
        );

        // fixture
        try (var tx = db.beginTx()) {
            Node currentNode = tx.createNode(Label.label("Text"));
            currentNode.setProperty("uuid", uuidText);

            for (int i = 0; i < fixtureLength; i++) {
                Node nextNode = tx.createNode(Label.label("Token"));
                nextNode.setProperty("uuid", Integer.valueOf(i).toString());
                currentNode.createRelationshipTo(nextNode, RelationshipType.withName("NEXT_TOKEN"));
                currentNode = nextNode;
            }
            tx.commit();
        }

        try {
            // cut
            db.executeTransactionally("""
                CALL atag.chains.update("text", $uuidBefore, $uuidAfter, $characterList, $config) YIELD path
                RETURN path
                """, params);
            if (exceptedException !=null) {
                fail("expected a exception but did not get one.");
            }

            validatePathLength(db, """
                    CALL atag.chains.update("text", $uuidBefore, $uuidAfter, $characterList, $config) YIELD path
                    RETURN path
                    """, params, expectedLength);

            // verification
            db.executeTransactionally("MATCH (t:Token) RETURN count(t) as count", Collections.emptyMap(), result -> {
                assertEquals((long) expectedNumberOfTokens, Iterators.single(result).get("count"));
                return true;
            });

            validatePathLength(db, """
                    MATCH path=(:Text{uuid:$uuidText})-[:NEXT_TOKEN*0..]->(x)
                    WHERE NOT (x)-[:NEXT_TOKEN]->()
                    RETURN path
                    """, params, expectedQueryLength);
        } catch (QueryExecutionException e) {
            if (exceptedException == null) {
                fail("unexpected exception", e);
            } else {
                assertEquals(exceptedException, ExceptionUtils.getRootCause(e).getClass());
            }
        }
    }
}
