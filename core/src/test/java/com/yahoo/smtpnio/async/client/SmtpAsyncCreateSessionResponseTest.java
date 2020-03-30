/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.client;

import org.mockito.Mockito;
import org.slf4j.Logger;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.yahoo.smtpnio.async.client.SmtpAsyncSession.DebugMode;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.internal.SmtpAsyncSessionImpl;
import com.yahoo.smtpnio.async.response.SmtpResponse;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;

/**
 * Unit test for {@link SmtpAsyncCreateSessionResponse}.
 */
public class SmtpAsyncCreateSessionResponseTest {

    /**
     * Tests {@link SmtpAsyncCreateSessionResponse} constructor and getters.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     */
    @Test
    public void testSmtpAsyncCreateSessionResponse() throws SmtpAsyncClientException {
        final long sessionId = 123456L;
        final SmtpResponse smtpResponse = new SmtpResponse("220 smtp.mail.yahoo.com ESMTP ready");
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        final Channel channel = Mockito.mock(Channel.class);
        final Logger logger = Mockito.mock(Logger.class);
        final String sessionContext = "currentContext";
        final SmtpAsyncSession session = new SmtpAsyncSessionImpl(channel, logger, DebugMode.DEBUG_ON, sessionId, pipeline, sessionContext);
        final SmtpAsyncCreateSessionResponse respOut = new SmtpAsyncCreateSessionResponse(session, smtpResponse);

        Assert.assertEquals(respOut.getSession(), session, "getSession() result mismatched.");
        Assert.assertEquals(respOut.getServerGreeting(), smtpResponse, "getServerGreeting() result mismatched.");
    }
}
