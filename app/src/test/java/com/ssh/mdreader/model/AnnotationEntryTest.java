package com.ssh.mdreader.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * JVM unit tests for {@link AnnotationEntry} CSV parsing / serialising.
 */
public class AnnotationEntryTest {

    @Test
    public void parseFourColumns() {
        AnnotationEntry e = AnnotationEntry.parse("\"id1\",\"内容\",\"片段\",3");
        assertNotNull(e);
        assertEquals("id1", e.id);
        assertEquals("内容", e.text);
        assertEquals("片段", e.originalText);
        assertEquals(3, e.occurrenceIndex);
    }

    @Test
    public void parseLegacyThreeColumns() {
        AnnotationEntry e = AnnotationEntry.parse("\"id2\",\"内容\",\"片段\"");
        assertNotNull(e);
        assertEquals(0, e.occurrenceIndex);
    }

    @Test
    public void parseSkipsLegacyLineColFormat() {
        assertNull(AnnotationEntry.parse("L3:5-12"));
    }

    @Test
    public void parseEmptyIdReturnsNull() {
        assertNull(AnnotationEntry.parse("\"\",\"内容\",\"片段\",0"));
    }

    @Test
    public void parseInvalidOccurrenceFallsBackToZero() {
        AnnotationEntry e = AnnotationEntry.parse("\"id3\",\"内容\",\"片段\",abc");
        assertNotNull(e);
        assertEquals(0, e.occurrenceIndex);
    }

    @Test
    public void parseHandlesEscapedQuotes() {
        AnnotationEntry e = AnnotationEntry.parse("\"id4\",\"说\"\"你好\"\"\",\"片段\",0");
        assertNotNull(e);
        assertEquals("说\"你好\"", e.text);
    }

    @Test
    public void parseHandlesUnquotedFields() {
        AnnotationEntry e = AnnotationEntry.parse("id5,text,original,2");
        assertNotNull(e);
        assertEquals("id5", e.id);
        assertEquals("text", e.text);
        assertEquals(2, e.occurrenceIndex);
    }

    @Test
    public void formatQuotesAndEscapes() {
        AnnotationEntry e = new AnnotationEntry("id6", "a\"b,c", "orig", 1);
        assertEquals("\"id6\",\"a\"\"b,c\",\"orig\",1", e.format());
    }

    @Test
    public void formatEscapesNewlineAsLiteralTwoChars() {
        AnnotationEntry e = new AnnotationEntry("id8", "a\nb", "orig", 0);
        // CSV must contain the two-char literal \n, not a real newline
        assertEquals("\"id8\",\"a\\nb\",\"orig\",0", e.format());
    }

    @Test
    public void backslashRoundTrips() {
        AnnotationEntry e = new AnnotationEntry("id9", "C:\\notes\\a", "orig", 0);
        AnnotationEntry back = AnnotationEntry.parse(e.format());
        assertNotNull(back);
        assertEquals("C:\\notes\\a", back.text);
    }

    @Test
    public void parseFormatRoundTrip() {
        AnnotationEntry e = new AnnotationEntry("id7", "批注内容", "原文<<>>", 4);
        AnnotationEntry back = AnnotationEntry.parse(e.format());
        assertNotNull(back);
        assertEquals(e.id, back.id);
        assertEquals(e.text, back.text);
        assertEquals(e.originalText, back.originalText);
        assertEquals(e.occurrenceIndex, back.occurrenceIndex);
    }
}
