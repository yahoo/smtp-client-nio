/*
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.response.SmtpResponse;

/**
 * Unit test verifying that commands reject control characters in caller-supplied arguments, so that such an argument cannot
 * terminate the command line and inject a second command of the caller's choosing.
 */
public class CommandArgumentValidationTest {

    /** A sender/recipient address carrying an injected RCPT TO command. */
    private static final String ADDRESS_WITH_INJECTED_COMMAND = "a@b.com>\r\nRCPT TO:<victim@target.test";

    /** A greeting carrying an injected MAIL FROM command. */
    private static final String GREETING_WITH_INJECTED_COMMAND = "x\r\nMAIL FROM:<spoof@target.test>";

    /** A server continuation prompting for the next AUTH LOGIN input. */
    private static final String CONTINUATION = "334 VXNlcm5hbWU6";

    /**
     * Asserts that building the command line fails with an INVALID_INPUT error rather than emitting the argument.
     *
     * @param request the command expected to reject its argument
     * @return the exception thrown, for further assertions
     */
    private SmtpAsyncClientException assertRejected(final SmtpRequest request) {
        final SmtpAsyncClientException e = Assert.expectThrows(SmtpAsyncClientException.class, request::getCommandLineBytes);
        Assert.assertEquals(e.getFailureType(), SmtpAsyncClientException.FailureType.INVALID_INPUT, "Expected results mismatched");
        return e;
    }

    /**
     * Tests that a CRLF in the sender is rejected instead of being written to the wire.
     */
    @Test
    public void testMailCommandRejectsInjectedCommand() {
        assertRejected(new MailCommand(ADDRESS_WITH_INJECTED_COMMAND));
    }

    /**
     * Tests that a CRLF in a mail parameter is rejected. The parameters are appended to the MAIL FROM line after the sender.
     */
    @Test
    public void testMailParameterRejectsInjectedCommand() {
        assertRejected(new MailCommand("user@yahoo.com", Collections.singletonList(new MailCommand.MailParameter("SIZE\r\nRSET"))));
        assertRejected(new MailCommand("user@yahoo.com", Collections.singletonList(new MailCommand.MailParameter("SIZE", "1000\r\nRSET"))));
    }

    /**
     * Tests that a CRLF in the recipient is rejected instead of being written to the wire.
     */
    @Test
    public void testRecipientCommandRejectsInjectedCommand() {
        assertRejected(new RecipientCommand(ADDRESS_WITH_INJECTED_COMMAND));
    }

    /**
     * Tests that a CRLF in the HELO greeting is rejected instead of being written to the wire.
     */
    @Test
    public void testHelloCommandRejectsInjectedCommand() {
        assertRejected(new HelloCommand(GREETING_WITH_INJECTED_COMMAND));
    }

    /**
     * Tests that a CRLF in the EHLO greeting is rejected instead of being written to the wire.
     */
    @Test
    public void testExtendedHelloCommandRejectsInjectedCommand() {
        assertRejected(new ExtendedHelloCommand(GREETING_WITH_INJECTED_COMMAND));
    }

    /**
     * Tests that the AUTH LOGIN credentials are rejected, they are each sent verbatim as their own command line.
     *
     * @throws SmtpAsyncClientException will not throw for the accepted username
     */
    @Test
    public void testAuthenticationLoginCommandRejectsInjectedCommand() throws SmtpAsyncClientException {
        final AuthenticationLoginCommand badUser = new AuthenticationLoginCommand("dXNlcg==\r\nRSET", "cGFzcw==");
        final SmtpAsyncClientException userError = Assert.expectThrows(SmtpAsyncClientException.class,
                () -> badUser.getNextCommandLineAfterContinuation(new SmtpResponse(CONTINUATION)));
        Assert.assertEquals(userError.getFailureType(), SmtpAsyncClientException.FailureType.INVALID_INPUT, "Expected results mismatched");

        // the password is only reached on the second continuation, after the username has been accepted
        final AuthenticationLoginCommand badPassword = new AuthenticationLoginCommand("dXNlcg==", "cGFzcw==\r\nRSET");
        Assert.assertEquals(badPassword.getNextCommandLineAfterContinuation(new SmtpResponse(CONTINUATION)).toString(StandardCharsets.US_ASCII),
                "dXNlcg==\r\n", "Expected results mismatched");
        final SmtpAsyncClientException passwordError = Assert.expectThrows(SmtpAsyncClientException.class,
                () -> badPassword.getNextCommandLineAfterContinuation(new SmtpResponse(CONTINUATION)));
        Assert.assertEquals(passwordError.getFailureType(), SmtpAsyncClientException.FailureType.INVALID_INPUT, "Expected results mismatched");
    }

    /**
     * Tests that a SOH in the XOAUTH2 arguments is rejected, it would otherwise inject a field into the SASL payload.
     */
    @Test
    public void testAuthenticationXoauth2CommandRejectsFieldInjection() {
        assertRejected(new AuthenticationXoauth2Command("victim@target.test\u0001auth=Bearer attacker", "token"));
        assertRejected(new AuthenticationXoauth2Command("user@target.test", "token\u0001auth=Bearer x"));
    }

    /**
     * Tests that a NULL in the AUTH PLAIN arguments is rejected, it would otherwise inject a field into the SASL payload.
     */
    @Test
    public void testAuthenticationPlainCommandRejectsFieldInjection() {
        assertRejected(new AuthenticationPlainCommand("authzid\0attacker", "user", "pass"));
        assertRejected(new AuthenticationPlainCommand(null, "user\0attacker", "pass"));
        assertRejected(new AuthenticationPlainCommand(null, "user", "pass\0attacker"));
    }

    /**
     * Tests that legitimate arguments are unaffected and still produce exactly one command line.
     *
     * @throws SmtpAsyncClientException will not throw
     */
    @Test
    public void testValidArgumentsStillAccepted() throws SmtpAsyncClientException {
        Assert.assertEquals(new MailCommand("user1.test@yahoo.com").getCommandLineBytes().toString(StandardCharsets.US_ASCII),
                "MAIL FROM:<user1.test@yahoo.com>\r\n", "Expected results mismatched");
        Assert.assertEquals(
                new MailCommand("user1.test@yahoo.com", Collections.singletonList(new MailCommand.MailParameter("SIZE", "1000")))
                        .getCommandLineBytes().toString(StandardCharsets.US_ASCII),
                "MAIL FROM:<user1.test@yahoo.com> SIZE=1000\r\n", "Expected results mismatched");
        Assert.assertEquals(new RecipientCommand("user2.test@yahoo.com").getCommandLineBytes().toString(StandardCharsets.US_ASCII),
                "RCPT TO:<user2.test@yahoo.com>\r\n", "Expected results mismatched");
        Assert.assertEquals(new HelloCommand("client.example.com").getCommandLineBytes().toString(StandardCharsets.US_ASCII),
                "HELO client.example.com\r\n", "Expected results mismatched");
        Assert.assertEquals(new ExtendedHelloCommand("client.example.com").getCommandLineBytes().toString(StandardCharsets.US_ASCII),
                "EHLO client.example.com\r\n", "Expected results mismatched");
        Assert.assertEquals(new AuthenticationPlainCommand(null, "Alice", "Apple").getCommandLineBytes().toString(StandardCharsets.US_ASCII),
                "AUTH PLAIN AEFsaWNlAEFwcGxl\r\n", "Expected results mismatched");

        final AuthenticationLoginCommand login = new AuthenticationLoginCommand("dXNlcg==", "cGFzcw==");
        Assert.assertEquals(login.getNextCommandLineAfterContinuation(new SmtpResponse(CONTINUATION)).toString(StandardCharsets.US_ASCII),
                "dXNlcg==\r\n", "Expected results mismatched");
    }
}
