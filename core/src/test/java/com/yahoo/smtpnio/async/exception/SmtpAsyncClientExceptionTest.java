/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.exception;

import java.io.IOException;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Unit test for {@link SmtpAsyncClientException}.
 */
public class SmtpAsyncClientExceptionTest {

    /**
     * Tests the single argument constructor of {@link SmtpAsyncClientException}.
     */
    @Test
    public void testSmtpAsyncClientException() {
        final SmtpAsyncClientException.FailureType failureType = SmtpAsyncClientException.FailureType.CHANNEL_DISCONNECTED;
        final SmtpAsyncClientException resp = new SmtpAsyncClientException(failureType);

        Assert.assertEquals(resp.getFailureType(), failureType, "result mismatched.");
        Assert.assertNull(resp.getCause(), "cause of exception mismatched.");
        Assert.assertEquals(resp.getMessage(), "failureType=" + failureType.name(), "result mismatched.");
    }

    /**
     * Tests the {@link SmtpAsyncClientException} constructor taking in a {@link Throwable}.
     */
    @Test
    public void testSmtpAsyncClientExceptionWithFailureTypeAndCause() {
        final SmtpAsyncClientException.FailureType failureType = SmtpAsyncClientException.FailureType.WRITE_TO_SERVER_FAILED;
        final IOException cause = new IOException("Failure in IO!");
        final SmtpAsyncClientException ex = new SmtpAsyncClientException(failureType, cause);

        Assert.assertEquals(ex.getFailureType(), failureType, "result mismatched.");
        Assert.assertEquals(ex.getMessage(), "failureType=WRITE_TO_SERVER_FAILED", "result mismatched.");
        Assert.assertEquals(ex.getCause(), cause, "cause of exception mismatched.");
    }

    /**
     * Tests the {@link SmtpAsyncClientException} constructor taking in session information.
     */
    @Test
    public void testSmtpAsyncClientExceptionWithSessionIdClientContext() {
        final SmtpAsyncClientException.FailureType failureType = SmtpAsyncClientException.FailureType.OPERATION_NOT_SUPPORTED_FOR_COMMAND;
        final Long sessionId = 5L;
        final String sessCtx = "T123riceratops123@scar123y.com";
        final SmtpAsyncClientException ex = new SmtpAsyncClientException(failureType, sessionId, sessCtx);

        Assert.assertEquals(ex.getFailureType(), failureType, "result mismatched.");
        Assert.assertNull(ex.getCause(), "cause of exception mismatched.");
        Assert.assertEquals(ex.getMessage(), "failureType=OPERATION_NOT_SUPPORTED_FOR_COMMAND,sId=5,uId=T123riceratops123@scar123y.com",
                "result mismatched.");
    }

    /**
     * Tests the {@link SmtpAsyncClientException} constructor taking in session information.
     */
    @Test
    public void testSmtpAsyncClientExceptionWithSessionIdClientContextAndMessage() {
        final SmtpAsyncClientException.FailureType failureType = SmtpAsyncClientException.FailureType.CONNECTION_FAILED_INVALID_GREETING_CODE;
        final Long sessionId = 5L;
        final String sessCtx = "abctest@example.org";
        final SmtpAsyncClientException ex = new SmtpAsyncClientException(failureType, sessionId, sessCtx, "Something went wrong");

        Assert.assertEquals(ex.getFailureType(), failureType, "result mismatched.");
        Assert.assertNull(ex.getCause(), "cause of exception mismatched.");
        Assert.assertEquals(ex.getMessage(),
                "failureType=CONNECTION_FAILED_INVALID_GREETING_CODE,sId=5,uId=abctest@example.org,message=Something went wrong",
                "result mismatched.");
    }


    /**
     * Tests the {@link SmtpAsyncClientException} constructor taking in the failure type and message.
     */
    @Test
    public void testMessageConstructorAndGetter() {
        final SmtpAsyncClientException ex = new SmtpAsyncClientException(SmtpAsyncClientException.FailureType.INVALID_INPUT, "your message here!");
        Assert.assertEquals(ex.getFailureType(), SmtpAsyncClientException.FailureType.INVALID_INPUT);
        Assert.assertEquals(ex.getMessage(), "failureType=INVALID_INPUT,message=your message here!");
    }

    /**
     * Tests the {@link SmtpAsyncClientException} constructor taking in every possible parameter.
     */
    @Test
    public void testSmtpAsyncClientExceptionWithAllParameters() {
        final SmtpAsyncClientException.FailureType failureType = SmtpAsyncClientException.FailureType.INVALID_INPUT;
        final IOException cause = new IOException("Failure in IO!");
        final Long sessionId = 7L;
        final String sessCtx = "unit.test@testing.org";
        final String message = "please check your input.";
        final SmtpAsyncClientException ex = new SmtpAsyncClientException(failureType, cause, sessionId, sessCtx, message);

        Assert.assertEquals(ex.getFailureType(), failureType, "result mismatched.");
        Assert.assertEquals(ex.getCause(), cause, "cause of exception mismatched.");
        Assert.assertEquals(ex.getMessage(), "failureType=INVALID_INPUT,sId=7,uId=unit.test@testing.org,message=please check your input.",
                "result mismatched.");
    }

    /**
     * Tests the correctness of {@link SmtpAsyncClientException.FailureType}.
     */
    @Test
    public void testFailureType() {
        final SmtpAsyncClientException.FailureType failureType = SmtpAsyncClientException.FailureType.valueOf("CHANNEL_TIMEOUT");
        Assert.assertEquals(failureType, SmtpAsyncClientException.FailureType.CHANNEL_TIMEOUT, "result mismatched.");
    }
}
