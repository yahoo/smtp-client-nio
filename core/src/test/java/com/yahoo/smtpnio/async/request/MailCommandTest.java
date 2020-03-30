/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.response.SmtpResponse;

/**
 * Unit test for {@link MailCommand}.
 */
public class MailCommandTest {

    /** Fields to check for cleanup. */
    private final Set<Field> fieldsToCheck = new HashSet<>();

    /**
     * Setup reflection.
     */
    @BeforeClass
    public void setUp() {
        final Class<?> classUnderTest = MailCommand.class;
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
     * Tests the basic functionality with a stub command.
     *
     * @throws SmtpAsyncClientException will not throw
     * @throws IllegalAccessException will not throw
     */
    @Test
    public void testGetCommandLine() throws SmtpAsyncClientException, IllegalAccessException {
        final SmtpRequest cmd = new MailCommand("user1.test@yahoo.com");
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII),
                "MAIL FROM:<user1.test@yahoo.com>\r\n", "Expected results mismatched");
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII),
                "MAIL" + SmtpClientConstants.SPACE + "FROM" + SmtpClientConstants.COLON
                        + SmtpClientConstants.L_ANGLE_BRACKET + "user1.test@yahoo.com" + SmtpClientConstants.R_ANGLE_BRACKET
                        + SmtpClientConstants.CRLF,
                "Expected results mismatched");
        Assert.assertFalse(cmd.isCommandLineDataSensitive());
        cmd.cleanup();
        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(cmd), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests the constructor with no arguments. It should use the string as the sender (aka. reverse-path).
     *
     * @throws SmtpAsyncClientException will not throw
     * @throws IllegalAccessException will not throw
     */
    @Test
    public void testGetCommandLineDefaultConstructor() throws SmtpAsyncClientException, IllegalAccessException {
        final SmtpRequest cmd = new MailCommand();
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII),
                "MAIL FROM:<>\r\n", "Expected results mismatched");
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII),
                "MAIL" + SmtpClientConstants.SPACE + "FROM" + SmtpClientConstants.COLON
                        + SmtpClientConstants.L_ANGLE_BRACKET + SmtpClientConstants.R_ANGLE_BRACKET + SmtpClientConstants.CRLF,
                "Expected results mismatched");
        Assert.assertFalse(cmd.isCommandLineDataSensitive());
        cmd.cleanup();
        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(cmd), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests the constructor taking in just the sender, the result should not have any additional arguments or trailing spaces.
     *
     * @throws SmtpAsyncClientException will not throw
     * @throws IllegalAccessException will not throw
     */
    @Test
    public void testNoMailParameter() throws SmtpAsyncClientException, IllegalAccessException {
        final SmtpRequest cmd = new MailCommand("alice");
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII),
                "MAIL FROM:<alice>\r\n", "Expected results mismatched");
        cmd.cleanup();
        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(cmd), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests the constructor taking in only an empty collection of {@link MailCommand.MailParameter}, its behavior should be set the
     * the sender as the empty string and have no additional arguments after or trailing spaces.
     *
     * @throws SmtpAsyncClientException will not throw
     * @throws IllegalAccessException will not throw
     */
    @Test
    public void testOnlyMailParameterEmpty() throws SmtpAsyncClientException, IllegalAccessException {
        final SmtpRequest cmd = new MailCommand(new LinkedList<>());
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII),
                "MAIL FROM:<>\r\n", "Expected results mismatched");
        cmd.cleanup();
        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(cmd), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests the constructor taking in a sender and an empty collection of {@link MailCommand.MailParameter}, its behavior should be
     * identical to the constructor without the mail parameters.
     *
     * @throws SmtpAsyncClientException will not throw
     * @throws IllegalAccessException will not throw
     */
    @Test
    public void testMailParameterEmpty() throws SmtpAsyncClientException, IllegalAccessException {
        final SmtpRequest cmd = new MailCommand("bob", new ArrayList<>());
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII),
                "MAIL FROM:<bob>\r\n", "Expected results mismatched");
        cmd.cleanup();
        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(cmd), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests the constructor taking in both the sender and a non-empty collection of {@link MailCommand.MailParameter}, the result
     * should follow the RFC standards.
     *
     * @throws SmtpAsyncClientException will not throw
     * @throws IllegalAccessException will not throw
     */
    @Test
    public void testMailParameter() throws SmtpAsyncClientException, IllegalAccessException {
        final SmtpRequest cmd = new MailCommand("bob", new ArrayList<MailCommand.MailParameter>() { {
            add(new MailCommand.MailParameter("AUTH", "value"));
            add(new MailCommand.MailParameter("Just_key"));
            add(new MailCommand.MailParameter("key2"));
        } });
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII),
                "MAIL FROM:<bob> AUTH=value Just_key key2\r\n", "Expected results mismatched");
        cmd.cleanup();
        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(cmd), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Another test for the constructor taking in both the sender and a non-empty collection of {@link MailCommand.MailParameter}, the result
     * should follow the RFC standards.
     *
     * @throws SmtpAsyncClientException will not throw
     * @throws IllegalAccessException will not throw
     */
    @Test
    public void testMailParameter2() throws SmtpAsyncClientException, IllegalAccessException {
        final SmtpRequest cmd = new MailCommand(new LinkedList<MailCommand.MailParameter>() { {
            add(new MailCommand.MailParameter("key1"));
            add(new MailCommand.MailParameter("key2"));
            add(new MailCommand.MailParameter("key3", "value3"));
        } });
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII),
                "MAIL FROM:<> key1 key2 key3=value3\r\n", "Expected results mismatched");
        cmd.cleanup();
        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(cmd), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests the {@code getCommandType} method.
     */
    @Test
    public void testGetCommandType() {
        Assert.assertSame(new MailCommand("example@verizonmedia.com").getCommandType(), SmtpCommandType.MAIL);
    }

    /**
     * Tests the {@code getNextCommandLineAfterContinuation} method.
     */
    @Test
    public void testGetNextCommandLineAfterContinuation() {
        final SmtpRequest cmd = new MailCommand("test.sender@example.net");
        try {
            cmd.getNextCommandLineAfterContinuation(new SmtpResponse("400 resp"));
            Assert.fail("Exception should've been thrown");
        } catch (SmtpAsyncClientException ex) {
            Assert.assertEquals(ex.getFailureType(), SmtpAsyncClientException.FailureType.OPERATION_NOT_SUPPORTED_FOR_COMMAND);
        }
    }

    /**
     * Tests the {@code getDebugData} method.
     */
    @Test
    public void testGetDebugData() {
        Assert.assertNull(new QuitCommand().getDebugData());
    }
}
