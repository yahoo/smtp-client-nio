/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.client;

import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import org.testng.Assert;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

/**
 * Unit test for {@link SmtpAsyncSessionData}.
 */
public class SmtpAsyncSessionDataTest {

    /**
     * Tests the correctness of the constructor.
     */
    @Test
    public void testConstructorWithoutSetters() {
        final SmtpAsyncSessionData data = SmtpAsyncSessionData.newBuilder("smtp.my.server.com", 993, true).build();
        Assert.assertEquals(data.getHost(), "smtp.my.server.com", "Unexpected host");
        Assert.assertEquals(data.getPort(), 993, "Unexpected port number");
        Assert.assertTrue(data.isSslEnabled(), "Unexpected SSL flag");
        Assert.assertNull(data.getSniNames(), "SNI name list should be null");
        Assert.assertNull(data.getLocalAddress(), "local address should be null");
        Assert.assertNull(data.getSessionContext(), "session context should be null");
        Assert.assertNull(data.getSslContext(), "Ssl context should be null");
    }

    /**
     * Tests the correctness of the setters.
     *
     * @throws NoSuchAlgorithmException Does not throw
     * @throws SSLException Does not throw
     */
    @Test
    public void testSetters() throws NoSuchAlgorithmException, SSLException {
        final InetSocketAddress localAddress = new InetSocketAddress("localhost", 65535);
        final String context = "UnitTest101";
        final List<String> sniNames = Arrays.asList("Sni1", "Sni2");
        final SSLContext sslCtxt = SSLContext.getDefault();
        SslContext sslContext = SslContextBuilder.forClient()
                .sslProvider(SslProvider.JDK).clientAuth(ClientAuth.NONE)
                .ciphers(Arrays.asList(sslCtxt.getDefaultSSLParameters().getCipherSuites()))
                .build();
        final SmtpAsyncSessionData data = SmtpAsyncSessionData.newBuilder("my_host.smtp.org", 587, false)
                .setSniNames(sniNames)
                .setLocalAddress(localAddress)
                .setSessionContext(context)
                .setSslContext(sslContext)
                .build();

        Assert.assertEquals(data.getHost(), "my_host.smtp.org", "Unexpected host");
        Assert.assertEquals(data.getPort(), 587, "Unexpected port number");
        Assert.assertFalse(data.isSslEnabled(), "Unexpected SSL flag");
        Assert.assertSame(data.getSniNames(), sniNames, "SNI name list should be same");
        Assert.assertSame(data.getLocalAddress(), localAddress, "local address should be same");
        Assert.assertEquals(data.getSessionContext(), "UnitTest101", "session context should match");
        Assert.assertSame(data.getSslContext(), sslContext, "Ssl context should be same");
    }
}
