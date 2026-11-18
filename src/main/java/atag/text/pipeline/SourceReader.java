package atag.text.pipeline;

import atag.profile.ImportProfile;

/**
 * Phase 1 of the import pipeline: parse the source document and check it against the
 * expectations declared in the profile. Implementations differ in the markup language
 * they read, and therefore in the parsed document type they hand on to phase 2.
 */
public interface SourceReader<D> {
    D read(String source, ImportProfile profile);
}
