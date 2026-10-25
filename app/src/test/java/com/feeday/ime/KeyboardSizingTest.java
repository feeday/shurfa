package com.feeday.ime;

import org.junit.Test;
import static org.junit.Assert.*;

public class KeyboardSizingTest {
    @Test public void landscapeLeavesRoomForEditor() {
        for (int height : new int[]{320, 360, 400, 480}) {
            KeyboardSizing s = KeyboardSizing.forWindow(height, true);
            assertTrue(s.totalHeight() <= height * .61f);
            assertTrue(s.row >= 28);
            assertTrue(s.compact);
        }
    }
    @Test public void portraitHasLargerTargetsAndBoundedHeight() {
        KeyboardSizing portrait = KeyboardSizing.forWindow(800, false);
        assertTrue(portrait.row >= 52);
        assertTrue(portrait.totalHeight() <= 340);
        assertTrue(portrait.row > KeyboardSizing.forWindow(360, true).row);
    }
    @Test public void smallAndUnknownWindowsStayUsable() {
        assertTrue(KeyboardSizing.forWindow(300, false).compact);
        assertTrue(KeyboardSizing.forWindow(0, false).row > 0);
        assertEquals(760, KeyboardSizing.forWindow(600, true).maxWidth);
    }
}
