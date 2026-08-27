package atag.text.pipeline;

import atag.profile.ImportProfile;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.Log;

import java.util.List;

/**
 * The import pipeline: parse and validate the source (phase 1), extract its structure
 * (phase 2), map it into the project vocabulary (phase 3) and construct the graph
 * (phase 4). Only the first two phases know the markup language, which is why they are
 * the only ones exchanged between the XML and the HTML pipeline.
 *
 * @param <D> the parsed document type handed from phase 1 to phase 2
 */
public class ImportPipeline<D> {

    private final SourceReader<D> reader;
    private final StructureExtractor<D> extractor;
    private final StructureMapper mapper = new StructureMapper();
    private final GraphConstructor constructor;
    private final Log log;

    public ImportPipeline(SourceReader<D> reader, StructureExtractor<D> extractor, Log log) {
        this.reader = reader;
        this.extractor = extractor;
        this.constructor = new GraphConstructor(log);
        this.log = log;
    }

    public static ImportPipeline<org.w3c.dom.Document> xml(Log log) {
        return new ImportPipeline<>(new XmlSourceReader(), new XmlStructureExtractor(), log);
    }

    public static ImportPipeline<org.jsoup.nodes.Document> html(Log log) {
        return new ImportPipeline<>(new HtmlSourceReader(), new HtmlStructureExtractor(), log);
    }

    public List<Node> run(Transaction tx, Node contentNode, String source, ImportProfile profile) {
        D document = reader.read(source, profile);
        ExtractedStructure structure = extractor.extract(document, profile);
        MappedStructure mapped = mapper.map(structure, profile);
        List<Node> annotations = constructor.construct(tx, contentNode, mapped, profile);
        log.debug("imported {} annotations and {} entity declarations from {} characters of text",
                annotations.size(), mapped.entities().size(), mapped.plainText().length());
        return annotations;
    }
}
