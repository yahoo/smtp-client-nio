/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.response.SmtpResponse;

/**
 * Unit test for {@link AuthenticationXoauth2Command}.
 */
public class AuthenticationXoauth2CommandTest {

    /** Fields to check for cleanup. */
    private final Set<Field> fieldsToCheck = new HashSet<>();

    /**
     * Setup reflection.
     */
    @BeforeClass
    public void setUp() {
        final Class<?> classUnderTest = AuthenticationXoauth2Command.class;
        for (Class<?> c = classUnderTest; c != null; c = c.getSuperclass()) {
            for (final Field declaredField : c.getDeclaredFields()) {
                if (!declaredField.getType().isPrimitive() && !Modifier.isStatic(declaredField.getModifiers())) {
                    declaredField.setAccessible(true);
                    fieldsToCheck.add(declaredField);
                }
            }
        }
    }

    /**
     * Tests the constructor taking in the username and oauth access token.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testConstructor() throws SmtpAsyncClientException, IllegalAccessException {
        final SmtpRequest cmd = new AuthenticationXoauth2Command("my_username", "my_token");
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII),
                "AUTH XOAUTH2 dXNlcj1teV91c2VybmFtZQFhdXRoPUJlYXJlciBteV90b2tlbgEB\r\n", "Expected results mismatched");
        Assert.assertTrue(cmd.isCommandLineDataSensitive());
        cmd.cleanup();
        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(cmd), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests the {@code getMechanism} method.
     */
    @Test
    public void testGetMechanism() {
        Assert.assertEquals(new AuthenticationXoauth2Command("abc", "def").getMechanism(), "XOAUTH2");
    }

    /**
     * Test the {@code getCommandType} method.
     */
    @Test
    public void testGetCommandType() {
        Assert.assertEquals(new AuthenticationXoauth2Command("user", "tok").getCommandType(), SmtpRFCSupportedCommandType.AUTH);
    }

    /**
     * Test the behavior of the {@code getNextCommandLineAfterContinuation} method. It should send CRLF.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     */
    @Test
    public void testGetNextCommandLineAfterContinuationFailedCredentials() throws SmtpAsyncClientException {
        final SmtpRequest cmd = new AuthenticationXoauth2Command("12345", "token");
        final String response = cmd.getNextCommandLineAfterContinuation(new SmtpResponse("334 Credentials not accepted"))
                .toString(StandardCharsets.US_ASCII);

        Assert.assertEquals(response, "\r\n", "Incorrect response sent upon a failed OAuth command");
    }

    /**
     * Tests the {@code getDebugData} method.
     */
    @Test
    public void testGetDebugData() {
        Assert.assertEquals(new AuthenticationXoauth2Command("Alice", "token2").getDebugData(), "AUTH XOAUTH2 <secret>\r\n");
    }
}
