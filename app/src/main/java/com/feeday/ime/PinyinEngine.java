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
        public final String pinyin;
        Candidate(String text, int consumed, String pinyin) {
            this.text = text; this.consumed = consumed; this.pinyin = pinyin;
        }
    }
    private final Map<String, List<String>> words = new LinkedHashMap<>();
    private final Map<String, List<Candidate>> t9 = new LinkedHashMap<>();

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
        for (Map.Entry<String, List<String>> entry : words.entrySet()) {
            String digits = toDigits(entry.getKey());
            List<Candidate> bucket = t9.get(digits);
            if (bucket == null) { bucket = new ArrayList<>(); t9.put(digits, bucket); }
            for (String word : entry.getValue()) {
                bucket.add(new Candidate(word, digits.length(), entry.getKey()));
            }
        }
    }

    /** Telephone keypad mapping: one tap per letter, including v for ü. */
    public static String toDigits(String pinyin) {
        StringBuilder result = new StringBuilder();
        for (char c : pinyin.toLowerCase(Locale.ROOT).toCharArray()) {
            if (c < 'a' || c > 'z') throw new IllegalArgumentException("Expected a-z");
            result.append("22233344455566677778889999".charAt(c - 'a'));
        }
        return result.toString();
    }

    public List<Candidate> lookupT9(String digits) {
        if (digits.isEmpty() || !digits.matches("[2-9]+")) return Collections.emptyList();
        List<Candidate> results = new ArrayList<>();
        for (int end = digits.length(); end > 0; end--) {
            List<Candidate> bucket = t9.get(digits.substring(0, end));
            if (bucket != null) results.addAll(bucket);
        }
        // Do not truncate ambiguous keys: every dictionary candidate stays reachable.
        return results;
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
                results.add(new Candidate(text, end, key.substring(0, end)));
                if (results.size() >= 40) return results;
            }
        }
        return results;
    }
}
