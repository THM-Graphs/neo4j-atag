package atag.export.format;

import atag.export.Subgraph;
import atag.profile.ExportProfile;

/**
 * The serializing end of an export: turn a collected {@link Subgraph} into a concrete
 * representation, guided by the profile. Implementations own their serialization format
 * (JSON, XML, TEI, ...), which is what keeps the export pipeline from being tied to any
 * single format.
 */
public interface Exporter {

    /**
     * The representation returned to Cypher as {@code YIELD value}. Typically a
     * {@code Map} for JSON-shaped formats or a {@code String} for textual formats
     * such as XML.
     */
    Object toValue(Subgraph subgraph, ExportProfile profile);

    /**
     * The serialized document written to a file. For textual formats this is the
     * same content returned by {@link #toValue}; for map-based formats it is the
     * pretty-printed serialization.
     */
    String render(Subgraph subgraph, ExportProfile profile);
}
