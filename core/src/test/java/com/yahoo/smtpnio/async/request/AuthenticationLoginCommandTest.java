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
 * Unit test for {@link AuthenticationLoginCommand}.
 */
public class AuthenticationLoginCommandTest {

    /** Fields to check for cleanup. */
    private final Set<Field> fieldsToCheck = new HashSet<>();

    /**
     * Setup reflection.
     */
    @BeforeClass
    public void setUp() {
        final Class<?> classUnderTest = AuthenticationLoginCommand.class;
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
     * Tests the sequences of challenges responses. It should first request the username, then the password.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testGetNextCommandLineAfterContinuation() throws SmtpAsyncClientException, IllegalAccessException {
        final SmtpRequest cmd = new AuthenticationLoginCommand("test_user123@example.com", "PasswordisPassword!");
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII), "AUTH LOGIN\r\n",
                "Expected results mismatched");
        Assert.assertTrue(cmd.isCommandLineDataSensitive(), "Incorrect flag for isCommandLineDataSensitive");

        final String username = cmd.getNextCommandLineAfterContinuation(new SmtpResponse("334 Enter username:")).toString(StandardCharsets.US_ASCII);
        Assert.assertEquals(username, "test_user123@example.com\r\n", "Username is incorrect");

        final String password = cmd.getNextCommandLineAfterContinuation(new SmtpResponse("334 Enter password:")).toString(StandardCharsets.US_ASCII);
        Assert.assertEquals(password, "PasswordisPassword!\r\n", "Password is incorrect");

        cmd.cleanup();
        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(cmd), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Test the behavior of command when additional, unexpected challenges are requests. The response should simply be new lines.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testGetNextCommandLineAfterContinuationExtraPrompts() throws SmtpAsyncClientException, IllegalAccessException {
        final SmtpRequest cmd = new AuthenticationLoginCommand("user", "pass");
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII), "AUTH LOGIN\r\n",
                "Expected results mismatched");
        Assert.assertTrue(cmd.isCommandLineDataSensitive());

        final String username = cmd.getNextCommandLineAfterContinuation(new SmtpResponse("334 Enter username:")).toString(StandardCharsets.US_ASCII);
        Assert.assertEquals(username, "user\r\n", "Username is incorrect");

        final String password = cmd.getNextCommandLineAfterContinuation(new SmtpResponse("334 Enter password:")).toString(StandardCharsets.US_ASCII);
        Assert.assertEquals(password, "pass\r\n", "Password is incorrect");

        final String extra = cmd.getNextCommandLineAfterContinuation(new SmtpResponse("334 More info:")).toString(StandardCharsets.US_ASCII);
        Assert.assertEquals(extra, "\r\n", "command input does not match the expected results");

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
        Assert.assertEquals(new AuthenticationLoginCommand("user", "pass").getMechanism(), "LOGIN");
    }

    /**
     * Tests the {@code getCommandType} method.
     */
    @Test
    public void testGetCommandType() {
        Assert.assertEquals(new AuthenticationLoginCommand("user", "password").getCommandType(), SmtpCommandType.AUTH);
    }

    /**
     * Tests the {@code getDebugData} method.
     */
    @Test
    public void testGetDebugData() {
        Assert.assertNull(new AuthenticationLoginCommand("", "").getDebugData());
    }
}
