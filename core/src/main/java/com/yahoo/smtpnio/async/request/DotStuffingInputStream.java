/*
 * Copyright Yahoo
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.Nonnull;

/**
 * This class encodes a message as DATA content, per RFC 5321 section 4.5.2:
 * <ul>
 * <li>A line starting with a period gets one more period in front, so the message cannot end the DATA content early.</li>
 * <li>A bare CR or bare LF is sent as CRLF, so it cannot start an unstuffed line.</li>
 * <li>CRLF "." CRLF is appended, without adding a second CRLF when the message already ends in one.</li>
 * </ul>
 */
final class DotStuffingInputStream extends InputStream {

    /** Value returned by {@code read} at the end of a stream. */
    private static final int END_OF_STREAM = -1;

    /** Carriage return. */
    private static final int CR = '\r';

    /** Line feed. */
    private static final int LF = '\n';

    /** Most bytes one source byte can produce: CRLF for a pending CR, then "." CRLF. */
    private static final int MAX_PENDING = 5;

    /** Number of bytes read from the message at a time. */
    private static final int SOURCE_BUFFER_LEN = 8192;

    /** The message to encode. */
    private final InputStream source;

    /** Bytes read from the message and not yet encoded. */
    private final byte[] sourceBuffer = new byte[SOURCE_BUFFER_LEN];

    /** Position of the next byte to encode in {@code sourceBuffer}. */
    private int sourcePos;

    /** Number of bytes held in {@code sourceBuffer}. */
    private int sourceLen;

    /** Encoded bytes not yet returned. */
    private final byte[] pending = new byte[MAX_PENDING];

    /** Position of the next byte to return in {@code pending}. */
    private int pendingPos;

    /** Number of bytes held in {@code pending}. */
    private int pendingLen;

    /** Whether the next source byte starts a line. */
    private boolean lineStart = true;

    /** Whether the last source byte was a CR. */
    private boolean crSeen;

    /** Whether the final "." CRLF has been produced. */
    private boolean ended;

    /**
     * Initializes a stream that encodes the given message.
     *
     * @param source the message to encode
     */
    DotStuffingInputStream(@Nonnull final InputStream source) {
        this.source = source;
    }

    @Override
    public int read() throws IOException {
        while (pendingPos == pendingLen) {
            if (ended) {
                return END_OF_STREAM;
            }
            pendingPos = 0;
            pendingLen = 0;
            encodeNext();
        }
        return pending[pendingPos++] & 0xFF;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException("Invalid offset " + off + " or length " + len + " for a buffer of " + b.length);
        }
        // bulk copy, instead of InputStream's one read() per byte
        int n = 0;
        while (n < len) {
            if (pendingPos < pendingLen) {
                b[off + n++] = pending[pendingPos++];
                continue;
            }
            if (ended) {
                break;
            }
            if (!lineStart && !crSeen) {
                // mid-line bytes need no encoding until the next CR or LF
                while (n < len && sourcePos < sourceLen && sourceBuffer[sourcePos] != CR && sourceBuffer[sourcePos] != LF) {
                    b[off + n++] = sourceBuffer[sourcePos++];
                }
                if (n == len) {
                    break;
                }
            }
            pendingPos = 0;
            pendingLen = 0;
            encodeNext();
        }
        return n == 0 && len > 0 ? END_OF_STREAM : n;
    }

    @Override
    public void close() throws IOException {
        source.close();
    }

    /**
     * Encodes the next message byte into {@code pending}.
     *
     * @throws IOException when reading the message fails
     */
    private void encodeNext() throws IOException {
        final int b = nextSourceByte();
        if (crSeen) {
            crSeen = false;
            endLine();
            if (b == LF) {
                return;
            }
        }
        if (b == END_OF_STREAM) {
            if (!lineStart) {
                endLine();
            }
            pending[pendingLen++] = SmtpClientConstants.PERIOD;
            endLine();
            ended = true;
        } else if (b == CR) {
            crSeen = true;
        } else if (b == LF) {
            endLine();
        } else {
            if (lineStart && b == SmtpClientConstants.PERIOD) {
                pending[pendingLen++] = SmtpClientConstants.PERIOD;
            }
            pending[pendingLen++] = (byte) b;
            lineStart = false;
        }
    }

    /**
     * Returns the next message byte, refilling {@code sourceBuffer} when empty.
     *
     * @return the next byte, or {@code END_OF_STREAM}
     * @throws IOException when reading the message fails
     */
    private int nextSourceByte() throws IOException {
        while (sourcePos == sourceLen) {
            final int n = source.read(sourceBuffer, 0, SOURCE_BUFFER_LEN);
            if (n == END_OF_STREAM) {
                return END_OF_STREAM;
            }
            sourcePos = 0;
            sourceLen = n;
        }
        return sourceBuffer[sourcePos++] & 0xFF;
    }

    /**
     * Ends the current line with CRLF.
     */
    private void endLine() {
        pending[pendingLen++] = CR;
        pending[pendingLen++] = LF;
        lineStart = true;
    }
}
