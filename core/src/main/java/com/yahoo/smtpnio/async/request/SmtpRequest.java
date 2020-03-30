/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.response.SmtpResponse;

import io.netty.buffer.ByteBuf;

/**
 * This interface defines a SMTP command sent from the client.
 */
public interface SmtpRequest {

    /**
     * @return true if the command line data is sensitive, false otherwise
     */
    boolean isCommandLineDataSensitive();

    /**
     * Builds the command line in bytes - the line to be sent over wire.
     *
     * @return command line in bytes
     * @throws SmtpAsyncClientException when encountering an error in building the command line
     */
    @Nonnull
    ByteBuf getCommandLineBytes() throws SmtpAsyncClientException;

    /**
     * @return SMTP command type
     */
    @Nullable
    SmtpCommandType getCommandType();

    /**
     * @return log data appropriate for the command
     */
    @Nonnull
    String getDebugData();

    /**
     * Builds the next command line upon additional server requests.
     *
     * @param serverResponse the server response
     * @return command line
     * @throws SmtpAsyncClientException when building command line encounters an error
     */
    @Nullable
    ByteBuf getNextCommandLineAfterContinuation(SmtpResponse serverResponse) throws SmtpAsyncClientException;

    /**
     * Avoids loitering.
     */
    void cleanup();
}
