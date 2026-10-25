package com.feeday.ime;
import org.junit.Test;
import static org.junit.Assert.*;

public class VoiceSessionTest {
    @Test public void resultOnlyReachesOriginalEditor() {
        VoiceSession session = new VoiceSession(); Object editor = new Object();
        long id = session.start(editor);
        assertTrue(session.accepts(id, editor));
        assertFalse(session.accepts(id, new Object()));
        assertFalse(session.accepts(id, null));
    }
    @Test public void cancellationRejectsDelayedResult() {
        VoiceSession session = new VoiceSession(); Object editor = new Object();
        long id = session.start(editor); session.cancel();
        assertFalse(session.accepts(id, editor));
    }
    @Test public void previousResultCannotEnterNewRecordingEvenInSameField() {
        VoiceSession session = new VoiceSession(); Object editor = new Object();
        long oldId = session.start(editor); long newId = session.start(editor);
        assertFalse(session.accepts(oldId, editor));
        assertTrue(session.accepts(newId, editor));
    }
}
