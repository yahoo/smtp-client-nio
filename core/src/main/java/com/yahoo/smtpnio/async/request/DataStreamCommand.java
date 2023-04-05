/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import com.yahoo.smtpnio.async.response.SmtpResponse;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.handler.stream.ChunkedStream;

/**
 * This class defines a Data (DATA) command.
 */
public class DataStreamCommand extends AbstractSmtpCommand {
    /** String literal for "DATA". */
    private static final String DATA = "DATA";
    private static final byte[] END_OF_INPUT = "\r\n.\r\n".getBytes(StandardCharsets.US_ASCII);

    /** The message to be sent. */
    private InputStream message;

    /**
     * Initializes a DATA command object that contains the message to be sent.
     *
     * @param message a {@link InputStream} representing the message
     */
    public DataStreamCommand(@Nonnull final InputStream message) {
        super(DATA);
        this.message = message;
    }

    @Override
    public boolean isCommandLineDataSensitive() {
        // prevents logging
        return true;
    }

    @Override
    public String getDebugData() {
        return "DATA stream";
    }

    InputStream getMessage() {
        return message;
    }

    @Override
    public ByteBuf getNextCommandLineAfterContinuation(@Nonnull final SmtpResponse serverResponse) {
        try {
            final byte[] bytes = toByteArray(message);
            return Unpooled.buffer(bytes.length + SmtpClientConstants.PADDING_LEN)
                .writeBytes(bytes)
                .writeBytes(SmtpClientConstants.CRLF.getBytes(StandardCharsets.US_ASCII))
                .writeByte(SmtpClientConstants.PERIOD)
                .writeBytes(SmtpClientConstants.CRLF.getBytes(StandardCharsets.US_ASCII));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private byte[] toByteArray(final InputStream inputStream) throws IOException {
        // We use a ThresholdingOutputStream to avoid reading AND writing more than Integer.MAX_VALUE.
        try (final ByteArrayOutputStream ubaOutput = new ByteArrayOutputStream()) {
            copy(inputStream, ubaOutput);
            return ubaOutput.toByteArray();
        }
    }

    private int copy(final InputStream inputStream, final OutputStream outputStream) throws IOException {
        return (int) copyLarge(inputStream, outputStream, new byte[1024]);
    }

    private long copyLarge(final InputStream inputStream, final OutputStream outputStream, final byte[] buffer) throws IOException {
        long count = 0;
        int n;
        while (-1 != (n = inputStream.read(buffer))) {
            outputStream.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    @Override
    public void encodeCommandAfterContinuation(final Channel channel, final Supplier<ChannelPromise> writeFuture, final SmtpResponse response) {
        channel.write(new ChunkedStream(message), writeFuture.get());
        channel.writeAndFlush(Unpooled.wrappedBuffer(END_OF_INPUT));
    }

    @Override
    public SmtpCommandType getCommandType() {
        return SmtpRFCSupportedCommandType.DATA;
    }

    @Override
    public void cleanup() {
        super.cleanup();
        try {
            message.close();
        } catch (IOException e) {
            // Do nothing
        }
        message = null;
    }
}
