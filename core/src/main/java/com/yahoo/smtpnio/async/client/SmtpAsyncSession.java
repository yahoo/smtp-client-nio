/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.client;

import javax.annotation.Nonnull;

import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.request.SmtpRequest;
import com.yahoo.smtpnio.async.response.SmtpAsyncResponse;

/**
 * This interface defines the behavior of an asynchronous SMTP session.
 */
public interface SmtpAsyncSession {

    /**
     * enum to turn debugging on or off for this session.
     */
    enum DebugMode {

        /** Debugging is off. */
        DEBUG_OFF,

        /** Debugging is on. */
        DEBUG_ON
    }

    /**
     * Turns debugging on or off.
     *
     * @param debugMode the debugging mode
     */
    void setDebugMode(@Nonnull DebugMode debugMode);

    /**
     * Sends a SMTP command to the server.
     *
     * @param command the command request.
     * @return a future placeholder for the response from this command
     * @throws SmtpAsyncClientException on failure
     */
    SmtpFuture<SmtpAsyncResponse> execute(@Nonnull SmtpRequest command) throws SmtpAsyncClientException;

    /**
     * Closes/disconnects this session.
     *
     * @return a future indicating completion status. True when successful, otherwise failure.
     */
    SmtpFuture<Boolean> close();
}
