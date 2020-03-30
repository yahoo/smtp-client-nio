/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.nio.charset.StandardCharsets;

import javax.annotation.Nonnull;

import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.response.SmtpResponse;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * This defines the base class that all SMTP commands inherit from.
 */
public abstract class AbstractSmtpCommand implements SmtpRequest {

    /** Constant for the CR and LF bytes. */
    protected static final byte[] CRLF_B = { '\r', '\n' };

    /** The SMTP command name. */
    protected String command;

    /**
     * Constructor for a SMTP command with no arguments.
     *
     * @param command the command string, eg. "QUIT"
     */
    protected AbstractSmtpCommand(@Nonnull final String command) {
        this.command = command;
    }

    @Override
    public void cleanup() {
        command = null;
    }

    /**
     * @return the type of command
     */
    @Override
    public abstract SmtpCommandType getCommandType();

    @Nonnull
    @Override
    public ByteBuf getCommandLineBytes() {
        return Unpooled.buffer(command.length() + CRLF_B.length)
                .writeBytes(command.getBytes(StandardCharsets.US_ASCII))
                .writeBytes(CRLF_B);
    }

    @Override
    public boolean isCommandLineDataSensitive() {
        return false;
    }

    @Nonnull
    @Override
    public String getDebugData() {
        return "";
    }

    @Override
    public ByteBuf getNextCommandLineAfterContinuation(@Nonnull final SmtpResponse serverResponse) throws SmtpAsyncClientException {
        throw new SmtpAsyncClientException(SmtpAsyncClientException.FailureType.OPERATION_NOT_SUPPORTED_FOR_COMMAND);
    }
}
