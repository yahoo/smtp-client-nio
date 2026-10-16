/*
 * Copyright Yahoo
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Unit test for {@link DotStuffingInputStream}.
 */
public class DotStuffingInputStreamTest {

    /**
     * Encodes the given message.
     *
     * @param message the message to encode
     * @return the encoded content
     * @throws IOException will not throw in this test
     */
    private static String encode(final String message) throws IOException {
        try (InputStream in = new DotStuffingInputStream(new ByteArrayInputStream(message.getBytes(StandardCharsets.ISO_8859_1)))) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[3];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            Assert.assertEquals(in.read(), -1, "Stream should stay at its end");
            return out.toString(StandardCharsets.ISO_8859_1.name());
        }
    }

    /**
     * Encodes the given message one {@code read()} at a time.
     *
     * @param message the message to encode
     * @return the encoded content
     * @throws IOException will not throw in this test
     */
    private static String encodeByByte(final String message) throws IOException {
        try (InputStream in = new DotStuffingInputStream(new ByteArrayInputStream(message.getBytes(StandardCharsets.ISO_8859_1)))) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1) {
                out.write(b);
            }
            Assert.assertEquals(in.read(new byte[1], 0, 1), -1, "Stream should stay at its end");
            return out.toString(StandardCharsets.ISO_8859_1.name());
        }
    }

    /**
     * Provides messages and their expected encoding.
     *
     * @return pairs of message and expected content
     */
    @DataProvider(name = "messages")
    public Object[][] messages() {
        return new Object[][] {
            // empty message: the indicator alone
            { "", ".\r\n" },
            // no trailing CRLF: one is added before the indicator
            { "hello", "hello\r\n.\r\n" },
            // trailing CRLF: no blank line is added
            { "hello\r\n", "hello\r\n.\r\n" },
            // a message ending in a blank line keeps it
            { "hello\r\n\r\n", "hello\r\n\r\n.\r\n" },
            // leading period on the first line
            { ".hello", "..hello\r\n.\r\n" },
            { ".", "..\r\n.\r\n" },
            // leading period in the middle of the message
            { "a\r\n.b\r\nc", "a\r\n..b\r\nc\r\n.\r\n" },
            // a period that does not start a line is left alone
            { "a.b\r\nc.\r\n", "a.b\r\nc.\r\n.\r\n" },
            // an embedded end of mail data indicator, with commands after it
            { "a\r\n.\r\nRSET\r\nMAIL FROM:<x@example.com>\r\n", "a\r\n..\r\nRSET\r\nMAIL FROM:<x@example.com>\r\n.\r\n" },
            { "a\r\n..\r\n", "a\r\n...\r\n.\r\n" },
            // bare LF becomes CRLF, so it cannot start an unstuffed line
            { "a\n.\nb", "a\r\n..\r\nb\r\n.\r\n" },
            { "a\r\n.\nb", "a\r\n..\r\nb\r\n.\r\n" },
            { "\n.", "\r\n..\r\n.\r\n" },
            // bare CR becomes CRLF, so it cannot start an unstuffed line
            { "a\r.\rb", "a\r\n..\r\nb\r\n.\r\n" },
            { "a\r\r\n.", "a\r\n\r\n..\r\n.\r\n" },
            { "a\r", "a\r\n.\r\n" },
            { "\r.", "\r\n..\r\n.\r\n" },
            // LF CR is two line endings
            { "a\n\r.", "a\r\n\r\n..\r\n.\r\n" },
            // octets above 127 pass through unchanged
            { "ÿ\u0080", "ÿ\u0080\r\n.\r\n" },
        };
    }

    /**
     * Tests the encoding of a message.
     *
     * @param message the message to encode
     * @param expected the expected content
     * @throws IOException will not throw in this test
     */
    @Test(dataProvider = "messages")
    public void testEncode(final String message, final String expected) throws IOException {
        Assert.assertEquals(encode(message), expected, "Wrong encoding");
        Assert.assertEquals(encodeByByte(message), expected, "Wrong encoding one byte at a time");
    }

    /**
     * Tests a message longer than the internal buffer.
     *
     * @throws IOException will not throw in this test
     */
    @Test
    public void testEncodeLongMessage() throws IOException {
        final StringBuilder message = new StringBuilder();
        final StringBuilder expected = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            message.append(".line\n");
            expected.append("..line\r\n");
        }
        expected.append(".\r\n");
        Assert.assertEquals(encode(message.toString()), expected.toString(), "Wrong encoding");
    }

    /**
     * Tests that a source read returning 0 bytes is not taken as the end.
     *
     * @throws IOException will not throw in this test
     */
    @Test
    public void testSourceReturnsNoBytes() throws IOException {
        final InputStream source = Mockito.mock(InputStream.class);
        Mockito.when(source.read(Mockito.any(byte[].class), Mockito.anyInt(), Mockito.anyInt())).thenReturn(0).thenAnswer(invocation -> {
            ((byte[]) invocation.getArguments()[0])[0] = 'a';
            return 1;
        }).thenReturn(-1);
        Assert.assertEquals(new String(readAll(new DotStuffingInputStream(source)), StandardCharsets.US_ASCII), "a\r\n.\r\n");
    }

    /**
     * Reads the given stream to its end.
     *
     * @param in the stream to read
     * @return the bytes read
     * @throws IOException will not throw in this test
     */
    private static byte[] readAll(final InputStream in) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final byte[] buffer = new byte[16];
        int n;
        while ((n = in.read(buffer, 0, buffer.length)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    /**
     * Tests that a read of no bytes returns 0, even at the end of the stream.
     *
     * @throws IOException will not throw in this test
     */
    @Test
    public void testReadNoBytes() throws IOException {
        final InputStream in = new DotStuffingInputStream(new ByteArrayInputStream(new byte[0]));
        Assert.assertEquals(in.read(new byte[1], 0, 0), 0, "Nothing should be read");
        readAll(in);
        Assert.assertEquals(in.read(new byte[1], 0, 0), 0, "Nothing should be read at the end");
    }

    /**
     * Provides offsets and lengths that do not fit a buffer of 4 bytes.
     *
     * @return pairs of offset and length
     */
    @DataProvider(name = "badRanges")
    public Object[][] badRanges() {
        return new Object[][] { { -1, 1 }, { 0, -1 }, { 2, 3 } };
    }

    /**
     * Tests that a range outside the buffer is refused.
     *
     * @param off the offset
     * @param len the length
     * @throws IOException will not throw in this test
     */
    @Test(dataProvider = "badRanges", expectedExceptions = IndexOutOfBoundsException.class)
    public void testReadBadRange(final int off, final int len) throws IOException {
        new DotStuffingInputStream(new ByteArrayInputStream(new byte[0])).read(new byte[4], off, len);
    }

    /**
     * Tests that closing the stream closes the message.
     *
     * @throws IOException will not throw in this test
     */
    @Test
    public void testClose() throws IOException {
        final InputStream source = Mockito.mock(InputStream.class);
        new DotStuffingInputStream(source).close();
        Mockito.verify(source, Mockito.times(1)).close();
    }

    /**
     * Tests that a failure reading the message is propagated.
     *
     * @throws IOException will not throw in this test
     */
    @Test
    public void testReadFails() throws IOException {
        final InputStream source = Mockito.mock(InputStream.class);
        Mockito.when(source.read(Mockito.any(byte[].class), Mockito.anyInt(), Mockito.anyInt())).thenThrow(new IOException("Fails"));
        final InputStream in = new DotStuffingInputStream(source);
        Assert.assertThrows(IOException.class, in::read);
    }
}
