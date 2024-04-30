package atag.text;

import net.sf.saxon.lib.Logger;
import org.neo4j.logging.Log;

public class Neo4jLoggerBridge extends Logger {

    private final Log log;

    public Neo4jLoggerBridge(Log log) {
        this.log = log;
    }

    @Override
    public void println(String s, int i) {
        switch (i) {
            case Logger.INFO:
                log.info(s);
                break;
            case Logger.WARNING:
                log.warn(s);
                break;
            case Logger.ERROR, Logger.DISASTER:
                log.error(s);
                break;
        }
    }
}
