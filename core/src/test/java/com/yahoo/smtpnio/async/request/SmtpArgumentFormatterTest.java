/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.nio.charset.StandardCharsets;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Unit test for {@link SmtpArgumentFormatter}.
 */
public class SmtpArgumentFormatterTest {

    /** The formatter under test, stateless so one instance serves every test. */
    private final SmtpArgumentFormatter formatter = new SmtpArgumentFormatter();

    /**
     * @return control characters that SMTP cannot represent in any argument position
     */
    @DataProvider(name = "controlChars")
    public Object[][] controlChars() {
        return new Object[][] {
                { "\r" },  // CR, terminates the command line
                { "\n" },  // LF, terminates the command line
                { "\0" },  // NUL, field separator of the AUTH PLAIN payload
                { "\u0001" },  // SOH, field separator of the XOAUTH2 payload
                { "\u001F" },  // the last C0 control character
                { "\u007F" },  // DEL
        };
    }

    /**
     * Tests that a well-formed argument is written through unchanged, there is nothing for SMTP to encode.
     *
     * @throws SmtpAsyncClientException will not throw
     */
    @Test
    public void testWellFormedArgumentWrittenUnchanged() throws SmtpAsyncClientException {
        final ByteBuf out = Unpooled.buffer();
        formatter.formatArgument("user1.test@yahoo.com", out, "sender");
        Assert.assertEquals(out.toString(StandardCharsets.US_ASCII), "user1.test@yahoo.com", "Encoded result mismatched.");

        final StringBuilder sb = new StringBuilder();
        formatter.formatArgument("user1.test@yahoo.com", sb, "sender");
        Assert.assertEquals(sb.toString(), "user1.test@yahoo.com", "Encoded result mismatched.");
    }

    /**
     * Tests that characters SMTP allows but that look special elsewhere are still written through, in particular the quote and
     * backslash of a quoted local part, which this formatter must not escape.
     *
     * @throws SmtpAsyncClientException will not throw
     */
    @Test
    public void testSpecialButLegalCharsWrittenUnchanged() throws SmtpAsyncClientException {
        final ByteBuf out = Unpooled.buffer();
        formatter.formatArgument("\"odd\\name\"@yahoo.com", out, "sender");
        Assert.assertEquals(out.toString(StandardCharsets.US_ASCII), "\"odd\\name\"@yahoo.com", "Encoded result mismatched.");
    }

    /**
     * Tests that an empty argument is accepted, MAIL FROM:&lt;&gt; is the legitimate null reverse-path.
     *
     * @throws SmtpAsyncClientException will not throw
     */
    @Test
    public void testEmptyArgumentAccepted() throws SmtpAsyncClientException {
        final ByteBuf out = Unpooled.buffer();
        formatter.formatArgument("", out, "sender");
        Assert.assertEquals(out.toString(StandardCharsets.US_ASCII), "", "Encoded result mismatched.");
    }

    /**
     * Tests that a control character is refused rather than written, on both overloads.
     *
     * @param controlChar the control character under test
     */
    @Test(dataProvider = "controlChars")
    public void testControlCharRefused(final String controlChar) {
        final ByteBuf out = Unpooled.buffer();
        final SmtpAsyncClientException bufError = Assert.expectThrows(SmtpAsyncClientException.class,
                () -> formatter.formatArgument("user" + controlChar + "@yahoo.com", out, "sender"));
        Assert.assertEquals(bufError.getFailureType(), SmtpAsyncClientException.FailureType.INVALID_INPUT, "Expected results mismatched");

        final StringBuilder sb = new StringBuilder();
        final SmtpAsyncClientException sbError = Assert.expectThrows(SmtpAsyncClientException.class,
                () -> formatter.formatArgument("user" + controlChar + "@yahoo.com", sb, "sender"));
        Assert.assertEquals(sbError.getFailureType(), SmtpAsyncClientException.FailureType.INVALID_INPUT, "Expected results mismatched");
    }

    /**
     * @return characters above the ASCII range, which RFC 5321 section 4.1.2 forbids in a command argument
     */
    @DataProvider(name = "nonAsciiChars")
    public Object[][] nonAsciiChars() {
        return new Object[][] {
                { "" },  // first character with the high order bit set
                { "é" },  // e with acute, a plausible accident in a name
                { "中" },  // outside latin-1 entirely
        };
    }

    /**
     * Tests that a non-ASCII character is refused rather than silently flattened to '?' by the US-ASCII encoding.
     *
     * @param nonAsciiChar the character under test
     */
    @Test(dataProvider = "nonAsciiChars")
    public void testNonAsciiCharRefused(final String nonAsciiChar) {
        final ByteBuf out = Unpooled.buffer();
        final SmtpAsyncClientException e = Assert.expectThrows(SmtpAsyncClientException.class,
                () -> formatter.formatArgument("user" + nonAsciiChar + "@yahoo.com", out, "sender"));
        Assert.assertEquals(e.getFailureType(), SmtpAsyncClientException.FailureType.INVALID_INPUT, "Expected results mismatched");
        Assert.assertTrue(e.getMessage().contains("non-ASCII"), "The message should say the character is non-ASCII");
        Assert.assertEquals(out.readableBytes(), 0, "Nothing should have been written");
    }

    /**
     * Tests that the printable ASCII boundaries themselves are accepted, SPACE and tilde are both legal in a quoted local part.
     *
     * @throws SmtpAsyncClientException will not throw
     */
    @Test
    public void testPrintableBoundariesAccepted() throws SmtpAsyncClientException {
        final ByteBuf out = Unpooled.buffer();
        formatter.formatArgument("\"odd name\"@yahoo.com", out, "sender");
        Assert.assertEquals(out.toString(StandardCharsets.US_ASCII), "\"odd name\"@yahoo.com", "Encoded result mismatched.");

        final ByteBuf tilde = Unpooled.buffer();
        formatter.formatArgument("~user@yahoo.com", tilde, "sender");
        Assert.assertEquals(tilde.toString(StandardCharsets.US_ASCII), "~user@yahoo.com", "Encoded result mismatched.");
    }

    /**
     * Tests the esmtp-keyword production, (ALPHA / DIGIT) *(ALPHA / DIGIT / "-").
     *
     * @throws SmtpAsyncClientException will not throw for the accepted keywords
     */
    @Test
    public void testEsmtpKeyword() throws SmtpAsyncClientException {
        final ByteBuf out = Unpooled.buffer();
        formatter.formatEsmtpKeyword("SIZE", out, "mail parameter keyword");
        Assert.assertEquals(out.toString(StandardCharsets.US_ASCII), "SIZE", "Encoded result mismatched.");

        final ByteBuf hyphenated = Unpooled.buffer();
        formatter.formatEsmtpKeyword("X-CUSTOM-1", hyphenated, "mail parameter keyword");
        Assert.assertEquals(hyphenated.toString(StandardCharsets.US_ASCII), "X-CUSTOM-1", "Encoded result mismatched.");

        // a leading hyphen, an inner space and an empty keyword are all outside the production
        for (final String bad : new String[] { "-LEADING", "TWO WORDS", "UNDER_SCORE", "" }) {
            final SmtpAsyncClientException e = Assert.expectThrows(SmtpAsyncClientException.class,
                    () -> formatter.formatEsmtpKeyword(bad, Unpooled.buffer(), "mail parameter keyword"));
            Assert.assertEquals(e.getFailureType(), SmtpAsyncClientException.FailureType.INVALID_INPUT, "Expected results mismatched");
        }
    }

    /**
     * Tests the esmtp-value production, 1*(%d33-60 / %d62-126), which excludes SPACE and the equals sign.
     *
     * @throws SmtpAsyncClientException will not throw for the accepted values
     */
    @Test
    public void testEsmtpValue() throws SmtpAsyncClientException {
        final ByteBuf out = Unpooled.buffer();
        formatter.formatEsmtpValue("1000", out, "mail parameter value");
        Assert.assertEquals(out.toString(StandardCharsets.US_ASCII), "1000", "Encoded result mismatched.");

        // the xtext form RFC 5321 directs callers to use for a mailbox valued parameter
        final ByteBuf xtext = Unpooled.buffer();
        formatter.formatEsmtpValue("rfc822;user+2Btag@yahoo.com", xtext, "mail parameter value");
        Assert.assertEquals(xtext.toString(StandardCharsets.US_ASCII), "rfc822;user+2Btag@yahoo.com", "Encoded result mismatched.");

        // a space would begin a further esmtp-param, an equals sign would read as the keyword separator
        for (final String bad : new String[] { "two words", "a=b", "" }) {
            final SmtpAsyncClientException e = Assert.expectThrows(SmtpAsyncClientException.class,
                    () -> formatter.formatEsmtpValue(bad, Unpooled.buffer(), "mail parameter value"));
            Assert.assertEquals(e.getFailureType(), SmtpAsyncClientException.FailureType.INVALID_INPUT, "Expected results mismatched");
        }
    }

    /**
     * Tests that the error names the argument and its offending index, but does not echo the value, which may be a credential.
     */
    @Test
    public void testErrorNamesArgumentWithoutEchoingIt() {
        final StringBuilder sb = new StringBuilder();
        final SmtpAsyncClientException e = Assert.expectThrows(SmtpAsyncClientException.class,
                () -> formatter.formatArgument("s3cret\r\nRSET", sb, "password"));
        Assert.assertFalse(e.getMessage().contains("s3cret"), "The rejected value must not appear in the message");
        Assert.assertTrue(e.getMessage().contains("password"), "The message should name the offending argument");
        Assert.assertTrue(e.getMessage().contains("index 6"), "The message should give the offending index");
    }

    /**
     * Tests that nothing is appended to the output when the argument is refused, a partially written argument must not survive.
     */
    @Test
    public void testNothingWrittenWhenRefused() {
        final StringBuilder sb = new StringBuilder();
        Assert.expectThrows(SmtpAsyncClientException.class, () -> formatter.formatArgument("bad\r\nRSET", sb, "sender"));
        Assert.assertEquals(sb.length(), 0, "Nothing should have been appended");

        final ByteBuf out = Unpooled.buffer();
        Assert.expectThrows(SmtpAsyncClientException.class, () -> formatter.formatArgument("bad\r\nRSET", out, "sender"));
        Assert.assertEquals(out.readableBytes(), 0, "Nothing should have been written");
    }
}
