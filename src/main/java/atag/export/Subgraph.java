package atag.export;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

import java.util.List;

/**
 * A collected set of nodes and relationships that forms the input to an {@link atag.export.format.Exporter}.
 * Produced by a {@link atag.export.collect.SubgraphCollector}, independent of how it was gathered.
 */
public record Subgraph(List<Node> nodes, List<Relationship> relationships) {
}
