package atag.text.pipeline;

import atag.profile.ImportProfile;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Phase 1 for HTML sources. HTML is parsed leniently by design, so there is nothing to
 * reject here; pretty printing is switched off because it would insert line breaks and
 * shift every character offset computed in phase 2.
 */
public class HtmlSourceReader implements SourceReader<Document> {

    @Override
    public Document read(String source, ImportProfile profile) {
        Document document = Jsoup.parse(source);
        document.outputSettings().prettyPrint(false);
        return document;
    }
}
