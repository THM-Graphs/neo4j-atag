package atag.text.pipeline;

import atag.profile.ImportProfile;

/**
 * Phase 2 of the import pipeline: walk the parsed document, build the plain text and
 * identify the structures the profile selects - inline elements, stand-off annotations
 * and entity declarations - as ranges over that text.
 */
public interface StructureExtractor<D> {
    ExtractedStructure extract(D document, ImportProfile profile);
}
