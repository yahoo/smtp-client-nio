/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.command;

import java.util.ArrayList;
import java.util.List;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;

import io.netty.channel.ChannelHandlerContext;

/**
 * Unit test for {@link SmtpClientRespDecoder}.
 */
public class SmtpClientRespDecoderTest {

    /**
     * Tests the correctness of the {@code decode} method.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     */
    @Test
    public void testDecode() throws SmtpAsyncClientException {
        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final SmtpClientRespDecoder decoder = new SmtpClientRespDecoder();
        final List<Object> result = new ArrayList<>();

        decoder.decode(ctx, "220 Welcome to SMTP", result);

        Assert.assertEquals(result.size(), 1);
        Assert.assertEquals(result.get(0).toString(), "220 Welcome to SMTP");
    }
}
