/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.client;

import java.net.InetSocketAddress;
import java.util.Collection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;

/**
 * This class defines session data associated with {@link SmtpAsyncClient}.
 */
public final class SmtpAsyncSessionData {

    /** the host name that the client will connect to. */
    @Nonnull
    private final String host;

    /** the port number to connect to. */
    private final int port;

    /** Whether to use SSL or not. */
    private final boolean enableSsl;

    /** Server Name Indication (SNI) list. */
    @Nullable
    private final Collection<String> sniNames;

    /** Local address to be used if present. */
    @Nullable
    private final InetSocketAddress localAddress;

    /** Context associated with the session. */
    @Nullable
    private final Object sessionContext;

    /**
     * {@link SSLContext} to be used for the session.
     *
     */
    @Nullable
    private final SSLContext sslContext;
    /**
     * Builder class to build {@link SmtpAsyncSessionData} instances.
     */
    public static final class DataBuilder {

        /** the host name that the client will connect to. */
        @Nonnull
        private final String host;

        /** the port number to connect to. */
        private final int port;

        /** Whether to use SSL or not. */
        private final boolean enableSsl;

        /** Server Name Indication (SNI) list. */
        @Nullable
        private Collection<String> sniNames;

        /** Local address to be used if present. */
        @Nullable
        private InetSocketAddress localAddress;

        /** Context associated with the session. */
        @Nullable
        private Object sessionContext;

        /** SSLContext to be used for the session. */
        @Nullable
        private SSLContext sslContext;

        /**
         * Initializes a builder for {@link SmtpAsyncSessionData}.
         *
         * @param host host name
         * @param port port number
         * @param enableSsl whether to use SSL or not
         */
        private DataBuilder(@Nonnull final String host, final int port, final boolean enableSsl) {
            this.host = host;
            this.port = port;
            this.enableSsl = enableSsl;
        }

        /**
         * @param sniNames new SNI list
         * @return {@code this} object for chaining
         */
        public DataBuilder setSniNames(@Nullable final Collection<String> sniNames) {
            this.sniNames = sniNames;
            return this;
        }

        /**
         * @param localAddress new local address
         * @return {@code this} object for chaining
         */
        public DataBuilder setLocalAddress(@Nullable final InetSocketAddress localAddress) {
            this.localAddress = localAddress;
            return this;
        }

        /**
         * @param sessionCtx new session context
         * @return {@code this} object for chaining
         */
        public DataBuilder setSessionContext(@Nonnull final Object sessionCtx) {
            this.sessionContext = sessionCtx;
            return this;
        }

        /**
         * @param sslCtx Ssl context
         * @return {@code this} object for chaining
         */
        public DataBuilder setSSLContext(@Nonnull final SSLContext sslCtx) {
            this.sslContext = sslCtx;
            return this;
        }

        /**
         * Builds the {@link SmtpAsyncSessionData} with the set properties.
         *
         * @return an {@link SmtpAsyncSessionData} instance
         */
        public SmtpAsyncSessionData build() {
            return new SmtpAsyncSessionData(host, port, enableSsl, sniNames, localAddress, sessionContext, sslContext);
        }
    }

    /**
     * Creates a new builder for {@link SmtpAsyncSessionData} if the arguments are valid.
     *
     * @param host host name
     * @param port port number
     * @param enableSsl whether if SSL is enabled
     * @return {@link DataBuilder} instance with the specified arguments
     */
    public static DataBuilder newBuilder(@Nonnull final String host, final int port, final boolean enableSsl) {
        return new DataBuilder(host, port, enableSsl);
    }

    /**
     * Initializes a {@link SmtpAsyncSessionData} object used for {@link SmtpAsyncClient}'s sessions.
     *
     * @param host host name
     * @param port port number
     * @param enableSsl whether to use SSL or not
     * @param sniNames collection of SNI names
     * @param localAddress local address to be used
     * @param sessionCtx session context
     * @param sslCtx Ssl Context to be used
     */
    private SmtpAsyncSessionData(@Nonnull final String host, final int port, final boolean enableSsl, @Nullable final Collection<String> sniNames,
                                 @Nullable final InetSocketAddress localAddress, @Nullable final Object sessionCtx,
                                 @Nullable final SSLContext sslCtx) {
        this.host = host;
        this.port = port;
        this.enableSsl = enableSsl;
        this.sniNames = sniNames;
        this.localAddress = localAddress;
        this.sessionContext = sessionCtx;
        this.sslContext = sslCtx;
    }

    /**
     * @return host
     */
    @Nonnull
    public String getHost() {
        return host;
    }

    /**
     * @return port number
     */
    public int getPort() {
        return port;
    }

    /**
     * @return whether if SSL is enabled
     */
    public boolean isSslEnabled() {
        return enableSsl;
    }

    /**
     * @return SNI name list
     */
    @Nullable
    public Collection<String> getSniNames() {
        return sniNames;
    }

    /**
     * @return local address
     */
    @Nullable
    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    /**
     * @return session context
     */
    @Nullable
    public Object getSessionContext() {
        return sessionContext;
    }

    /**
     * @return Ssl context
     */
    @Nullable
    public SSLContext getSSLContext() {
        return sslContext;
    }

}
