/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.response;

import java.util.Collection;

import javax.annotation.Nonnull;

/**
 * This class defines the response for asynchronous SMTP requests.
 */
public class SmtpAsyncResponse {

    /** List of SMTP response lines. */
    @Nonnull
    private final Collection<SmtpResponse> responses;

    /**
     * Initializes a {@link SmtpAsyncResponse} object.
     *
     * @param responses list of response lines
     */
    public SmtpAsyncResponse(@Nonnull final Collection<SmtpResponse> responses) {
        this.responses = responses;
    }

    /**
     * @return list of {@link SmtpAsyncResponse} lines
     */
    public Collection<SmtpResponse> getResponseLines() {
        return responses;
    }
}
