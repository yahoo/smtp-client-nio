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
 * Unit test for {@link AuthenticationPlainCommand}.
 */
public class AuthenticationPlainCommandTest {

    /** Fields to check for cleanup. */
    private final Set<Field> fieldsToCheck = new HashSet<>();

    /**
     * Setup reflection.
     */
    @BeforeClass
    public void setUp() {
        final Class<?> classUnderTest = AuthenticationPlainCommand.class;
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
     * Tests the constructor taking in the username and password in plaintext.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testGetCommandLineConstructorUserPass() throws SmtpAsyncClientException, IllegalAccessException {
        final SmtpRequest cmd = new AuthenticationPlainCommand("test_user123@example.com", "PasswordisPassword!");
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII),
                "AUTH PLAIN AHRlc3RfdXNlcjEyM0BleGFtcGxlLmNvbQBQYXNzd29yZGlzUGFzc3dvcmQh\r\n", "Expected results mismatched");
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII),
                "AUTH" + SmtpClientConstants.SPACE + "PLAIN AHRlc3RfdXNlcjEyM0BleGFtcGxlLmNvbQBQYXNzd29yZGlzUGFzc3dvcmQh"
                        + SmtpClientConstants.CRLF, "Expected results mismatched");
        Assert.assertTrue(cmd.isCommandLineDataSensitive());
        cmd.cleanup();
        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(cmd), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests the constructor taking the base64 encoded string.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testGetCommandLineConstructorSecret() throws SmtpAsyncClientException, IllegalAccessException {
        final SmtpRequest cmd = new AuthenticationPlainCommand("ThisShouldAlreadyBeBase64Encoded!");
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII),
                "AUTH PLAIN ThisShouldAlreadyBeBase64Encoded!\r\n", "Expected results mismatched");
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII),
                "AUTH" + SmtpClientConstants.SPACE + "PLAIN ThisShouldAlreadyBeBase64Encoded!"
                        + SmtpClientConstants.CRLF, "Expected results mismatched");
        Assert.assertTrue(cmd.isCommandLineDataSensitive());
        cmd.cleanup();
        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(cmd), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * This tests the equivalent command by using thw two different constructors.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testEquivalentResponses() throws SmtpAsyncClientException, IllegalAccessException {
        final SmtpRequest cmd = new AuthenticationPlainCommand("me.user@test.org", "this is my password?");
        final SmtpRequest cmd2 = new AuthenticationPlainCommand("AG1lLnVzZXJAdGVzdC5vcmcAdGhpcyBpcyBteSBwYXNzd29yZD8=");

        final String expected = "AUTH PLAIN AG1lLnVzZXJAdGVzdC5vcmcAdGhpcyBpcyBteSBwYXNzd29yZD8=\r\n";
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII), expected , "Expected results mismatched");
        Assert.assertEquals(cmd2.getCommandLineBytes().toString(StandardCharsets.US_ASCII), expected , "Expected results mismatched");

        Assert.assertTrue(cmd.isCommandLineDataSensitive());
        Assert.assertTrue(cmd2.isCommandLineDataSensitive());

        cmd.cleanup();
        cmd2.cleanup();
        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(cmd), "Cleanup should set " + field.getName() + " as null");
            Assert.assertNull(field.get(cmd2), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests the {@code getMechanism} method.
     */
    @Test
    public void testGetMechanism() {
        Assert.assertEquals(new AuthenticationPlainCommand("secret").getMechanism(), "PLAIN");
        Assert.assertEquals(new AuthenticationPlainCommand("user", "pass").getMechanism(), "PLAIN");
    }

    /**
     * Test the {@code getCommandType} method.
     */
    @Test
    public void testGetCommandType() {
        Assert.assertEquals(new AuthenticationPlainCommand("user", "password").getCommandType(), SmtpCommandType.AUTH);
        Assert.assertEquals(new AuthenticationPlainCommand("secret_passphrase").getCommandType(), SmtpCommandType.AUTH);
    }

    /**
     * Test the behavior of the {@code getNextCommandLineAfterContinuation} method, as well as the correctness of the corresponding exception thrown.
     */
    @Test
    public void testGetNextCommandLineAfterContinuationUsernamePassword() {
        final SmtpRequest cmd = new AuthenticationPlainCommand("user", "pw");
        try {
            cmd.getNextCommandLineAfterContinuation(new SmtpResponse("400 resp"));
            Assert.fail("Exception should've been thrown");
        } catch (SmtpAsyncClientException ex) {
            Assert.assertEquals(ex.getFailureType(), SmtpAsyncClientException.FailureType.OPERATION_NOT_SUPPORTED_FOR_COMMAND,
                    "Failure type mismatch");
        }
    }

    /**
     * Test the behavior of the {@code getNextCommandLineAfterContinuation} method, as well as the correctness of the corresponding exception thrown.
     */
    @Test
    public void testGetNextCommandLineAfterContinuationSecrets() {
        final SmtpRequest cmd = new AuthenticationPlainCommand("username_password");
        try {
            cmd.getNextCommandLineAfterContinuation(new SmtpResponse("400 resp"));
            Assert.fail("Exception should have been thrown this object");
        } catch (SmtpAsyncClientException ex) {
            Assert.assertEquals(ex.getFailureType(), SmtpAsyncClientException.FailureType.OPERATION_NOT_SUPPORTED_FOR_COMMAND,
                    "Failure type mismatch");
        }
    }

    /**
     * Tests the {@code getDebugData} method.
     */
    @Test
    public void testGetDebugData() {
        Assert.assertNull(new AuthenticationPlainCommand("").getDebugData());
        Assert.assertNull(new AuthenticationPlainCommand("", "").getDebugData());
    }
}
