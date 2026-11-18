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
import atag.export.format.tei.TeiExporter;
import atag.export.io.ExportWriter;
import atag.profile.ExportProfile;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.procedure.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Thin dispatch layer for the export pipeline. Each procedure wires together a
 * {@link SubgraphCollector} (which part of the graph is exported) and an
 * {@link Exporter} (how it is serialized), both configured by the same
 * {@link ExportProfile}; all of the actual work lives in those collaborators.
 */
public class ExporterProcedures {

    @Context
    public GraphDatabaseAPI graphDatabaseAPI;

    @Context
    public Transaction tx;

    @UserFunction
    @Description("export a graph into JGF format and return as a map")
    public Map<String, Object> jgf(@Name("nodes") List<Node> nodes, @Name("relationships") List<Relationship> relationships) {
        return new JgfExporter().toValue(new Subgraph(nodes, relationships), ExportProfile.from(Map.of()));
    }

    @Procedure(name = "atag.export.jgf.list", mode = Mode.READ)
    @Description("export a list of nodes and relationships into JGF format")
    public Stream<ObjectResult> jgfList(@Name("nodes") List<Node> nodes, @Name("relationships") List<Relationship> relationships, @Name("config") Map<String, Object> config) {
        return fromList(nodes, relationships, config, new JgfExporter());
    }

    @Procedure(name = "atag.export.jgf.fromNode", mode = Mode.READ)
    @Description("traverse from a start node and export the subgraph as JGF")
    public Stream<ObjectResult> jgfFromNode(@Name("startNode") Node startNode, @Name("config") Map<String, Object> config) {
        return fromNode(startNode, config, new JgfExporter());
    }

    @Procedure(name = "atag.export.standoff_json.list", mode = Mode.READ)
    @Description("export a list of nodes and relationships as standoff JSON")
    public Stream<ObjectResult> standoffJsonList(@Name("nodes") List<Node> nodes, @Name("relationships") List<Relationship> relationships, @Name("config") Map<String, Object> config) {
        return fromList(nodes, relationships, config, new StandoffJsonExporter());
    }

    @Procedure(name = "atag.export.standoff_json.fromNode", mode = Mode.READ)
    @Description("traverse from a start node and export the subgraph as standoff JSON")
    public Stream<ObjectResult> standoffJsonFromNode(@Name("startNode") Node startNode, @Name("config") Map<String, Object> config) {
        return fromNode(startNode, config, new StandoffJsonExporter());
    }

    @Procedure(name = "atag.export.standoff_xml.list", mode = Mode.READ)
    @Description("export a list of nodes and relationships as standoff XML")
    public Stream<ObjectResult> standoffXmlList(@Name("nodes") List<Node> nodes, @Name("relationships") List<Relationship> relationships, @Name("config") Map<String, Object> config) {
        return fromList(nodes, relationships, config, new StandoffXmlExporter());
    }

    @Procedure(name = "atag.export.standoff_xml.fromNode", mode = Mode.READ)
    @Description("traverse from a start node and export the subgraph as standoff XML")
    public Stream<ObjectResult> standoffXmlFromNode(@Name("startNode") Node startNode, @Name("config") Map<String, Object> config) {
        return fromNode(startNode, config, new StandoffXmlExporter());
    }

    @Procedure(name = "atag.export.tei.list", mode = Mode.READ)
    @Description("export a list of nodes and relationships as TEI/XML")
    public Stream<ObjectResult> teiList(@Name("nodes") List<Node> nodes, @Name("relationships") List<Relationship> relationships, @Name("config") Map<String, Object> config) {
        return fromList(nodes, relationships, config, new TeiExporter());
    }

    @Procedure(name = "atag.export.tei.fromNode", mode = Mode.READ)
    @Description("traverse from a start node and export the subgraph as TEI/XML, inline or as stand-off markup")
    public Stream<ObjectResult> teiFromNode(@Name("startNode") Node startNode, @Name("config") Map<String, Object> config) {
        return fromNode(startNode, config, new TeiExporter());
    }

    private Stream<ObjectResult> fromNode(Node startNode, Map<String, Object> config, Exporter exporter) {
        ExportProfile profile = ExportProfile.from(config, tx);
        return export(new TraversalCollector(startNode, TraversalRules.from(profile)), exporter, profile);
    }

    private Stream<ObjectResult> fromList(List<Node> nodes, List<Relationship> relationships,
                                          Map<String, Object> config, Exporter exporter) {
        ExportProfile profile = ExportProfile.from(config, tx);
        return export(new ListCollector(nodes, relationships), exporter, profile);
    }

    private Stream<ObjectResult> export(SubgraphCollector collector, Exporter exporter, ExportProfile profile) {
        Subgraph subgraph = collector.collect();
        if (profile.fileName() != null) {
            String message = new ExportWriter(graphDatabaseAPI)
                    .write(profile.fileName(), exporter.render(subgraph, profile));
            return Stream.of(new ObjectResult(message));
        }
        return Stream.of(new ObjectResult(exporter.toValue(subgraph, profile)));
    }
}
