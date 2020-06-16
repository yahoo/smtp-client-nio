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
 * Unit test for {@link QuitCommand}.
 */
public class QuitCommandTest {

    /** Fields to check for cleanup. */
    private final Set<Field> fieldsToCheck = new HashSet<>();

    /**
     * Setup reflection.
     */
    @BeforeClass
    public void setUp() {
        final Class<?> classUnderTest = QuitCommand.class;
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
     * Tests the correctness of {@code getCommandLineBytes} and {@code isCommandLineDataSensitive}.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testGetCommandLine() throws SmtpAsyncClientException, IllegalAccessException {
        final SmtpRequest cmd = new QuitCommand();
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII), "QUIT\r\n", "Expected results mismatched");
        Assert.assertFalse(cmd.isCommandLineDataSensitive());
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
        Assert.assertSame(new QuitCommand().getCommandType(), SmtpRFCSupportedCommandType.QUIT, "Incorrect command type");
    }

    /**
     * Test the behavior of the {@code getNextCommandLineAfterContinuation} method, as well as the correctness of the corresponding exception thrown.
     */
    @Test
    public void testGetNextCommandLineAfterContinuation() {
        final SmtpRequest cmd = new QuitCommand();
        try {
            cmd.getNextCommandLineAfterContinuation(new SmtpResponse("221 goodbye"));
            Assert.fail("Exception should've been thrown");
        } catch (final SmtpAsyncClientException ex) {
            Assert.assertEquals(ex.getFailureType(), SmtpAsyncClientException.FailureType.OPERATION_NOT_SUPPORTED_FOR_COMMAND);
        }
    }

    /**
     * Tests the {@code getDebugData} method.
     */
    @Test
    public void testGetDebugData() {
        Assert.assertEquals(new QuitCommand().getDebugData(), "");
    }
}
