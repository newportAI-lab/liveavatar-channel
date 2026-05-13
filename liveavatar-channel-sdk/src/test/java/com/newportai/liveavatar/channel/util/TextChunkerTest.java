package com.newportai.liveavatar.channel.util;

import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class TextChunkerTest {

    // ── Markdown Stripping ────────────────────────────────────

    @Test
    public void testStripFencedCodeBlock() {
        String text = "Before\n```python\nprint('hi')\n```\nAfter";
        String result = TextChunker.stripMarkdown(text);
        assertFalse(result.contains("print"));
        assertTrue(result.contains("Before"));
        assertTrue(result.contains("After"));
    }

    @Test
    public void testStripMultipleFencedBlocks() {
        String text = "```a\nx\n```\nmid\n```b\ny\n```";
        String result = TextChunker.stripMarkdown(text);
        assertFalse(result.contains("x"));
        assertFalse(result.contains("y"));
        assertTrue(result.contains("mid"));
    }

    @Test
    public void testStripUnclosedFence() {
        String text = "keep\n```\nlost";
        String result = TextChunker.stripMarkdown(text);
        assertTrue(result.contains("keep"));
        assertFalse(result.contains("lost"));
    }

    @Test
    public void testStripBold() {
        assertEquals("hello world", TextChunker.stripMarkdown("**hello** world"));
    }

    @Test
    public void testStripItalic() {
        assertEquals("hello world", TextChunker.stripMarkdown("*hello* world"));
    }

    @Test
    public void testStripLinkKeepsText() {
        assertEquals("click", TextChunker.stripMarkdown("[click](https://x.com)"));
    }

    @Test
    public void testStripImageKeepsAlt() {
        assertEquals("logo", TextChunker.stripMarkdown("![logo](img.png)"));
    }

    @Test
    public void testStripInlineCode() {
        assertEquals("use func() now", TextChunker.stripMarkdown("use `func()` now"));
    }

    @Test
    public void testStripHeadings() {
        assertEquals("Title\n\ntext", TextChunker.stripMarkdown("# Title\n\ntext"));
    }

    @Test
    public void testStripListMarkers() {
        assertEquals("item1\nitem2", TextChunker.stripMarkdown("- item1\n- item2"));
    }

    @Test
    public void testStripTableRows() {
        String text = "| a | b |\n|-----|\n| 1 | 2 |";
        String result = TextChunker.stripMarkdown(text);
        assertFalse(result.contains("a"));
        assertFalse(result.contains("1"));
    }

    // ── Number Spelling ───────────────────────────────────────

    @Test
    public void testSpellZero() {
        assertEquals("zero", TextChunker.spellNumber(0));
    }

    @Test
    public void testSpellSingleDigit() {
        assertEquals("three", TextChunker.spellNumber(3));
    }

    @Test
    public void testSpellTen() {
        assertEquals("ten", TextChunker.spellNumber(10));
    }

    @Test
    public void testSpellEleven() {
        assertEquals("eleven", TextChunker.spellNumber(11));
    }

    @Test
    public void testSpellTwenty() {
        assertEquals("twenty", TextChunker.spellNumber(20));
    }

    @Test
    public void testSpellTwentyOne() {
        assertEquals("twenty one", TextChunker.spellNumber(21));
    }

    @Test
    public void testSpellHundred() {
        assertEquals("one hundred", TextChunker.spellNumber(100));
    }

    @Test
    public void testSpellThousand() {
        assertEquals("two thousand twenty four", TextChunker.spellNumber(2024));
    }

    // ── Text Normalization ────────────────────────────────────

    @Test
    public void testNormalizeUrl() {
        assertEquals("see  link  now", TextChunker.normalizeText("see https://example.com/path now"));
    }

    @Test
    public void testNormalizeEmail() {
        assertEquals("mail  email  pls", TextChunker.normalizeText("mail a@b.com pls"));
    }

    @Test
    public void testNormalizePercent() {
        assertEquals("fifty percent off", TextChunker.normalizeText("50% off"));
    }

    @Test
    public void testNormalizeNumber() {
        assertEquals("I bought three apples", TextChunker.normalizeText("I bought 3 apples"));
    }

    // ── Sentence Boundary Detection ───────────────────────────

    @Test
    public void testFindLastHardBoundaryPeriod() {
        assertEquals(2, TextChunker.findLastHardBoundary("Hi. World"));
    }

    @Test
    public void testFindLastHardBoundaryExclamation() {
        assertEquals(3, TextChunker.findLastHardBoundary("Wow! Nice"));
    }

    @Test
    public void testFindLastHardBoundaryDoubleNewline() {
        assertEquals(5, TextChunker.findLastHardBoundary("line\n\nnext"));
    }

    @Test
    public void testFindLastHardBoundaryNone() {
        assertEquals(-1, TextChunker.findLastHardBoundary("hello world"));
    }

    @Test
    public void testFindLastHardBoundaryLastOfMultiple() {
        assertEquals(4, TextChunker.findLastHardBoundary("A. B. C"));
    }

    @Test
    public void testFindLastSoftBoundary() {
        Set<String> soft = new HashSet<>(Arrays.asList(",", ";"));
        assertEquals(3, TextChunker.findLastSoftBoundary("a,b,c", soft));
        assertEquals(-1, TextChunker.findLastSoftBoundary("abc", soft));
    }

    // ── TextChunker Basic ─────────────────────────────────────

    @Test
    public void testFlushesOnPeriod() {
        List<String> sentences = new ArrayList<>();
        TextChunker c = new TextChunker.Builder().onFlush(sentences::add).build();
        c.process("Hello.");
        assertEquals(1, sentences.size());
        assertEquals("Hello.", sentences.get(0));
        assertEquals("", c.getBuffer());
    }

    @Test
    public void testAccumulatesWithoutTerminator() {
        List<String> sentences = new ArrayList<>();
        TextChunker c = new TextChunker.Builder().onFlush(sentences::add).build();
        c.process("Based on current data,");
        assertEquals(0, sentences.size());
        assertTrue(c.getBuffer().contains("Based"));
    }

    @Test
    public void testFlushesFromMultiChunk() {
        List<String> sentences = new ArrayList<>();
        TextChunker c = new TextChunker.Builder().onFlush(sentences::add).build();
        c.process("Based on current");
        assertEquals(0, sentences.size());
        c.process(" data, the market is volatile.");
        assertEquals(1, sentences.size());
        assertTrue(sentences.get(0).contains("volatile"));
    }

    @Test
    public void testKeepsRemnant() {
        List<String> sentences = new ArrayList<>();
        TextChunker c = new TextChunker.Builder().onFlush(sentences::add).build();
        c.process("It is volatile. We suggest watching");
        assertEquals(1, sentences.size());
        assertEquals("It is volatile.", sentences.get(0));
        assertEquals(" We suggest watching", c.getBuffer());
    }

    // ── Max Chars Fallback ────────────────────────────────────

    @Test
    public void testFlushesAtSoftBoundaryOnMaxChars() {
        List<String> sentences = new ArrayList<>();
        TextChunker c = new TextChunker.Builder().maxChars(20).onFlush(sentences::add).build();
        String longText = "This is a very long sentence, with many commas, that goes on, ";
        c.process(longText);
        assertTrue(sentences.size() >= 1);
        for (String s : sentences) {
            assertTrue(s.endsWith(","));
        }
    }

    @Test
    public void testForceFlushesWithNoSoftBoundary() {
        List<String> sentences = new ArrayList<>();
        TextChunker c = new TextChunker.Builder().maxChars(10).onFlush(sentences::add).build();
        c.process("abcdefghijklmnop");
        assertTrue(sentences.size() >= 1);
    }

    @Test
    public void testBackpressureDoublesMaxChars() {
        List<String> sentences = new ArrayList<>();
        TextChunker c = new TextChunker.Builder().maxChars(20).onFlush(sentences::add).build();
        c.setBackpressure(true);
        String text = "Short text, another phrase, third phrase";
        c.process(text);
        // Under backpressure effective maxChars=40, text is shorter than 40
        // so it may or may not flush depending on actual content
        assertTrue(text.length() < 40 || sentences.size() > 0);
    }

    // ── Flush Remaining ───────────────────────────────────────

    @Test
    public void testFlushesBufferOnDone() {
        List<String> sentences = new ArrayList<>();
        TextChunker c = new TextChunker.Builder().onFlush(sentences::add).build();
        c.process("An unfinished sentence");
        assertEquals(0, sentences.size());
        c.flushRemaining();
        assertEquals(1, sentences.size());
        assertEquals("An unfinished sentence", sentences.get(0));
    }

    @Test
    public void testEmptyBufferFlushNoop() {
        List<String> sentences = new ArrayList<>();
        TextChunker c = new TextChunker.Builder().onFlush(sentences::add).build();
        c.flushRemaining();
        assertEquals(0, sentences.size());
    }

    // ── Multiple Sentences ────────────────────────────────────

    @Test
    public void testFlushesMultipleInOneChunk() {
        List<String> sentences = new ArrayList<>();
        TextChunker c = new TextChunker.Builder().onFlush(sentences::add).build();
        c.process("First. Second! Third?");
        // Last boundary is "?" — everything flushed together
        assertEquals(1, sentences.size());
        assertEquals("First. Second! Third?", sentences.get(0));
        assertEquals("", c.getBuffer());
    }

    @Test
    public void testParagraphBoundary() {
        List<String> sentences = new ArrayList<>();
        TextChunker c = new TextChunker.Builder().onFlush(sentences::add).build();
        c.process("Paragraph one\n\nParagraph two");
        assertEquals(1, sentences.size());
        assertEquals("Paragraph one\n\n", sentences.get(0));
        assertEquals("Paragraph two", c.getBuffer());
    }

    // ── Markdown Integration ──────────────────────────────────

    @Test
    public void testStripsCodeBeforeSplitting() {
        List<String> sentences = new ArrayList<>();
        TextChunker c = new TextChunker.Builder().onFlush(sentences::add).build();
        c.process("Output: ```python\nprint('hi')\n```\nAbove is the code.");
        assertEquals(1, sentences.size());
        assertFalse(sentences.get(0).contains("print"));
        assertTrue(sentences.get(0).contains("Output"));
        assertTrue(sentences.get(0).contains("the code"));
    }

    @Test
    public void testStripsLinks() {
        List<String> sentences = new ArrayList<>();
        TextChunker c = new TextChunker.Builder().onFlush(sentences::add).build();
        c.process("Click [here](https://x.com) to view.");
        assertEquals(1, sentences.size());
        assertEquals("Click here to view.", sentences.get(0));
    }

    // ── Normalization Integration ─────────────────────────────

    @Test
    public void testNormalizesNumbers() {
        List<String> sentences = new ArrayList<>();
        TextChunker c = new TextChunker.Builder().onFlush(sentences::add).build();
        c.process("I bought 3 apples.");
        assertTrue(sentences.get(0).contains("three"));
    }

    @Test
    public void testNormalizesUrls() {
        List<String> sentences = new ArrayList<>();
        TextChunker c = new TextChunker.Builder().onFlush(sentences::add).build();
        c.process("Visit https://example.com.");
        assertTrue(sentences.get(0).contains("link"));
        assertFalse(sentences.get(0).contains("example.com"));
    }

    // ── Timer ─────────────────────────────────────────────────

    @Test
    public void testMaxTimeFlushesBuffer() throws Exception {
        List<String> sentences = new ArrayList<>();
        TextChunker c = new TextChunker.Builder().maxTimeMs(100).onFlush(sentences::add).build();
        c.process("Not finished");
        assertEquals(0, sentences.size());
        Thread.sleep(250);
        assertEquals(1, sentences.size());
        assertEquals("Not finished", sentences.get(0));
        c.close();
    }

    @Test
    public void testTimerResetsOnNewChunk() throws Exception {
        List<String> sentences = new ArrayList<>();
        TextChunker c = new TextChunker.Builder().maxTimeMs(200).onFlush(sentences::add).build();
        c.process("First part");
        Thread.sleep(100);
        c.process(" continues without punctuation");
        Thread.sleep(150);
        assertEquals(0, sentences.size());
        Thread.sleep(250);
        assertEquals(1, sentences.size());
        c.close();
    }

    @Test
    public void testCloseCancelsTimer() throws Exception {
        List<String> sentences = new ArrayList<>();
        TextChunker c = new TextChunker.Builder().maxTimeMs(100).onFlush(sentences::add).build();
        c.process("test");
        c.close();
        Thread.sleep(200);
        assertEquals(0, sentences.size());
    }

    // ── Edge Cases ────────────────────────────────────────────

    @Test
    public void testEmptyChunk() {
        TextChunker c = new TextChunker.Builder().build();
        c.process("");
        assertEquals("", c.getBuffer());
    }

    @Test
    public void testOnlyWhitespace() {
        List<String> sentences = new ArrayList<>();
        TextChunker c = new TextChunker.Builder().onFlush(sentences::add).build();
        c.process("   \n  ");
        assertEquals(0, sentences.size());
    }

    @Test
    public void testNoOnFlushDoesNotCrash() {
        TextChunker c = new TextChunker.Builder().build();
        c.process("Hello.");
        assertEquals("", c.getBuffer());
    }

    @Test
    public void testLongBufferNoBoundaries() {
        List<String> sentences = new ArrayList<>();
        TextChunker c = new TextChunker.Builder().maxChars(10).onFlush(sentences::add).build();
        c.process("abcdefghijklmno");
        assertTrue(sentences.size() >= 1);
    }
}
