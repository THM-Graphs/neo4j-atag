package atag.util;

import org.neo4j.procedure.Name;
import org.neo4j.procedure.UserFunction;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class DeterministicUUID {

    @UserFunction(name = "atag.deterministicUUID")
    public String deterministicUUID(
            @Name("input") String input
    ) {
        UUID uuid = UUID.nameUUIDFromBytes(
                input.getBytes(StandardCharsets.UTF_8)
        );
        return uuid.toString();
    }
}
