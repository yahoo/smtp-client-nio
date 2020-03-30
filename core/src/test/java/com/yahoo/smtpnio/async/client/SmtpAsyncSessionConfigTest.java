/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.client;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Unit test for {@link SmtpAsyncSessionConfig}.
 */
public class SmtpAsyncSessionConfigTest {

    /**
     * Tests the correctness of {@code setConnectionTimeout} and {@code getConnectionTimeout}.
     */
    @Test
    public void testGetAndSetConnectionTimeout() {
        final SmtpAsyncSessionConfig client = new SmtpAsyncSessionConfig();
        Assert.assertEquals(client.getConnectionTimeout(), SmtpAsyncSessionConfig.DEFAULT_CONNECTION_TIMEOUT_MILLIS,
                "Incorrect default connection timeout");
        Assert.assertEquals(client.setConnectionTimeout(5678).getConnectionTimeout(), 5678, "Incorrect connection timeout set");
    }

    /**
     * Tests the correctness of {@code setReadTimeout} and {@code getReadTimeout}.
     */
    @Test
    public void testGetAndSetReadTimeout() {
        final SmtpAsyncSessionConfig client = new SmtpAsyncSessionConfig();
        Assert.assertEquals(client.getReadTimeout(), SmtpAsyncSessionConfig.DEFAULT_READ_TIMEOUT_MILLIS, "Incorrect default read timeout");
        Assert.assertEquals(client.setReadTimeout(12345).getReadTimeout(), 12345, "Incorrect read timeout set");
    }

    /**
     * Tests the correctness of the setters for the timeouts when used together.
     */
    @Test
    public void testGetAndSetAllTimeout() {
        final SmtpAsyncSessionConfig client = new SmtpAsyncSessionConfig();
        client.setConnectionTimeout(6000)
                .setReadTimeout(39000)
                .setReadTimeout(3000)
                .setConnectionTimeout(9001); // Only the most recent values count
        Assert.assertEquals(client.getReadTimeout(), 3000);
        Assert.assertEquals(client.getConnectionTimeout(), 9001);
    }
}
