package atag.model;

import atag.model.Ramen.Concept;
import org.neo4j.graphdb.Transaction;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Procedures for the meta graph: the project-specific model kept as graph data alongside
 * the instance data it describes. Once written, import and export profiles can refer to
 * it with {@code model: 'meta'} instead of spelling the model out in every call.
 */
public class ModelProcedures {

    @Context
    public Transaction tx;

    public static class MapResult {
        public final Map<String, Object> value;

        public MapResult(Map<String, Object> value) {
            this.value = value;
        }
    }

    @Procedure(name = "atag.model.meta.write", mode = Mode.WRITE)
    @Description("write the project model of a profile configuration to the graph as a meta graph")
    public Stream<MapResult> write(@Name(value = "config", defaultValue = "{}") Map<String, Object> config) {
        return Stream.of(new MapResult(MetaGraph.write(tx, ProjectModel.from(config))));
    }

    @Procedure(name = "atag.model.meta.read", mode = Mode.READ)
    @Description("read the project model back from the meta graph")
    public Stream<MapResult> read() {
        ProjectModel model = MetaGraph.read(tx);
        Map<String, Object> result = new LinkedHashMap<>();
        for (Concept concept : Concept.values()) {
            result.put(concept.name().toLowerCase(), model.labels(concept));
        }
        result.put("partOf", model.partOf().name());
        result.put("hasAnnotation", model.hasAnnotation().name());
        result.put("refersTo", model.refersTo().name());
        return Stream.of(new MapResult(result));
    }
}
