package com.feeday.ime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Small, deterministic offline dictionary. No learning or network requests. */
public final class PinyinEngine {
    public static final class Candidate {
        public final String text;
        public final int consumed;
        Candidate(String text, int consumed) { this.text = text; this.consumed = consumed; }
    }
    private final Map<String, List<String>> words = new LinkedHashMap<>();

    public PinyinEngine(Reader source) throws IOException {
        try (BufferedReader reader = new BufferedReader(source)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 2) continue;
                List<String> values = words.get(parts[0]);
                if (values == null) {
                    values = new ArrayList<>();
                    words.put(parts[0], values);
                }
                for (int i = 1; i < parts.length; i++) if (!values.contains(parts[i])) values.add(parts[i]);
            }
        }
    }

    public List<Candidate> lookup(String input) {
        if (input.isEmpty()) return Collections.emptyList();
        String key = input.toLowerCase(Locale.ROOT);
        List<Candidate> results = new ArrayList<>();
        // Prefer complete phrases, then progressively shorter known prefixes.
        for (int end = key.length(); end > 0; end--) {
            List<String> matches = words.get(key.substring(0, end));
            if (matches == null) continue;
            for (String text : matches) {
                results.add(new Candidate(text, end));
                if (results.size() >= 40) return results;
            }
        }
        return results;
    }
}
