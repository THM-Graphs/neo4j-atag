package atag.text.pipeline;

import atag.profile.ImportProfile;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.logging.Log;

import java.util.ArrayList;
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

    /**
     * Build the collections and content nodes the source describes, and import the text
     * of every content node among them. This is the whole of a corpus document in one
     * call: hierarchy, register, texts, annotations and entity references.
     */
    public List<Node> importDocuments(Transaction tx, String source, ImportProfile profile) {
        List<Node> documents = new DocumentBuilder(log).build(tx, source, profile);
        log.debug("built {} collection and content nodes from {} characters of source",
                documents.size(), source.length());
        return documents;
    }

    /**
     * Only phases 1 to 4 for the entity declarations of a document: nothing is extracted
     * as text and nothing is written to the start node. This is how a register that is
     * declared once for a whole corpus enters the graph.
     */
    public List<Node> importEntities(Transaction tx, String source, ImportProfile profile) {
        D document = reader.read(source, profile);
        List<ExtractedEntity> declarations = extractor.extractEntities(document, profile);
        List<MappedStructure.MappedEntity> mapped = mapper.mapEntities(declarations, profile);
        List<Node> entities = new ArrayList<>(constructor.entities(tx, mapped, profile).values());
        log.debug("imported {} of {} entity declarations", entities.size(), declarations.size());
        return entities;
    }
}
