/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManagerFactory;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.yahoo.smtpnio.async.client.SmtpAsyncSession.DebugMode;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.response.SmtpResponse;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.ssl.SslHandler;

/**
 * Tests that the {@link SslHandler} built for SSL and for STARTTLS verifies the server certificate against the server name, by running its
 * handshake in memory against a server engine.
 *
 * <p>
 * The stores under {@code src/test/resources/tls} hold a test CA ({@code truststore.p12}) and two server certificates it issued, one for
 * {@code localhost} ({@code localhost.p12}) and one for {@code smtp.example.com} ({@code other.p12}). Both chain to the trusted CA, so only the
 * hostname check tells them apart. All stores use the password {@code changeit}.
 */
public class SmtpAsyncClientHostnameVerificationTest {

    /** Password of the test key and trust stores. */
    private static final char[] STORE_PASSWORD = "changeit".toCharArray();

    /** Host name the {@code localhost.p12} certificate names. */
    private static final String HOST = "localhost";

    /** Port the client connects to. */
    private static final int PORT = 465;

    /** Size of the buffers carrying TLS records between the engines. */
    private static final int BUFFER_SIZE = 1 << 16;

    /**
     * @return rows of host, server key store, SNI name and whether the handshake should succeed, each once for SSL and once for STARTTLS
     */
    @DataProvider
    public Object[][] cases() {
        final Object[][] base = {
            { HOST, "localhost.p12", null, true }, // certificate names the host
            { HOST, "other.p12", null, false }, // trusted certificate for another name
            { HOST, "other.p12", "smtp.example.com", true }, // certificate names the SNI name
            { HOST, "other.p12", "sni.example.org", false }, // certificate names neither
            { "127.0.0.1", "localhost.p12", HOST, true }, // connecting by address, with the server name as SNI
            { "127.0.0.1", "localhost.p12", null, false }, // connecting by address alone
        };
        final List<Object[]> rows = new ArrayList<>();
        for (final boolean starttls : new boolean[] { false, true }) {
            for (final Object[] row : base) {
                rows.add(new Object[] { starttls, row[0], row[1], row[2], row[3] });
            }
        }
        return rows.toArray(new Object[0][]);
    }

    /**
     * Tests whether the handshake succeeds for the given certificate and settings.
     *
     * @param starttls whether to take the handler STARTTLS adds rather than the one built for SSL
     * @param host host the client connects to
     * @param serverKeyStore key store holding the server certificate
     * @param sniName SNI name to send, or null for none
     * @param expectSuccess whether the handshake should succeed
     * @throws GeneralSecurityException on failure to load a store or set up the engines
     * @throws IOException on failure to read a store
     * @throws SmtpAsyncClientException on failure to parse a canned server reply
     */
    @Test(dataProvider = "cases")
    public void testHandshake(final boolean starttls, @Nonnull final String host, @Nonnull final String serverKeyStore,
            @Nullable final String sniName, final boolean expectSuccess) throws GeneralSecurityException, IOException, SmtpAsyncClientException {
        final SmtpAsyncSessionData data = SmtpAsyncSessionData.newBuilder(host, PORT, !starttls)
                .setSniNames(sniName == null ? null : Collections.singletonList(sniName)).setSSLContext(trustingContext()).build();
        final SSLEngine client = (starttls ? starttlsHandler(data)
                : SmtpAsyncClient.createSSLHandler(ByteBufAllocator.DEFAULT, host, PORT, data.getSniNames(), data.getSSLContext())).engine();
        final SSLEngine server = serverEngine(serverKeyStore);

        if (expectSuccess) {
            handshake(client, server);
        } else {
            Assert.assertThrows(SSLHandshakeException.class, () -> handshake(client, server));
        }
    }

    /**
     * Tests that the engine built from the default context also checks the name.
     *
     * @throws SSLException on failure to build the default context
     */
    @Test
    public void testDefaultContextChecksName() throws SSLException {
        final SSLEngine engine = SmtpAsyncClient.createSSLHandler(ByteBufAllocator.DEFAULT, HOST, PORT, null, null).engine();
        Assert.assertEquals(engine.getSSLParameters().getEndpointIdentificationAlgorithm(), "HTTPS", "Name check should be on");
    }

    /**
     * Creates a client context trusting the test CA. Each test gets its own, so no session cached by an earlier test is offered for resumption.
     *
     * @return the client context
     * @throws GeneralSecurityException on failure to load the trust store
     * @throws IOException on failure to read the trust store
     */
    private SSLContext trustingContext() throws GeneralSecurityException, IOException {
        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(loadStore("truststore.p12"));
        final SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx;
    }

    /**
     * Runs {@link StarttlsHandler} on mocks up to the STARTTLS reply and returns the {@link SslHandler} it adds to the pipeline.
     *
     * @param data session data
     * @return the handler added for the upgrade
     * @throws SmtpAsyncClientException on failure to parse a canned server reply
     */
    private SslHandler starttlsHandler(@Nonnull final SmtpAsyncSessionData data) throws SmtpAsyncClientException {
        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(ctx.pipeline()).thenReturn(pipeline);
        Mockito.when(ctx.channel()).thenReturn(Mockito.mock(Channel.class));
        Mockito.when(ctx.alloc()).thenReturn(ByteBufAllocator.DEFAULT);

        final StarttlsHandler handler = new StarttlsHandler(new SmtpFuture<>(), Mockito.mock(Logger.class), DebugMode.DEBUG_OFF, 1, data);
        final List<Object> out = new ArrayList<>();
        handler.decode(ctx, new SmtpResponse("220 Hello there"), out);
        handler.decode(ctx, new SmtpResponse("250 STARTTLS\r\n"), out);
        handler.decode(ctx, new SmtpResponse("220 Ready to start TLS"), out);

        final ArgumentCaptor<ChannelHandler> captor = ArgumentCaptor.forClass(ChannelHandler.class);
        Mockito.verify(pipeline).addFirst(Mockito.anyString(), captor.capture());
        return (SslHandler) captor.getValue();
    }

    /**
     * @param keyStore key store holding the server certificate
     * @return a server engine presenting that certificate
     * @throws GeneralSecurityException on failure to load the key store
     * @throws IOException on failure to read the key store
     */
    private SSLEngine serverEngine(@Nonnull final String keyStore) throws GeneralSecurityException, IOException {
        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(loadStore(keyStore), STORE_PASSWORD);
        final SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        final SSLEngine engine = ctx.createSSLEngine();
        engine.setUseClientMode(false);
        return engine;
    }

    /**
     * Runs a TLS handshake between two engines by passing their records to each other.
     *
     * @param client client engine
     * @param server server engine
     * @throws SSLException when either side rejects the handshake
     */
    private void handshake(@Nonnull final SSLEngine client, @Nonnull final SSLEngine server) throws SSLException {
        final ByteBuffer toServer = ByteBuffer.allocate(BUFFER_SIZE);
        final ByteBuffer toClient = ByteBuffer.allocate(BUFFER_SIZE);
        client.beginHandshake();
        server.beginHandshake();
        while (isHandshaking(client) || isHandshaking(server)) {
            step(client, toClient, toServer);
            step(server, toServer, toClient);
        }
    }

    /**
     * Lets an engine read what the peer sent, run its delegated tasks and write its reply.
     *
     * @param engine engine to step
     * @param in records from the peer
     * @param out records to the peer
     * @throws SSLException when the engine rejects the handshake
     */
    private void step(@Nonnull final SSLEngine engine, @Nonnull final ByteBuffer in, @Nonnull final ByteBuffer out) throws SSLException {
        in.flip();
        engine.unwrap(in, ByteBuffer.allocate(BUFFER_SIZE));
        in.compact();
        for (Runnable task = engine.getDelegatedTask(); task != null; task = engine.getDelegatedTask()) {
            task.run();
        }
        engine.wrap(ByteBuffer.allocate(0), out);
    }

    /**
     * @param engine engine to check
     * @return whether the engine has more handshake work to do
     */
    private boolean isHandshaking(@Nonnull final SSLEngine engine) {
        final HandshakeStatus status = engine.getHandshakeStatus();
        return status != HandshakeStatus.NOT_HANDSHAKING && status != HandshakeStatus.FINISHED;
    }

    /**
     * @param name name of the store under {@code tls/} on the test class path
     * @return the loaded store
     * @throws GeneralSecurityException on failure to load the store
     * @throws IOException on failure to read the store
     */
    private KeyStore loadStore(@Nonnull final String name) throws GeneralSecurityException, IOException {
        final KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = getClass().getResourceAsStream("/tls/" + name)) {
            Assert.assertNotNull(in, "Missing test store " + name);
            ks.load(in, STORE_PASSWORD);
        }
        return ks;
    }
}
