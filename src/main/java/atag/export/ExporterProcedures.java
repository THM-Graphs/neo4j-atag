package atag.export;

import apoc.result.ObjectResult;
import atag.export.collect.ListCollector;
import atag.export.collect.SubgraphCollector;
import atag.export.collect.TraversalCollector;
import atag.export.collect.TraversalRules;
import atag.export.format.Exporter;
import atag.export.format.jgf.JgfExporter;
import atag.export.format.standoff.StandoffJsonExporter;
import atag.export.format.standoff.StandoffXmlExporter;
import atag.export.io.ExportWriter;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.procedure.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Thin dispatch layer for the export pipeline. Each procedure wires together a
 * {@link SubgraphCollector} (what to export) and an {@link Exporter} (how to render it);
 * all of the actual work lives in those collaborators.
 */
public class ExporterProcedures {

    @Context
    public GraphDatabaseAPI graphDatabaseAPI;

    @UserFunction
    @Description("export a graph into JGF format and return as a map")
    public Map<String, Object> jgf(@Name("nodes") List<Node> nodes, @Name("relationships") List<Relationship> relationships) {
        return new JgfExporter().toValue(new Subgraph(nodes, relationships));
    }

    @Procedure(name = "atag.export.jgf.list", mode = Mode.READ)
    @Description("export a list of nodes and relationships into JGF format")
    public Stream<ObjectResult> jgfList(@Name("nodes") List<Node> nodes, @Name("relationships") List<Relationship> relationships, @Name("config") Map<String, Object> config) {
        return export(new ListCollector(nodes, relationships), new JgfExporter(), config);
    }

    @Procedure(name = "atag.export.jgf.fromNode", mode = Mode.READ)
    @Description("traverse from a start node and export the subgraph as JGF")
    public Stream<ObjectResult> jgfFromNode(@Name("startNode") Node startNode, @Name("config") Map<String, Object> config) {
        return export(traversal(startNode, config), new JgfExporter(), config);
    }

    @Procedure(name = "atag.export.standoff_json.list", mode = Mode.READ)
    @Description("export a list of nodes and relationships as standoff JSON")
    public Stream<ObjectResult> standoffJsonList(@Name("nodes") List<Node> nodes, @Name("relationships") List<Relationship> relationships, @Name("config") Map<String, Object> config) {
        return export(new ListCollector(nodes, relationships), new StandoffJsonExporter(), config);
    }

    @Procedure(name = "atag.export.standoff_json.fromNode", mode = Mode.READ)
    @Description("traverse from a start node and export the subgraph as standoff JSON")
    public Stream<ObjectResult> standoffJsonFromNode(@Name("startNode") Node startNode, @Name("config") Map<String, Object> config) {
        return export(traversal(startNode, config), new StandoffJsonExporter(), config);
    }

    @Procedure(name = "atag.export.standoff_xml.list", mode = Mode.READ)
    @Description("export a list of nodes and relationships as standoff XML")
    public Stream<ObjectResult> standoffXmlList(@Name("nodes") List<Node> nodes, @Name("relationships") List<Relationship> relationships, @Name("config") Map<String, Object> config) {
        return export(new ListCollector(nodes, relationships), new StandoffXmlExporter(), config);
    }

    @Procedure(name = "atag.export.standoff_xml.fromNode", mode = Mode.READ)
    @Description("traverse from a start node and export the subgraph as standoff XML")
    public Stream<ObjectResult> standoffXmlFromNode(@Name("startNode") Node startNode, @Name("config") Map<String, Object> config) {
        return export(traversal(startNode, config), new StandoffXmlExporter(), config);
    }

    private SubgraphCollector traversal(Node startNode, Map<String, Object> config) {
        return new TraversalCollector(startNode, TraversalRules.from(config));
    }

    private Stream<ObjectResult> export(SubgraphCollector collector, Exporter exporter, Map<String, Object> config) {
        Subgraph subgraph = collector.collect();
        String fileName = (String) config.get("fileName");
        if (fileName != null) {
            String message = new ExportWriter(graphDatabaseAPI).write(fileName, exporter.render(subgraph));
            return Stream.of(new ObjectResult(message));
        }
        return Stream.of(new ObjectResult(exporter.toValue(subgraph)));
    }
}
