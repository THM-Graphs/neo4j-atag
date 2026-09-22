package atag.profile;

import atag.model.MetaGraph;
import atag.model.ProjectModel;
import org.neo4j.graphdb.Transaction;
import org.neo4j.procedure.Context;
import org.neo4j.procedure.Description;
import org.neo4j.procedure.Mode;
import org.neo4j.procedure.Name;
import org.neo4j.procedure.Procedure;
import org.neo4j.procedure.UserFunction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Procedures for profiles kept in the graph: a project declares once how its documents
 * are read and written, and every later call refers to that profile by name.
 */
public class ProfileProcedures {

    @Context
    public Transaction tx;

    public static class MapResult {
        public final Map<String, Object> value;

        public MapResult(Map<String, Object> value) {
            this.value = value;
        }
    }

    @Procedure(name = "atag.profile.write", mode = Mode.WRITE)
    @Description("store an import/export profile under its name; its model is written to the meta graph as well")
    public Stream<MapResult> write(@Name("profile") Map<String, Object> profile) {
        Object name = profile.get("name");
        if (!(name instanceof String) || ((String) name).isBlank()) {
            throw new IllegalArgumentException("a profile needs a name to be stored under");
        }
        MetaGraph.writeProfile(tx, (String) name, Profiles.toJson(profile));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("sections", profile.keySet().stream().filter(key -> !"name".equals(key)).sorted().toList());
        if (profile.get("model") instanceof Map) {
            result.put("model", MetaGraph.write(tx, ProjectModel.from(profile)));
        }
        return Stream.of(new MapResult(result));
    }

    @Procedure(name = "atag.profile.read", mode = Mode.READ)
    @Description("read a stored profile back")
    public Stream<MapResult> read(@Name("name") String name) {
        String json = MetaGraph.readProfileJson(tx, name);
        if (json == null) {
            throw new IllegalArgumentException("no profile named '" + name + "' has been written");
        }
        return Stream.of(new MapResult(Profiles.fromJson(json)));
    }

    @UserFunction(name = "atag.profile.parse")
    @Description("parse a profile written as JSON, so that one kept in version control can be stored")
    public Map<String, Object> parse(@Name("json") String json) {
        return Profiles.fromJson(json);
    }
}
