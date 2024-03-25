package atag.util;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;

/**
 * helper class to define result types for procedures
 */
public class ResultTypes {
    public static class PathResult {
        public final Path path;

        public PathResult(Path path) {
            this.path = path;
        }
    }

    public static class NodeResult {
        public final Node node;

        public NodeResult(Node node) {
            this.node = node;
        }
    }
}
