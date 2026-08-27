package atag.text;

import atag.profile.ImportProfile;
import atag.text.pipeline.ImportPipeline;
import atag.util.ResultTypes;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.Log;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.Map;
import java.util.stream.Stream;

/**
 * Thin dispatch layer for the import pipeline. Each procedure builds an
 * {@link ImportProfile} and hands it to an {@link ImportPipeline}; all of the work
 * happens in the four phases of that pipeline.
 */
public class Importer {

    @Context
    public Transaction tx;

    @Context
    public Log log;

    @Procedure(mode = Mode.WRITE, name = "atag.text.import.html")
    @Description("generate annotation nodes from an HTML document stored on a node property")
    public Stream<ResultTypes.NodeResult> importHtml(
            @Name("startNode") Node startNode,
            @Name("propertyKey") String propertyKey,
            @Name(value = "label for annotation nodes", defaultValue = "Annotation") String label,
            @Name(value = "property name for plain text", defaultValue = "plainText") String plainTextProperty,
            @Name(value = "relationship type", defaultValue = "HAS_ANNOTATION") String relationshipType,
            @Name(value = "add uuid to annotation nodes", defaultValue = "true") boolean addUuid) {

        ImportProfile profile = ImportProfile.html(Map.of(
                "annotationLabel", label,
                "plainTextProperty", plainTextProperty,
                "relationshipType", relationshipType,
                "addUuid", addUuid,
                "idAttribute", ""));
        return run(ImportPipeline.html(log), startNode, propertyKey, profile);
    }

    @Procedure(mode = Mode.WRITE, name = "atag.text.import.xml")
    @Description("generate annotation nodes from an XML document stored on a node property")
    public Stream<ResultTypes.NodeResult> importXml(
            @Name("startNode") Node startNode,
            @Name("propertyKey") String propertyKey,
            @Name(value = "xpath expression", defaultValue = "/TEI/text/body//node()") String xpath,
            @Name(value = "label for annotation nodes", defaultValue = "Annotation") String label,
            @Name(value = "property name for plain text", defaultValue = "plainText") String plainTextProperty,
            @Name(value = "relationship type", defaultValue = "HAS_ANNOTATION") String relationshipType) {

        ImportProfile profile = ImportProfile.xml(Map.of(
                "xpath", xpath,
                "annotationLabel", label,
                "plainTextProperty", plainTextProperty,
                "relationshipType", relationshipType,
                "addUuid", false,
                "idAttribute", ""));
        return run(ImportPipeline.xml(log), startNode, propertyKey, profile);
    }

    private <D> Stream<ResultTypes.NodeResult> run(ImportPipeline<D> pipeline, Node startNode,
                                                   String propertyKey, ImportProfile profile) {
        String source = (String) startNode.getProperty(propertyKey);
        return pipeline.run(tx, startNode, source, profile).stream().map(ResultTypes.NodeResult::new);
    }
}
