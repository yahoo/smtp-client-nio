/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;

import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;

import io.netty.buffer.ByteBuf;

/**
 * This class writes a caller-supplied SMTP command argument out to the command line being built, refusing any argument that
 * RFC 5321 section 4.1.2 cannot represent.
 *
 * <p>
 * Unlike IMAP, SMTP offers no quoting, escaping or literal syntax able to carry a character outside the printable US-ASCII
 * range. {@code qtextSMTP} is %d32-33 / %d35-91 / %d93-126 and {@code quoted-pairSMTP} is %d92 %d32-126, so even the
 * Quoted-string form of a Local-part reaches no further than %d126, and section 4.1.2 states that non-ASCII characters
 * (octets with the high order bit set to one) and ASCII control characters (decimal 0-31 and 127) "MUST NOT be used in MAIL
 * or RCPT commands or other commands that require mailbox names". There is consequently nothing to encode: a well-formed
 * argument is written through unchanged, and the only alternative to writing it is refusing to build the command.
 * </p>
 *
 * <p>
 * Refusing those characters is also what keeps an argument from altering the structure of the line it is written into. A CR
 * or LF terminates the command line and turns the remainder of the argument into an additional command of the caller's
 * choosing, while NUL and SOH are field separators inside the SASL payloads used by the AUTH commands.
 * </p>
 */
public class SmtpArgumentFormatter {

    /** Lowest code point SMTP can carry in a command argument, ie. SPACE. */
    private static final char FIRST_PRINTABLE_CHAR = 0x20;

    /** Highest code point SMTP can carry in a command argument, ie. the last ASCII graphic. */
    private static final char LAST_PRINTABLE_CHAR = 0x7E;

    /** Highest code point still considered an ASCII control character, ie. the last character before SPACE. */
    private static final char LAST_CONTROL_CHAR = 0x1F;

    /** The DEL control character. */
    private static final char DEL_CHAR = 0x7F;

    /**
     * Writes the given argument to the given buffer, refusing it when it carries a character SMTP cannot represent.
     *
     * @param src the argument to write
     * @param out the {@link ByteBuf} to write to
     * @param name the name of the argument, used in the error message
     * @throws SmtpAsyncClientException when the argument contains a control or non-ASCII character
     */
    void formatArgument(@Nonnull final String src, @Nonnull final ByteBuf out, @Nonnull final String name) throws SmtpAsyncClientException {
        ensurePrintableAscii(src, name);
        out.writeBytes(src.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Appends the given argument to the given builder, refusing it when it carries a character SMTP cannot represent.
     *
     * <p>
     * This overload serves the AUTH commands, which assemble a SASL payload as a string before encoding it into base64.
     * </p>
     *
     * @param src the argument to append
     * @param out the {@link StringBuilder} to append to
     * @param name the name of the argument, used in the error message
     * @throws SmtpAsyncClientException when the argument contains a control or non-ASCII character
     */
    void formatArgument(@Nonnull final String src, @Nonnull final StringBuilder out, @Nonnull final String name) throws SmtpAsyncClientException {
        ensurePrintableAscii(src, name);
        out.append(src);
    }

    /**
     * Writes the given esmtp-keyword to the given buffer, refusing it when it does not match
     * {@code esmtp-keyword = (ALPHA / DIGIT) *(ALPHA / DIGIT / "-")}.
     *
     * @param src the keyword to write
     * @param out the {@link ByteBuf} to write to
     * @param name the name of the argument, used in the error message
     * @throws SmtpAsyncClientException when the keyword is empty or holds a character outside the production
     */
    void formatEsmtpKeyword(@Nonnull final String src, @Nonnull final ByteBuf out, @Nonnull final String name) throws SmtpAsyncClientException {
        if (src.isEmpty()) {
            throw invalidInput(new StringBuilder("The ").append(name)
                    .append(" argument is empty, an esmtp-keyword requires at least one character."));
        }
        for (int i = 0; i < src.length(); i++) {
            final char c = src.charAt(i);
            final boolean alphaNumeric = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
            // a hyphen is permitted everywhere except the first position
            if (!alphaNumeric && !(c == '-' && i > 0)) {
                throw invalidInput(new StringBuilder("The ").append(name)
                        .append(" argument is not a valid esmtp-keyword, offending character (0x").append(Integer.toHexString(c))
                        .append(") at index ").append(i).append('.'));
            }
        }
        out.writeBytes(src.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Writes the given esmtp-value to the given buffer, refusing it when it does not match
     * {@code esmtp-value = 1*(%d33-60 / %d62-126)}, ie. any character other than "=", SPACE and the control characters.
     *
     * <p>
     * A SPACE would begin a further esmtp-param and an "=" would be read as the keyword/value separator, so both have to be
     * refused here. RFC 5321 directs callers whose value is itself a mailbox to encode it as xtext, which this rule permits.
     * </p>
     *
     * @param src the value to write
     * @param out the {@link ByteBuf} to write to
     * @param name the name of the argument, used in the error message
     * @throws SmtpAsyncClientException when the value is empty or holds a character outside the production
     */
    void formatEsmtpValue(@Nonnull final String src, @Nonnull final ByteBuf out, @Nonnull final String name) throws SmtpAsyncClientException {
        if (src.isEmpty()) {
            throw invalidInput(new StringBuilder("The ").append(name).append(" argument is empty, an esmtp-value requires at least one character."));
        }
        ensurePrintableAscii(src, name);
        for (int i = 0; i < src.length(); i++) {
            final char c = src.charAt(i);
            if (c == FIRST_PRINTABLE_CHAR || c == '=') {
                throw invalidInput(new StringBuilder("The ").append(name)
                        .append(" argument is not a valid esmtp-value, it may not contain a space or an equals sign, found (0x")
                        .append(Integer.toHexString(c)).append(") at index ").append(i).append('.'));
            }
        }
        out.writeBytes(src.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Throws when the given argument holds a character outside the printable US-ASCII range that SMTP command arguments are
     * confined to by RFC 5321 section 4.1.2.
     *
     * @param src the argument to inspect
     * @param name the name of the argument, used in the error message
     * @throws SmtpAsyncClientException when the argument contains a control or non-ASCII character
     */
    private void ensurePrintableAscii(@Nonnull final String src, @Nonnull final String name) throws SmtpAsyncClientException {
        for (int i = 0; i < src.length(); i++) {
            final char c = src.charAt(i);
            if (c < FIRST_PRINTABLE_CHAR || c > LAST_PRINTABLE_CHAR) {
                // the offending value is deliberately excluded from the message, it may hold a credential
                final String kind = (c <= LAST_CONTROL_CHAR || c == DEL_CHAR) ? "control" : "non-ASCII";
                throw invalidInput(new StringBuilder("The ").append(name).append(" argument contains an illegal ").append(kind)
                        .append(" character (0x").append(Integer.toHexString(c)).append(") at index ").append(i).append('.'));
            }
        }
    }

    /**
     * Builds the exception reporting a refused argument.
     *
     * @param message the message describing the refusal
     * @return the exception to throw
     */
    private SmtpAsyncClientException invalidInput(@Nonnull final StringBuilder message) {
        return new SmtpAsyncClientException(SmtpAsyncClientException.FailureType.INVALID_INPUT, message.toString());
    }
}
