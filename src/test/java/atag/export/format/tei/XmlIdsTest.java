package atag.export.format.tei;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class XmlIdsTest {

    @ParameterizedTest
    @CsvSource({
            "a-1,a-1",
            "123,_123",
            "urn:x:1,urn_x_1",
            "'a b',a_b",
            "'',_",
            "-x,_-x",
            "Äpfel.1,Äpfel.1"
    })
    void invalidIdsAreRewritten(String raw, String expected) {
        assertThat(new XmlIds().of(raw)).isEqualTo(expected);
    }

    @Test
    void sameGraphIdYieldsSameValue() {
        XmlIds ids = new XmlIds();
        assertThat(ids.of("a b")).isEqualTo(ids.of("a b"));
    }

    @Test
    void distinctGraphIdsStayDistinct() {
        XmlIds ids = new XmlIds();
        assertThat(ids.of("a b")).isEqualTo("a_b");
        assertThat(ids.of("a_b")).isEqualTo("a_b_2");
    }

    @Test
    void generatedIdsAvoidTakenOnes() {
        XmlIds ids = new XmlIds();
        assertThat(ids.of("atag-1")).isEqualTo("atag-1");
        assertThat(ids.generate()).isEqualTo("atag-2");
    }
}
