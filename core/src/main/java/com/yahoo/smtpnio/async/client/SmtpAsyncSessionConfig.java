/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.client;

/**
 * Class for SMTP Client connection and channel configurations.
 */
public final class SmtpAsyncSessionConfig {

    /** Default connection timeout (milliseconds). */
    public static final int DEFAULT_CONNECTION_TIMEOUT_MILLIS = 500;

    /** Default read from server timeout (milliseconds). */
    public static final int DEFAULT_READ_TIMEOUT_MILLIS = 10000;

    /** Maximum time in milliseconds for opening a connection. */
    private int connectionTimeoutMillis = DEFAULT_CONNECTION_TIMEOUT_MILLIS;

    /** Maximum time in milliseconds for read timeout. The maximum time allowing no responses from server since client command sent. */
    private int readTimeoutMillis = DEFAULT_READ_TIMEOUT_MILLIS;

    /**
     * @return Maximum time for opening a connection before timeout
     */
    public int getConnectionTimeout() {
        return connectionTimeoutMillis;
    }

    /**
     * Sets the maximum time for opening a connection in milliseconds.
     *
     * @param connectionTimeoutMillis time in milliseconds
     * @return this object for chaining
     */
    public SmtpAsyncSessionConfig setConnectionTimeout(final int connectionTimeoutMillis) {
        this.connectionTimeoutMillis = connectionTimeoutMillis;
        return this;
    }

    /**
     * @return Maximum time for read before timeout
     */
    public int getReadTimeout() {
        return readTimeoutMillis;
    }

    /**
     * Sets the maximum time for read timeout - time waiting for server to respond.
     *
     * @param readTimeoutMillis time in milliseconds
     * @return this object for chaining
     */
    public SmtpAsyncSessionConfig setReadTimeout(final int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
        return this;
    }
}
