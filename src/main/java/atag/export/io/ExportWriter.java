package atag.export.io;

import org.neo4j.configuration.Config;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.kernel.internal.GraphDatabaseAPI;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static java.nio.file.Files.writeString;

/**
 * Writes an already-rendered document into the Neo4j import directory. Format-agnostic:
 * it takes serialized text, so it works for JSON, XML or any other textual export format.
 */
public class ExportWriter {

    private final Path importFolder;

    public ExportWriter(GraphDatabaseAPI graphDatabaseAPI) {
        Config config = graphDatabaseAPI.getDependencyResolver().resolveDependency(Config.class);
        this.importFolder = config.get(GraphDatabaseSettings.load_csv_file_url_root);
    }

    public String write(String fileName, String content) {
        if (fileName.contains(File.separator)) {
            throw new IllegalArgumentException("File name must not contain path separators");
        }
        Path outputPath = importFolder.resolve(fileName);
        try {
            writeString(outputPath, content);
            long size = outputPath.toFile().length();
            return String.format("%d bytes written to %s", size, fileName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
