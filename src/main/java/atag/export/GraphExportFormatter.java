package atag.export;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

import java.util.List;
import java.util.Map;

public interface GraphExportFormatter {
    Map<String, Object> format(List<Node> nodes, List<Relationship> relationships);
}
