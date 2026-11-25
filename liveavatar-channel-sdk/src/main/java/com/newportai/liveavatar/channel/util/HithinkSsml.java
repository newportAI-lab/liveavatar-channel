package com.newportai.liveavatar.channel.util;

import java.util.ArrayList;
import java.util.List;

/** Builds emotion SSML for avatars configured to use HiThink TTS. Other TTS providers cannot use these fragments. */
public final class HithinkSsml {
    private static final int MAX_LENGTH = 10_000;
    private static final int TARGET_LENGTH = 3_000;
    private static final String SUFFIX = "</emotion></voice></speak>";
    private static final String SENTENCE_END = ".!?。！？\n\r";
    private static final String PAUSE = ",;:，；：、 \t";

    private HithinkSsml() { }

    /**
     * Builds ordered emotion SSML fragments for an avatar configured with platform HiThink TTS.
     * Pass plain text directly to a prompt or response method when no emotion label is needed.
     * Send each returned fragment separately, in list order, within the same stream.
     * Each fragment contains at most 3,000 Unicode code points, including tags and XML escapes.
     * If the complete SSML fits within that limit, the list contains one fragment.
     *
     * @param text plain text to escape and wrap as SSML
     * @param voiceId HiThink voice ID from the avatar configuration
     * @param label emotion applied to every fragment. HiThink documents {@code natural}, {@code non},
     *              {@code neutral}, {@code happy}, {@code sad}, {@code angry}, {@code fearful},
     *              {@code disgust}, and {@code surprised}. The effect depends on the voice.
     *              The SDK accepts unrecognized labels. HiThink treats them as text without an explicit emotion.
     * @return complete SSML fragments in send order
     * @throws IllegalArgumentException if an argument is null, blank, or contains invalid XML characters,
     *                                  or a fragment cannot fit the markup and the next escaped character
     */
    public static List<String> emotionSegments(String text, String voiceId, String label) {
        return emotionSegments(text, voiceId, label, TARGET_LENGTH);
    }

    /**
     * Builds HiThink emotion SSML with a custom fragment limit.
     * See {@link #emotionSegments(String, String, String)} for usage and label handling.
     *
     * @param text plain text to escape and wrap as SSML
     * @param voiceId HiThink voice ID from the avatar configuration
     * @param label emotion applied to every fragment
     * @param maxCodePoints maximum Unicode code points per serialized fragment, including tags and
     *                      XML escapes. Valid range is 1 to 10,000. Prefer 4,000 or fewer for playback.
     * @return complete SSML fragments in send order
     * @throws IllegalArgumentException for null, blank, or XML-invalid strings, a limit outside 1 to 10,000,
     *                                  or insufficient room for the markup and the next escaped character
     */
    public static List<String> emotionSegments(String text, String voiceId, String label, int maxCodePoints) {
        if (maxCodePoints < 1 || maxCodePoints > MAX_LENGTH) {
            throw new IllegalArgumentException("maxCodePoints must be between 1 and 10000");
        }
        String prefix = prefix(text, voiceId, label, maxCodePoints);
        int capacity = maxCodePoints - codePoints(prefix) - SUFFIX.length();
        List<String> fragments = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = start;
            int sentence = start;
            int pause = start;
            int remaining = capacity;
            while (end < text.length()) {
                int cp = text.codePointAt(end);
                int width = escapedWidth(cp);
                if (width > remaining) break;
                remaining -= width;
                end += Character.charCount(cp);
                if (SENTENCE_END.indexOf(cp) >= 0) {
                    sentence = end;
                } else if (PAUSE.indexOf(cp) >= 0) {
                    pause = end;
                }
            }
            if (end == start) {
                throw new IllegalArgumentException("HiThink SSML wrapper leaves insufficient room for text");
            }
            if (end < text.length()) {
                end = sentence > start ? sentence : pause > start ? pause : end;
            }
            fragments.add(fragment(prefix, text.substring(start, end)));
            start = end;
        }
        return fragments;
    }

    private static String prefix(String text, String voiceId, String label, int limit) {
        if ((voiceId != null && codePoints(voiceId) >= limit)
                || (label != null && codePoints(label) >= limit)) {
            throw new IllegalArgumentException("HiThink SSML attributes exceed the fragment limit");
        }
        validate(text);
        validate(voiceId);
        validate(label);
        return "<speak><voice name=\"" + attribute(voiceId) + "\"><emotion type=\"" + attribute(label) + "\">";
    }

    private static String fragment(String prefix, String text) {
        return prefix + escaped(text) + SUFFIX;
    }

    private static int codePoints(String value) {
        return value.codePointCount(0, value.length());
    }

    private static int escapedWidth(int cp) {
        switch (cp) {
            case '<': case '>': return 4;
            case '&': case '\r': return 5;
            case '"': case '\'': return 6;
            default: return 1;
        }
    }

    private static String attribute(String value) {
        return escaped(value).replace("\t", "&#9;").replace("\n", "&#10;");
    }

    private static void validate(String value) {
        if (value == null || value.codePoints().allMatch(cp -> Character.isWhitespace(cp)
                        || Character.isSpaceChar(cp) || cp == 0x85)
                || !value.codePoints().allMatch(cp -> cp == 9 || cp == 10 || cp == 13
                    || cp >= 32 && cp <= 0xD7FF || cp >= 0xE000 && cp <= 0xFFFD || cp >= 0x10000)) {
            throw new IllegalArgumentException("SSML arguments must be non-blank XML characters");
        }
    }

    private static String escaped(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;").replace("\r", "&#13;");
    }
}
