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
 * Unit test for {@link ExtendedHelloCommand}.
 */
public class ExtendedHelloCommandTest {

    /** Fields to check for cleanup. */
    private final Set<Field> fieldsToCheck = new HashSet<>();

    /**
     * Setup reflection.
     */
    @BeforeClass
    public void setUp() {
        final Class<?> classUnderTest = ExtendedHelloCommand.class;
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
     * Tests the properties of the command, including {@code isCommandLineDataSensitive} and {@code getCommandLineBytes}.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testGetCommandLine() throws SmtpAsyncClientException, IllegalAccessException {
        final SmtpRequest cmd1 = new ExtendedHelloCommand("User1");
        Assert.assertFalse(cmd1.isCommandLineDataSensitive());
        Assert.assertEquals(cmd1.getCommandLineBytes().toString(StandardCharsets.US_ASCII), "EHLO User1\r\n",
                "Expected results mismatched");
        cmd1.cleanup();

        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(cmd1), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Test the {@code getCommandType} method.
     */
    @Test
    public void testGetCommandType() {
        Assert.assertSame(new ExtendedHelloCommand("test.client.me").getCommandType(), SmtpRFCSupportedCommandType.EHLO, "Unexpected command type");
        Assert.assertEquals(SmtpRFCSupportedCommandType.EHLO, SmtpRFCSupportedCommandType.valueOf("EHLO"), "Unexpected command value");
    }

    /**
     * Test the behavior of the {@code getNextCommandLineAfterContinuation} method, as well as the correctness of the corresponding exception thrown.
     */
    @Test
    public void testGetNextCommandLineAfterContinuation() {
        SmtpRequest cmd = new ExtendedHelloCommand("John");
        try {
            cmd.getNextCommandLineAfterContinuation(new SmtpResponse("250 8BITMIME"));
            Assert.fail("Exception should've been thrown");
        } catch (final SmtpAsyncClientException ex) {
            Assert.assertEquals(ex.getFailureType(), SmtpAsyncClientException.FailureType.OPERATION_NOT_SUPPORTED_FOR_COMMAND);
        }
    }
}
