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
 * Unit test for {@link HelpCommand}.
 */
public class HelpCommandTest {

    /** Fields to check for cleanup. */
    private final Set<Field> fieldsToCheck = new HashSet<>();

    /**
     * Setup reflection.
     */
    @BeforeClass
    public void setUp() {
        final Class<?> classUnderTest = HelpCommand.class;
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
     * Tests the correctness of the command line content.
     *
     * @throws SmtpAsyncClientException will not throw in a successful test
     * @throws IllegalAccessException will not throw in a successful test
     */
    @Test
    public void testGetCommandLine() throws SmtpAsyncClientException, IllegalAccessException {
        final SmtpRequest cmd = new HelpCommand();
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII),
                "HELP\r\n", "Expected results mismatched");
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
        Assert.assertSame(new HelpCommand().getCommandType(), SmtpCommandType.HELP);
    }

    /**
     * Test the behavior of the {@code getNextCommandLineAfterContinuation} method, as well as the correctness of the corresponding exception thrown.
     */
    @Test
    public void testGetNextCommandLineAfterContinuation() {
        final SmtpRequest cmd = new HelpCommand();
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
