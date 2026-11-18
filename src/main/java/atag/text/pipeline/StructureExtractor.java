package atag.text.pipeline;

import atag.profile.ImportProfile;

import java.util.List;

/**
 * Phase 2 of the import pipeline: walk the parsed document, build the plain text and
 * identify the structures the profile selects - inline elements, stand-off annotations
 * and entity declarations - as ranges over that text.
 */
public interface StructureExtractor<D> {
    ExtractedStructure extract(D document, ImportProfile profile);

    /** Only the entity declarations of a document, for sources that declare but hold no text. */
    default List<ExtractedEntity> extractEntities(D document, ImportProfile profile) {
        return List.of();
    }
}
