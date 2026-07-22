package atag.export.collect;

import atag.export.Subgraph;

/**
 * Stage one of an export: gather the {@link Subgraph} that should be exported.
 * Implementations differ in <em>where</em> the subgraph comes from (an explicit
 * list, a rule-based traversal, ...), not in how it is later serialized.
 */
public interface SubgraphCollector {
    Subgraph collect();
}
