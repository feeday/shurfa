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
}
