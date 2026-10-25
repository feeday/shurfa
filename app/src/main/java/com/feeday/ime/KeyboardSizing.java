package com.feeday.ime;

/** Dimensions in dp; independent of Android so small-window behavior can be tested. */
public final class KeyboardSizing {
    public final int row, header, candidates, maxWidth;
    public final boolean compact;
    private KeyboardSizing(int row, int header, int candidates, int maxWidth, boolean compact) {
        this.row = row; this.header = header; this.candidates = candidates;
        this.maxWidth = maxWidth; this.compact = compact;
    }
    public static KeyboardSizing forWindow(int heightDp, boolean landscape) {
        int height = heightDp > 0 ? heightDp : 640;
        boolean compact = landscape || height < 480;
        int header = compact ? 28 : 36;
        int candidates = compact ? 34 : 44;
        int budget = Math.min(compact ? 226 : 340,
                Math.round(height * (compact ? .60f : .46f)));
        // Four rows, with 8dp outer padding. Never use a fixed portrait height in landscape.
        int row = Math.max(22, Math.min(compact ? 40 : 62,
                (budget - header - candidates - 8) / 4));
        return new KeyboardSizing(row, header, candidates, landscape ? 760 : 600, compact);
    }
    public int totalHeight() { return header + candidates + 4 * row + 8; }
}
