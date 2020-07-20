/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.netty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;

/**
 * This class provides a builder to create new SslHandler.
 */
public final class SslHandlerBuilder {
    /** SslContext used to create SslHandler. */
    @Nonnull
    private final SslContext sslContext;

    /** Allocator for ByteBuf objects. */
    @Nonnull
    private final ByteBufAllocator allocator;

    /** Host name that the client will connect to. */
    @Nonnull
    private final String host;

    /** Port number to connect to. */
    private final int port;

    /** Server Name Indication (SNI) list. */
    @Nullable
    private final Collection<String> sniNames;

    /**
     * Initialize an SslHandlerBuilder object used to build {@link SslHanlder}.
     *
     * @param sslContext SslContext used to create SslHandler
     * @param alloc allocator for ByteBuf objects
     * @param host host name of server
     * @param port port of server
     * @param sniNames collection of SNI names
     */
    private SslHandlerBuilder(@Nonnull final SslContext sslContext, @Nonnull final ByteBufAllocator alloc, @Nonnull final String host,
            final int port,
            @Nullable final Collection<String> sniNames) {
        this.sslContext = sslContext;
        this.allocator = alloc;
        this.host = host;
        this.port = port;
        this.sniNames = sniNames;
    }

    /**
     * Creates a new SslHandlerBuilder object used to build {@link SslHanlder}.
     *
     * @param sslContext SslContext used to create SslHandler
     * @param alloc allocator for ByteBuf objects
     * @param host host name of server
     * @param port port of server
     * @param sniNames collection of SNI names
     * @return a SslHandlerBuilder object used to build {@link SslHanlder}
     */
    public static SslHandlerBuilder newBuilder(@Nonnull final SslContext sslContext, @Nonnull final ByteBufAllocator alloc,
            @Nonnull final String host,
            final int port, @Nullable final Collection<String> sniNames) {
        return new SslHandlerBuilder(sslContext, alloc, host, port, sniNames);
    }

    /**
     * Create a new {@link SslHanlder} for decryption/encryption.
     *
     * @return an Sslhandler to process ssl connection
     */
    public SslHandler build() {
        if (sniNames != null && !sniNames.isEmpty()) { // SNI support
            final List<SNIServerName> serverNames = new ArrayList<>();
            for (final String sni : sniNames) {
                serverNames.add(new SNIHostName(sni));
            }
            final SSLParameters params = new SSLParameters();
            params.setServerNames(serverNames);
            final SSLEngine engine = sslContext.newEngine(allocator, host, port);
            engine.setSSLParameters(params);
            return new SslHandler(engine);
        } else {
            return sslContext.newHandler(allocator, host, port);
        }
    }



}
