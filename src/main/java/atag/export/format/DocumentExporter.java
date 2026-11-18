package atag.export.format;

import atag.export.Subgraph;
import atag.export.map.DocumentMapper;
import atag.export.map.MappedExport;
import atag.profile.ExportProfile;

/**
 * Base class for formats that serialize the mapped document model rather than the raw
 * graph: the subgraph first goes through phase 2 ({@link DocumentMapper}), and the format
 * only deals with the result. Graph-oriented formats such as JGF implement
 * {@link Exporter} directly instead, because they serialize nodes and edges as they are.
 */
public abstract class DocumentExporter implements Exporter {

    @Override
    public Object toValue(Subgraph subgraph, ExportProfile profile) {
        return value(new DocumentMapper(profile).map(subgraph), profile);
    }

    @Override
    public String render(Subgraph subgraph, ExportProfile profile) {
        return serialize(new DocumentMapper(profile).map(subgraph), profile);
    }

    protected abstract Object value(MappedExport export, ExportProfile profile);

    protected abstract String serialize(MappedExport export, ExportProfile profile);
}
