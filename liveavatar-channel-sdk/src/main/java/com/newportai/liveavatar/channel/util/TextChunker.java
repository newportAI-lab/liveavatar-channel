package com.newportai.liveavatar.channel.util;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Accumulates LLM SSE text chunks and flushes complete sentences for TTS.
 *
 * <p>Buffers raw streaming text, strips Markdown, normalizes content,
 * detects sentence boundaries, and forwards each complete sentence to
 * the {@code onFlush} callback.
 *
 * <pre>{@code
 * TextChunker chunker = new TextChunker.Builder()
 *     .maxChars(150)
 *     .maxTimeMs(500)
 *     .onFlush(sentence -> client.sendMessage(
 *         MessageBuilder.responseChunk(requestId, responseId, nextSeq(), sentence)))
 *     .build();
 *
 * for (String chunk : llmSseStream) {
 *     chunker.process(chunk);
 * }
 * chunker.flushRemaining();
 * chunker.close();
 * }</pre>
 */
public class TextChunker {

    // ── Configuration defaults ───────────────────────────────
    private static final int DEFAULT_MAX_CHARS = 150;
    private static final long DEFAULT_MAX_TIME_MS = 500;
    private static final Set<String> DEFAULT_HARD_TERMINATORS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(".", "!", "?", "\n\n")));
    private static final Set<String> DEFAULT_SOFT_TERMINATORS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(",", ":", ";")));

    // Shared scheduler for max-time timers
    private static final ScheduledExecutorService SHARED_SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "text-chunker-timer");
                t.setDaemon(true);
                return t;
            });

    // ── Instance fields ──────────────────────────────────────
    private final int maxChars;
    private final long maxTimeMs;
    private final Set<String> hardTerminators;
    private final Set<String> softTerminators;
    private final Consumer<String> onFlush;

    private String buffer = "";
    private boolean backpressure = false;
    private ScheduledFuture<?> timerFuture;

    private TextChunker(Builder builder) {
        this.maxChars = builder.maxChars;
        this.maxTimeMs = builder.maxTimeMs;
        this.hardTerminators = builder.hardTerminators;
        this.softTerminators = builder.softTerminators;
        this.onFlush = builder.onFlush;
    }

    // ── Public API ───────────────────────────────────────────

    /** Current accumulated (un-flushed) text. */
    public String getBuffer() {
        return buffer;
    }

    /** When true the chunker is under backpressure and should slow down. */
    public boolean isBackpressure() {
        return backpressure;
    }

    public void setBackpressure(boolean backpressure) {
        this.backpressure = backpressure;
    }

    /**
     * Ingest an SSE text chunk. Flushes complete sentences via {@code onFlush}.
     */
    public void process(String chunk) {
        String cleaned = stripMarkdown(chunk);
        buffer += cleaned;

        // Flush at the last hard terminator repeatedly
        while (true) {
            int idx = findLastHardBoundary();
            if (idx == -1) break;
            String sentence = buffer.substring(0, idx + 1);
            buffer = buffer.substring(idx + 1);
            emit(sentence);
        }

        // max_chars fallback
        int effectiveMax = backpressure ? maxChars * 2 : maxChars;
        while (buffer.length() >= effectiveMax) {
            int softIdx = findLastSoftBoundary();
            if (softIdx >= 0) {
                String sentence = buffer.substring(0, softIdx + 1);
                buffer = buffer.substring(softIdx + 1);
                emit(sentence);
            } else {
                emit(buffer);
                buffer = "";
            }
        }

        resetTimer();
    }

    /**
     * Flush any text left in the buffer. Call when the LLM stream ends.
     */
    public void flushRemaining() {
        cancelTimer();
        if (!buffer.trim().isEmpty()) {
            emit(buffer);
            buffer = "";
        }
    }

    /**
     * Cancel background timer. Does NOT flush — call {@link #flushRemaining()} first.
     */
    public void close() {
        cancelTimer();
    }

    // ── Builder ─────────────────────────────────────────────

    public static class Builder {
        private int maxChars = DEFAULT_MAX_CHARS;
        private long maxTimeMs = DEFAULT_MAX_TIME_MS;
        private Set<String> hardTerminators = DEFAULT_HARD_TERMINATORS;
        private Set<String> softTerminators = DEFAULT_SOFT_TERMINATORS;
        private Consumer<String> onFlush;

        public Builder maxChars(int maxChars) {
            this.maxChars = maxChars;
            return this;
        }

        public Builder maxTimeMs(long maxTimeMs) {
            this.maxTimeMs = maxTimeMs;
            return this;
        }

        public Builder hardTerminators(Set<String> hardTerminators) {
            this.hardTerminators = hardTerminators;
            return this;
        }

        public Builder softTerminators(Set<String> softTerminators) {
            this.softTerminators = softTerminators;
            return this;
        }

        public Builder onFlush(Consumer<String> onFlush) {
            this.onFlush = onFlush;
            return this;
        }

        public TextChunker build() {
            return new TextChunker(this);
        }
    }

    // ── Internals ────────────────────────────────────────────

    private void emit(String text) {
        text = normalizeText(text);
        if (onFlush != null) {
            onFlush.accept(text);
        }
    }

    private synchronized void resetTimer() {
        cancelTimer();
        if (!buffer.isEmpty() && maxTimeMs > 0) {
            timerFuture = SHARED_SCHEDULER.schedule(() -> {
                synchronized (TextChunker.this) {
                    if (!buffer.trim().isEmpty()) {
                        emit(buffer);
                        buffer = "";
                    }
                }
            }, maxTimeMs, TimeUnit.MILLISECONDS);
        }
    }

    private synchronized void cancelTimer() {
        if (timerFuture != null) {
            timerFuture.cancel(false);
            timerFuture = null;
        }
    }

    // ── Markdown Stripping ───────────────────────────────────

    private static final Pattern FENCE_RE = Pattern.compile("```.*?```", Pattern.DOTALL);
    private static final Pattern UNCLOSED_FENCE_RE = Pattern.compile("```.*", Pattern.DOTALL);
    private static final Pattern TABLE_ROW_RE = Pattern.compile("^\\|.*\\|$", Pattern.MULTILINE);
    private static final Pattern TABLE_SEP_RE = Pattern.compile("^\\|[\\s\\-:|]+\\|$", Pattern.MULTILINE);
    private static final Pattern IMAGE_RE = Pattern.compile("!\\[([^\\]]*)\\]\\([^)]*\\)");
    private static final Pattern LINK_RE = Pattern.compile("\\[([^\\]]*)\\]\\([^)]*\\)");
    private static final Pattern BOLD_RE = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern ITALIC_RE = Pattern.compile("(?<!\\*)\\*([^*]+)\\*(?!\\*)");
    private static final Pattern INLINE_CODE_RE = Pattern.compile("`([^`]+)`");
    private static final Pattern HEADING_RE = Pattern.compile("^#{1,6}\\s+", Pattern.MULTILINE);
    private static final Pattern NUMBERED_LIST_RE = Pattern.compile("^\\s*\\d+\\.\\s+", Pattern.MULTILINE);
    private static final Pattern UNORDERED_LIST_RE = Pattern.compile("^\\s*[-*+]\\s+", Pattern.MULTILINE);
    private static final Pattern BLOCKQUOTE_RE = Pattern.compile("^>\\s*", Pattern.MULTILINE);
    private static final Pattern EXCESS_NL_RE = Pattern.compile("\\n{3,}");

    static String stripMarkdown(String text) {
        if (text == null || text.isEmpty()) return "";

        // 1. Strip fenced code blocks
        text = FENCE_RE.matcher(text).replaceAll("");
        text = UNCLOSED_FENCE_RE.matcher(text).replaceAll("");

        // 2. Remove table rows
        text = TABLE_ROW_RE.matcher(text).replaceAll("");
        text = TABLE_SEP_RE.matcher(text).replaceAll("");

        // 3. Images before links
        text = IMAGE_RE.matcher(text).replaceAll("$1");
        text = LINK_RE.matcher(text).replaceAll("$1");

        // 4. Bold before italic
        text = BOLD_RE.matcher(text).replaceAll("$1");
        text = ITALIC_RE.matcher(text).replaceAll("$1");

        // 5. Inline code
        text = INLINE_CODE_RE.matcher(text).replaceAll("$1");

        // 6. Headings, list markers, blockquotes
        text = HEADING_RE.matcher(text).replaceAll("");
        text = NUMBERED_LIST_RE.matcher(text).replaceAll("");
        text = UNORDERED_LIST_RE.matcher(text).replaceAll("");
        text = BLOCKQUOTE_RE.matcher(text).replaceAll("");

        // 7. Collapse 3+ newlines into double newline
        text = EXCESS_NL_RE.matcher(text).replaceAll("\n\n");

        return text;
    }

    // ── Text Normalization ───────────────────────────────────

    private static final Pattern URL_RE = Pattern.compile("https?://\\S+");
    private static final Pattern EMAIL_RE = Pattern.compile("[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PERCENT_RE = Pattern.compile("(\\d+(?:\\.\\d+)?)%");
    private static final Pattern NUMBER_RE = Pattern.compile("\\d+");

    private static final String[] ONES = {"", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};
    private static final String[] TEENS = {"ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen",
            "sixteen", "seventeen", "eighteen", "nineteen"};
    private static final String[] TENS = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"};

    static String normalizeText(String text) {
        if (text == null || text.isEmpty()) return "";

        text = URL_RE.matcher(text).replaceAll(" link ");
        text = EMAIL_RE.matcher(text).replaceAll(" email ");

        Matcher pm = PERCENT_RE.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (pm.find()) {
            pm.appendReplacement(sb, pm.group(1) + " percent");
        }
        pm.appendTail(sb);
        text = sb.toString();

        Matcher nm = NUMBER_RE.matcher(text);
        sb = new StringBuffer();
        while (nm.find()) {
            int n = Integer.parseInt(nm.group());
            nm.appendReplacement(sb, n <= 99999999 ? spellNumber(n) : nm.group());
        }
        nm.appendTail(sb);

        return sb.toString();
    }

    static String spellNumber(int n) {
        if (n == 0) return "zero";
        if (n < 10) return ONES[n];
        if (n < 20) return TEENS[n - 10];
        if (n < 100) {
            int tens = n / 10;
            int ones = n % 10;
            return ones == 0 ? TENS[tens] : TENS[tens] + " " + ONES[ones];
        }
        if (n < 1000) {
            int hundreds = n / 100;
            int rest = n % 100;
            return rest == 0 ? ONES[hundreds] + " hundred"
                    : ONES[hundreds] + " hundred " + spellNumber(rest);
        }
        if (n < 1000000) {
            int thousands = n / 1000;
            int rest = n % 1000;
            return rest == 0 ? spellNumber(thousands) + " thousand"
                    : spellNumber(thousands) + " thousand " + spellNumber(rest);
        }
        if (n < 100000000) {
            int millions = n / 1000000;
            int rest = n % 1000000;
            return rest == 0 ? spellNumber(millions) + " million"
                    : spellNumber(millions) + " million " + spellNumber(rest);
        }
        return String.valueOf(n);
    }

    // ── Sentence Boundary Detection ──────────────────────────

    /**
     * Index of the last configured hard sentence terminator, or -1.
     */
    private int findLastHardBoundary() {
        for (int i = buffer.length() - 1; i >= 0; i--) {
            char ch = buffer.charAt(i);
            if (hardTerminators.contains(String.valueOf(ch))) {
                return i;
            }
            // Double newline (paragraph boundary)
            if (ch == '\n' && i > 0 && buffer.charAt(i - 1) == '\n') {
                if (i < 2 || buffer.charAt(i - 2) != '\n') {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Last soft terminator index (comma/semicolon fallback), or -1.
     */
    private int findLastSoftBoundary() {
        for (int i = buffer.length() - 1; i >= 0; i--) {
            if (softTerminators.contains(String.valueOf(buffer.charAt(i)))) {
                return i;
            }
        }
        return -1;
    }

    // ── Static helpers (exposed for testing) ──────────────────

    static int findLastHardBoundary(String text) {
        for (int i = text.length() - 1; i >= 0; i--) {
            char ch = text.charAt(i);
            if (DEFAULT_HARD_TERMINATORS.contains(String.valueOf(ch))) {
                return i;
            }
            if (ch == '\n' && i > 0 && text.charAt(i - 1) == '\n') {
                if (i < 2 || text.charAt(i - 2) != '\n') {
                    return i;
                }
            }
        }
        return -1;
    }

    static int findLastSoftBoundary(String text, Set<String> softTerminators) {
        for (int i = text.length() - 1; i >= 0; i--) {
            if (softTerminators.contains(String.valueOf(text.charAt(i)))) {
                return i;
            }
        }
        return -1;
    }
}
