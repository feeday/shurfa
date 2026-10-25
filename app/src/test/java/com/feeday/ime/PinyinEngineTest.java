package com.feeday.ime;

import org.junit.Test;
import java.io.StringReader;
import java.util.List;
import static org.junit.Assert.*;

public class PinyinEngineTest {
    private PinyinEngine engine() throws Exception {
        return new PinyinEngine(new StringReader("ni 你 尼\nhao 好 号\nnihao 你好\nnv 女\n"));
    }
    @Test public void completePhraseComesBeforeSyllable() throws Exception {
        List<PinyinEngine.Candidate> list = engine().lookup("nihao");
        assertEquals("你好", list.get(0).text);
        assertEquals(5, list.get(0).consumed);
        assertEquals("你", list.get(1).text);
        assertEquals(2, list.get(1).consumed);
    }
    @Test public void unknownSentenceSupportsPrefixSelection() throws Exception {
        PinyinEngine engine = engine();
        String input = "nihaoma";
        PinyinEngine.Candidate c = engine.lookup(input).get(0);
        assertEquals("你好", c.text);
        assertEquals("ma", input.substring(c.consumed));
    }
    @Test public void emptyAndUnknownInputHaveNoCandidates() throws Exception {
        assertTrue(engine().lookup("").isEmpty());
        assertTrue(engine().lookup("xyz").isEmpty());
    }
    @Test public void umlautAndUppercaseAreHandled() throws Exception {
        assertEquals("女", engine().lookup("nv").get(0).text);
        assertEquals("你好", engine().lookup("NIHAO").get(0).text);
    }
    @Test public void duplicateDictionaryLinesMergeWithoutDuplicates() throws Exception {
        PinyinEngine e = new PinyinEngine(new StringReader("# note\nni 你 尼\nni 你 泥\n"));
        assertEquals(3, e.lookup("ni").size());
    }
    @Test public void t9MapsEveryLetterIncludingUmlautV() {
        assertEquals("22233344455566677778889999", PinyinEngine.toDigits("abcdefghijklmnopqrstuvwxyz"));
        assertEquals("64426", PinyinEngine.toDigits("nihao"));
        assertEquals("58", PinyinEngine.toDigits("lv"));
    }
    @Test public void t9PhraseRanksBeforePrefix() throws Exception {
        List<PinyinEngine.Candidate> list = engine().lookupT9("64426");
        assertEquals("你好", list.get(0).text);
        assertEquals("nihao", list.get(0).pinyin);
        assertEquals(5, list.get(0).consumed);
        assertEquals("你", list.get(1).text);
    }
    @Test public void t9DisambiguatesAndConsumesOnlySelectedPrefix() throws Exception {
        PinyinEngine e = new PinyinEngine(new StringReader("mi 米\nni 你\nhao 好\nnihao 你好\nshijie 世界\n"));
        List<PinyinEngine.Candidate> ambiguous = e.lookupT9("64");
        assertEquals("mi", ambiguous.get(0).pinyin);
        assertEquals("ni", ambiguous.get(1).pinyin);
        String input = PinyinEngine.toDigits("nihaoshijie");
        PinyinEngine.Candidate first = e.lookupT9(input).get(0);
        assertEquals("你好", first.text);
        assertEquals("世界", e.lookupT9(input.substring(first.consumed)).get(0).text);
    }
    @Test public void t9RejectsNonKeypadInput() throws Exception {
        for (String s : new String[]{"", "10", "ni", "64'426", "64 426"})
            assertTrue(engine().lookupT9(s).isEmpty());
    }
    @Test public void t9DoesNotHideCandidatesBeyondFirstPage() throws Exception {
        StringBuilder source = new StringBuilder("ni");
        for (int i = 0; i < 80; i++) source.append(' ').append((char) (0x4e00 + i));
        assertEquals(80, new PinyinEngine(new StringReader(source.toString())).lookupT9("64").size());
    }
}
