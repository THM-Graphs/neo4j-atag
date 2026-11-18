package atag.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XPathSyntaxTest {

    @Test
    void rewritesElementAndAttributeWildcards() {
        assertEquals("/*[local-name()='teiCorpus']/*[local-name()='TEI']",
                XPathSyntax.expand("/*:teiCorpus/*:TEI"));
        assertEquals("/*[local-name()='TEI']/@*[local-name()='id']",
                XPathSyntax.expand("/*:TEI/@*:id"));
        assertEquals("/*[local-name()='TEI']/*[local-name()='text']/*[local-name()='body']"
                        + "//node()[not(self::*[local-name()='ab'])]",
                XPathSyntax.expand("/*:TEI/*:text/*:body//node()[not(self::*:ab)]"));
    }

    @Test
    void leavesEverythingElseAsItIs() {
        String written = "/*[local-name()='TEI']/*[local-name()='standOff']";
        assertEquals(written, XPathSyntax.expand(written));
        assertEquals("normalize-space((.//*[@type='reg'])[1])",
                XPathSyntax.expand("normalize-space((.//*[@type='reg'])[1])"));
        assertEquals("@xml:id", XPathSyntax.expand("@xml:id"));
        assertEquals("", XPathSyntax.expand(""));
    }

    @Test
    void doesNotTouchWhatIsInsideAStringLiteral() {
        assertEquals("*[local-name()='item'][@n='*:terms']",
                XPathSyntax.expand("*:item[@n='*:terms']"));
        assertEquals("*[local-name()='list'][@type=\"*:entity\"]/*[local-name()='item']",
                XPathSyntax.expand("*:list[@type=\"*:entity\"]/*:item"));
    }
}
