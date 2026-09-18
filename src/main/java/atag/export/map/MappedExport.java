package atag.export.map;

import atag.model.Ramen.Concept;

import java.util.List;
import java.util.Map;

/**
 * Result of export phase 2: a serialization-neutral view of the collected subgraph.
 * Anchors form a tree along the {@code PART_OF} hierarchy, annotations sit on the anchor
 * or annotation they were attached to, and entity references have been resolved to
 * identifiers. Every format renders this same model, so JSON, XML and TEI cannot drift
 * apart in how they interpret the graph.
 *
 * @param entities the entities in scope, so a format can serialize them alongside the
 *                 references pointing at them
 */
public record MappedExport(MappedDocument root, List<MappedEntity> entities) {

    /**
     * An anchor: a collection or a content node. {@code properties} holds the complete set
     * of mapped properties, with {@code id} and {@code text} as named views on the two
     * that formats need to address individually.
     *
     * @param header a verbatim document header the anchor carries, or {@code null}
     */
    public record MappedDocument(String name,
                                 Concept concept,
                                 String id,
                                 String text,
                                 String header,
                                 Map<String, Object> properties,
                                 List<MappedAnnotation> annotations,
                                 List<MappedDocument> children) {
    }

    /**
     * An annotation over a range of its anchor's text, or - when it carries no range - a
     * statement about the annotation it is nested in.
     *
     * @param element    the markup element this annotation maps to, or {@code null} when
     *                   neither the dictionary nor the graph names one
     * @param depth      nesting depth in the source the annotation was imported from, or
     *                   {@code null}; decides the order of annotations over the same range
     * @param references identifiers of the entities the annotation refers to
     */
    public record MappedAnnotation(String element,
                                   String id,
                                   Long startIndex,
                                   Long endIndex,
                                   Long depth,
                                   Map<String, Object> properties,
                                   List<String> references,
                                   List<MappedAnnotation> children) {
    }

    /**
     * @param element the markup element the entity was declared with, or {@code null}
     * @param source  the entity's verbatim declaration, or {@code null}
     */
    public record MappedEntity(String id,
                               List<String> labels,
                               String label,
                               String element,
                               String source,
                               Map<String, Object> properties) {
    }
}
