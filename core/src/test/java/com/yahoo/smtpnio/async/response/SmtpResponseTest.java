/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.response;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;

/**
 * Unit test for {@link SmtpResponse}.
 */
public class SmtpResponseTest {

    /** Test objects for this suite. */
    private SmtpResponse response1, response2, response3, response4, response5, response6;

    /**
     * Initializes the responses for the tests.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     */
    @BeforeClass
    public void setUp() throws SmtpAsyncClientException {
        response1 = new SmtpResponse("250 smtp.gmail.com at your service");
        response2 = new SmtpResponse("502 5.5.1 Unrecognized command. k4sm7825243pfg.40 - gsmtp");
        response3 = new SmtpResponse("354  Go ahead gx2sm7609749pjb.18 - gsmtp");
        response4 = new SmtpResponse("221 2.0.0 closing connection k4sm7825243pfg.40 - gsmtp");
        response5 = new SmtpResponse("250-8BITMIME");
        response6 = new SmtpResponse("421 Service not available, check your connection");
    }

    /**
     * Tests the correctness of the reply codes via method {@code getCode}.
     */
    @Test
    public void testResponseCode() {
        Assert.assertEquals(response1.getCode().value(), 250, "Wrong response code!");
        Assert.assertEquals(response2.getCode().value(), 502, "Wrong response code!");
        Assert.assertEquals(response3.getCode().value(), 354, "Wrong response code!");
        Assert.assertEquals(response4.getCode().value(), 221, "Wrong response code!");
        Assert.assertEquals(response5.getCode().value(), 250, "Wrong response code!");
        Assert.assertEquals(response6.getCode().value(), 421, "Wrong response code!");
    }

    /**
     * Tests the correctness of the flags {@code isPositiveCompletionReply}, {@code isNegativeTransientReply}, {@code isPositiveIntermediateReply},
     * {@code isNegativePermanentReply}, {@code isContinuation} and {@code isLastLineResponse}.
     */
    @Test
    public void testFlags() {
        Assert.assertEquals(response1.getReplyType(), SmtpResponse.ReplyType.POSITIVE_COMPLETION);
        Assert.assertFalse(response1.isContinuation());
        Assert.assertTrue(response1.isLastLineResponse());

        Assert.assertEquals(response2.getReplyType(), SmtpResponse.ReplyType.NEGATIVE_PERMANENT);
        Assert.assertFalse(response2.isContinuation());
        Assert.assertTrue(response2.isLastLineResponse());

        Assert.assertEquals(response3.getReplyType(), SmtpResponse.ReplyType.POSITIVE_INTERMEDIATE);
        Assert.assertTrue(response3.isContinuation());
        Assert.assertTrue(response3.isLastLineResponse());

        Assert.assertEquals(response4.getReplyType(), SmtpResponse.ReplyType.POSITIVE_COMPLETION);
        Assert.assertFalse(response4.isContinuation());
        Assert.assertTrue(response4.isLastLineResponse());

        Assert.assertEquals(response5.getReplyType(), SmtpResponse.ReplyType.POSITIVE_COMPLETION);
        Assert.assertFalse(response5.isContinuation());
        Assert.assertFalse(response5.isLastLineResponse());

        Assert.assertEquals(response6.getReplyType(), SmtpResponse.ReplyType.NEGATIVE_TRANSIENT);
        Assert.assertFalse(response6.isContinuation());
        Assert.assertTrue(response6.isLastLineResponse());
    }

    /**
     * Tests the {@code getResponse} method.
     */
    @Test
    public void testGetResponses() {
        Assert.assertEquals(response1.toString(), "250 smtp.gmail.com at your service", "Wrong response lines");
        Assert.assertEquals(response2.toString(), "502 5.5.1 Unrecognized command. k4sm7825243pfg.40 - gsmtp", "Wrong response lines");
        Assert.assertEquals(response3.toString(), "354  Go ahead gx2sm7609749pjb.18 - gsmtp", "Wrong response lines");
        Assert.assertEquals(response4.toString(), "221 2.0.0 closing connection k4sm7825243pfg.40 - gsmtp", "Wrong response lines");
        Assert.assertEquals(response5.toString(), "250-8BITMIME", "Wrong response lines");
        Assert.assertEquals(response6.toString(), "421 Service not available, check your connection", "Wrong response lines");
    }

    /**
     * Tests the {@code getResponseMessage} method.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     */
    @Test
    public void testGetResponseMessage() throws SmtpAsyncClientException {
        Assert.assertEquals(response1.getMessage(), "smtp.gmail.com at your service", "Wrong response message");
        Assert.assertEquals(response2.getMessage(), "5.5.1 Unrecognized command. k4sm7825243pfg.40 - gsmtp", "Wrong response message");
        Assert.assertEquals(response3.getMessage(), " Go ahead gx2sm7609749pjb.18 - gsmtp", "Wrong response message");
        Assert.assertEquals(response4.getMessage(), "2.0.0 closing connection k4sm7825243pfg.40 - gsmtp", "Wrong response message");
        Assert.assertEquals(response5.getMessage(), "8BITMIME", "Wrong response message");
        Assert.assertEquals(response6.getMessage(), "Service not available, check your connection", "Wrong response lines");
        Assert.assertEquals(new SmtpResponse("445").getMessage(), "", "Wrong response message");
        Assert.assertEquals(new SmtpResponse("543 ").getMessage(), "", "Wrong response message");
        Assert.assertEquals(new SmtpResponse("250-").getMessage(), "", "Wrong response message");
        Assert.assertEquals(new SmtpResponse("234-A").getMessage(), "A", "Wrong response message");
        Assert.assertEquals(new SmtpResponse("234 B").getMessage(), "B", "Wrong response message");
        Assert.assertEquals(new SmtpResponse("234- ").getMessage(), " ", "Wrong response message");
        Assert.assertEquals(new SmtpResponse("234  ").getMessage(), " ", "Wrong response message");
    }

    /**
     * Tests the correctness of different {@link SmtpResponse.Code}s.
     *
      @throws SmtpAsyncClientException will not throw in this test
     */
    @Test
    public void testSpecialResponseCodes() throws SmtpAsyncClientException {
        final SmtpResponse help = new SmtpResponse("214 2.0.0 help message here - smtp");
        Assert.assertEquals(help.getCode().value(), SmtpResponse.Code.HELP, "Response codes do not match");

        final SmtpResponse greeting = new SmtpResponse("220 smtp.mail.yahoo.com ESMTP ready");
        Assert.assertEquals(greeting.getCode().value(), SmtpResponse.Code.GREETING, "Response codes do not match");
        Assert.assertEquals(response1.getCode().value(), SmtpResponse.Code.EHLO_SUCCESS, "Response codes do not match");
        Assert.assertEquals(response3.getCode().value(), SmtpResponse.Code.START_MSG_INPUT, "Response codes do not match");
        Assert.assertEquals(response4.getCode().value(), SmtpResponse.Code.CLOSING, "Response codes do not match");
    }

    /**
     * Tests the behavior of a very short response (just the reply code); this is considered valid.
     *
     @throws SmtpAsyncClientException will not throw in this test
     */
    @Test
    public void testIsLastResponseShortResponse() throws SmtpAsyncClientException {
        final SmtpResponse response = new SmtpResponse("200");
        Assert.assertTrue(response.isLastLineResponse());
        Assert.assertEquals(response.getCode().value(), 200);
        Assert.assertEquals(response.getMessage(), "");
    }

    /**
     * Test the behavior of bad input to the constructor.
     *
      @throws SmtpAsyncClientException expected to throw in this test due to invalid input
     */
    @Test(expectedExceptions = SmtpAsyncClientException.class)
    public void testInvalidSmtpAsyncResponseNoCode() throws SmtpAsyncClientException {
        new SmtpResponse("there should be a reply code");
    }

    /**
     * Test the behavior of bad input to the constructor.
     *
      @throws SmtpAsyncClientException expected to throw in this test due to invalid input
     */
    @Test(expectedExceptions = SmtpAsyncClientException.class)
    public void testInvalidSmtpAsyncResponseCodeTooShortNoMessage() throws SmtpAsyncClientException {
        new SmtpResponse("20");
    }

    /**
     * Test the behavior of bad input to the constructor.
     *
      @throws SmtpAsyncClientException expected to throw in this test due to invalid input
     */
    @Test(expectedExceptions = SmtpAsyncClientException.class)
    public void testInvalidSmtpAsyncResponseCodeTooShortWithMessage() throws SmtpAsyncClientException {
        new SmtpResponse("21 Code should be 3 digits");
    }

    /**
     * Test the behavior of bad input to the constructor.
     *
      @throws SmtpAsyncClientException expected to throw in this test due to invalid input
     */
    @Test(expectedExceptions = SmtpAsyncClientException.class)
    public void testInvalidSmtpAsyncResponseBadCodeNegative() throws SmtpAsyncClientException {
        new SmtpResponse("-21 Code should be positive");
    }

    /**
     * Test the behavior of bad input to the constructor.
     *
      @throws SmtpAsyncClientException expected to throw in this test due to invalid input
     */
    @Test(expectedExceptions = SmtpAsyncClientException.class)
    public void testInvalidSmtpAsyncResponseBadCodeFirstDigit() throws SmtpAsyncClientException {
        new SmtpResponse("122 First digit should be 2-5");
    }

    /**
     * Test the behavior of bad input to the constructor.
     *
      @throws SmtpAsyncClientException expected to throw in this test due to invalid input
     */
    @Test(expectedExceptions = SmtpAsyncClientException.class)
    public void testInvalidSmtpAsyncResponseBadCodeFirstDigit2() throws SmtpAsyncClientException {
        new SmtpResponse("622 First digit should be 2-5");
    }

    /**
     * Test the behavior of bad input to the constructor.
     *
      @throws SmtpAsyncClientException expected to throw in this test due to invalid input
     */
    @Test(expectedExceptions = SmtpAsyncClientException.class)
    public void testInvalidSmtpAsyncResponseBadCodeSecondDigit() throws SmtpAsyncClientException {
        new SmtpResponse("3*2 Second digit should be 0-5");
    }

    /**
     * Test the behavior of bad input to the constructor.
     *
      @throws SmtpAsyncClientException expected to throw in this test due to invalid input
     */
    @Test(expectedExceptions = SmtpAsyncClientException.class)
    public void testInvalidSmtpAsyncResponseBadCodeSecondDigit2() throws SmtpAsyncClientException {
        new SmtpResponse("372 Second digit should be 0-5");
    }

    /**
     * Test the behavior of bad input to the constructor.
     *
      @throws SmtpAsyncClientException expected to throw in this test due to invalid input
     */
    @Test(expectedExceptions = SmtpAsyncClientException.class)
    public void testInvalidSmtpAsyncResponseBadCodeThirdDigit() throws SmtpAsyncClientException {
        new SmtpResponse("32+ Third digit should be 0-9");
    }

    /**
     * Test the behavior of bad input to the constructor.
     *
      @throws SmtpAsyncClientException expected to throw in this test due to invalid input
     */
    @Test(expectedExceptions = SmtpAsyncClientException.class)
    public void testInvalidSmtpAsyncResponseBadCodeThirdDigit2() throws SmtpAsyncClientException {
        new SmtpResponse("33X Third digit should be 0-9");
    }

    /**
     * Test the behavior of bad input to the constructor.
     *
      @throws SmtpAsyncClientException expected to throw in this test due to invalid input
     */
    @Test(expectedExceptions = SmtpAsyncClientException.class)
    public void testInvalidSmtpAsyncResponseBadCodeFourthChar() throws SmtpAsyncClientException {
        new SmtpResponse("2218 Fourth char should be space or hyphen if present");
    }

    /**
     * Test the behavior of bad input to the constructor.
     *
      @throws SmtpAsyncClientException expected to throw in this test due to invalid input
     */
    @Test(expectedExceptions = SmtpAsyncClientException.class)
    public void testInvalidSmtpAsyncResponseBadCodeFourthChar2() throws SmtpAsyncClientException {
        new SmtpResponse("220There should be a space or hyphen after the code");
    }
}
