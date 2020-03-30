/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.client;

import javax.annotation.Nonnull;

import com.yahoo.smtpnio.async.response.SmtpResponse;

/**
 * This class defines the response from {@link SmtpAsyncClient}'s {@code createSession} method.
 */
public class SmtpAsyncCreateSessionResponse {

    /** An instance of {@link SmtpAsyncSession} created through the {@code createSession} method in {@link SmtpAsyncClient}. */
    private final SmtpAsyncSession session;

    /** SMTP server greeting response upon an established connection. */
    private final SmtpResponse greeting;

    /**
     * Instantiates a {@link SmtpAsyncCreateSessionResponse} instance with a {@link SmtpAsyncSession} object and server initial greeting.
     *
     * @param session {@link SmtpAsyncSession} instance that is created by {@code createSession} method in {@link SmtpAsyncClient}
     * @param greeting the initial server greeting response
     */
    public SmtpAsyncCreateSessionResponse(@Nonnull final SmtpAsyncSession session, @Nonnull final SmtpResponse greeting) {
        this.session = session;
        this.greeting = greeting;
    }

    /**
     * @return {@link SmtpAsyncSession} instance
     */
    public SmtpAsyncSession getSession() {
        return session;
    }

    /**
     * @return server's initial greeting after establishing a connection
     */
    public SmtpResponse getServerGreeting() {
        return greeting;
    }
}
