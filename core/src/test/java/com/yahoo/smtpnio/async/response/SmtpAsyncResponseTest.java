/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.response;

import java.util.ArrayList;
import java.util.Collection;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;

/**
 * Unit test for {@link SmtpAsyncResponse}.
 */
public class SmtpAsyncResponseTest {

    /**
     * Tests the {@link SmtpAsyncResponse} constructor and getters.
     *
     * @throws SmtpAsyncClientException when encountering an invalid server response
     */
    @Test
    public void testSmtpAsyncResponse() throws SmtpAsyncClientException {
        final Collection<SmtpResponse> smtpResponses = new ArrayList<>();
        final SmtpResponse oneSmtpResponse = new SmtpResponse("220 greetings\r\n");
        smtpResponses.add(oneSmtpResponse);
        final SmtpAsyncResponse resp = new SmtpAsyncResponse(smtpResponses);

        final Collection<SmtpResponse> actual = resp.getResponseLines();
        Assert.assertEquals(actual, smtpResponses, "result mismatched.");
        Assert.assertEquals(actual.iterator().next(), oneSmtpResponse, "result mismatched.");
    }
}
