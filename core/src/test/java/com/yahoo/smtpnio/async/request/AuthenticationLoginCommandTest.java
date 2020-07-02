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
     * Tests the sequence of challenges responses. It should first give the username, then the password.
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
     * Test the behavior of the command when additional, unexpected challenges are requested. The response should simply be empty strings.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testGetNextCommandLineAfterContinuationExtraPrompts() throws SmtpAsyncClientException, IllegalAccessException {
        final SmtpRequest cmd = new AuthenticationLoginCommand("user", "pass");
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII), "AUTH LOGIN\r\n",
                "Expected results mismatched");
        Assert.assertTrue(cmd.isCommandLineDataSensitive(), "User credentials should be sensitive!");

        final String username = cmd.getNextCommandLineAfterContinuation(new SmtpResponse("334 Enter username:")).toString(StandardCharsets.US_ASCII);
        Assert.assertEquals(username, "user\r\n", "Username is incorrect");

        final String password = cmd.getNextCommandLineAfterContinuation(new SmtpResponse("334 Enter password:")).toString(StandardCharsets.US_ASCII);
        Assert.assertEquals(password, "pass\r\n", "Password is incorrect");

        // Exception should be catched if server asks for more input
        try {
            cmd.getNextCommandLineAfterContinuation(new SmtpResponse("334 Extra stuff"));
            Assert.fail("Exception not catched after server ask for more data");
        } catch (SmtpAsyncClientException e) {
            Assert.assertEquals(e.getFailureType(),
                    SmtpAsyncClientException.FailureType.MORE_INPUT_THAN_EXPECTED,
                    "Wrong SmtpAsyncClientException type");
        }

        // Exception should be catched if server response is 5xx
        try {
            cmd.getNextCommandLineAfterContinuation(new SmtpResponse("504 Unrecognized authentication type"));
            Assert.fail("Exception not catched if server response is 5xx");
        } catch (SmtpAsyncClientException e) {
            Assert.assertEquals(e.getFailureType(), SmtpAsyncClientException.FailureType.COMMAND_NOT_ALLOWED, "Wrong SmtpAsyncClientException type");
        }

        // Exception should be catched if server response is 4xx
        try {
            cmd.getNextCommandLineAfterContinuation(new SmtpResponse("455  Server unable to accommodate parameters\n"));
            Assert.fail("Exception not catched if server response is 5xx");
        } catch (SmtpAsyncClientException e) {
            Assert.assertEquals(e.getFailureType(),
                    SmtpAsyncClientException.FailureType.COMMAND_NOT_ALLOWED,
                    "Wrong SmtpAsyncClientException type");
        }
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
        Assert.assertEquals(new AuthenticationLoginCommand("user", "pass").getMechanism(), "LOGIN", "Incorrect mechanism");
    }

    /**
     * Tests the {@code getCommandType} method.
     */
    @Test
    public void testGetCommandType() {
        Assert.assertEquals(new AuthenticationLoginCommand("user", "password").getCommandType(), SmtpRFCSupportedCommandType.AUTH,
                "Incorrect command type");
    }

    /**
     * Tests the {@code getDebugData} method. Sensitive info should not be produced
     *
     * @throws SmtpAsyncClientException will not throw in this test
     */
    @Test
    public void testGetDebugData() throws SmtpAsyncClientException {
        final AuthenticationLoginCommand cmd = new AuthenticationLoginCommand("my_user", "my_pass");

        // Initially, debug data prints the command sent, which is AUTH LOGIN
        Assert.assertEquals(cmd.getDebugData(), "AUTH LOGIN\r\n", "Wrong debug data");

        // Username prompt
        cmd.getNextCommandLineAfterContinuation(new SmtpResponse("334 VXNlcm5hbWU6"));
        Assert.assertEquals(cmd.getDebugData(), "<username>\r\n", "Wrong debug data");

        // Password prompt
        cmd.getNextCommandLineAfterContinuation(new SmtpResponse("334 UGFzc3dvcmQ6"));
        Assert.assertEquals(cmd.getDebugData(), "<password>\r\n", "Wrong debug data");

        // Extra prompts
        try {
            cmd.getNextCommandLineAfterContinuation(new SmtpResponse("334 Extra stuff"));
            Assert.fail("Exception not catched after server ask for more data");
        } catch (SmtpAsyncClientException e) {
            Assert.assertEquals(e.getFailureType(), SmtpAsyncClientException.FailureType.MORE_INPUT_THAN_EXPECTED,
                    "Wrong SmtpAsyncClientException type");
        }
    }
}
