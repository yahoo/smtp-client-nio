/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.sun.mail.smtp.SMTPMessage;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.response.SmtpResponse;

/**
 * Unit test for {@link DataCommand}.
 */
public class DataCommandTest {

    /** Fields to check for cleanup. */
    private final Set<Field> fieldsToCheck = new HashSet<>();

    /**
     * Setup reflection.
     */
    @BeforeClass
    public void setUp() {
        final Class<?> classUnderTest = DataCommand.class;
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
     * Tests the trivial case of an empty message.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     * @throws MessagingException will not throw in this test
     */
    @Test
    public void testGetCommandLineEmpty() throws SmtpAsyncClientException, IllegalAccessException, MessagingException {
        SMTPMessage msg = new SMTPMessage((Session) null);
        msg.setText("");
        final SmtpRequest cmd = new DataCommand(msg);
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII), "DATA\r\n",
                "Expected results mismatched");
        final String message = cmd.getNextCommandLineAfterContinuation(new SmtpResponse("123-Not needed"))
                .toString(StandardCharsets.US_ASCII);
        Assert.assertTrue(message.endsWith("\r\n.\r\n"), "Incorrect ending");
        cmd.cleanup();
        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(cmd), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests the correctness of the constructor.
     *
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testConstructor() throws IllegalAccessException {
        SMTPMessage msg = new SMTPMessage((Session) null);
        final DataCommand cmd = new DataCommand(msg);
        Assert.assertSame(cmd.getMessage(), msg, "Wrong message");
        cmd.cleanup();
        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(cmd), "Cleanup should set " + field.getName() + " as null");
        }
    }


    /**
     * Tests the correctness of the header content in a message.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     * @throws MessagingException will not throw in this test
     */
    @Test
    public void testSetHeaders() throws SmtpAsyncClientException, IllegalAccessException, MessagingException {
        SMTPMessage msg = new SMTPMessage((Session) null);
        msg.setText("");

        msg.setHeader("Cc", "Eric");
        msg.setHeader("Bcc", "Alice");
        msg.setHeader("From", "a good friend");
        msg.setHeader("To", "my best friend");
        msg.setSubject("long time no see!", "UTF-8");

        final SmtpRequest cmd = new DataCommand(msg);

        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII), "DATA\r\n",
                "Expected results mismatched");
        final String message = cmd.getNextCommandLineAfterContinuation(new SmtpResponse("123-Not needed"))
                .toString(StandardCharsets.US_ASCII);

        Assert.assertTrue(message.contains("Cc: Eric\r\n"), "Output does not contain the expected string");
        Assert.assertTrue(message.contains("Bcc: Alice\r\n"), "Output does not contain the expected string");
        Assert.assertTrue(message.contains("From: a good friend\r\n"), "Output does not contain the expected string");
        Assert.assertTrue(message.contains("To: my best friend\r\n"), "Output does not contain the expected string");
        Assert.assertTrue(message.contains("Subject: long time no see!\r\n"), "Output does not contain the expected string");

        cmd.cleanup();
        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(cmd), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests for the correctness of the body content of a message.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     * @throws MessagingException will not throw in this test
     */
    @Test
    public void testSetBody() throws SmtpAsyncClientException, IllegalAccessException, MessagingException {
        SMTPMessage msg = new SMTPMessage((Session) null);
        msg.setText("Hi Bart,\n\nThis is a reminder that you have outstanding payments due soon.");
        final SmtpRequest cmd = new DataCommand(msg);
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII), "DATA\r\n",
                "Expected results mismatched");
        final String message = cmd.getNextCommandLineAfterContinuation(new SmtpResponse("123-Not needed"))
                .toString(StandardCharsets.US_ASCII);
        Assert.assertTrue(message.contains("\r\nHi Bart,\n\nThis is a reminder that you have outstanding payments due soon.\r\n.\r\n"),
                "Output does not contain the expected string");

        cmd.cleanup();
        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(cmd), "Cleanup should set " + field.getName() + " as null");
        }
    }

    /**
     * Tests the correctness of the body content of a message with non-ascii characters.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     * @throws IOException will not throw in this test
     * @throws MessagingException will not throw in this test
     */
    @Test
    public void testSetBodyComplex() throws SmtpAsyncClientException, IOException, MessagingException {
        final SMTPMessage msg = new SMTPMessage((Session) null);
        msg.setFrom(new InternetAddress("testing123@example.net", "John Smith"));
        msg.setSubject("Hello World!", String.valueOf(StandardCharsets.UTF_8));
        msg.setText("哈咯，这是一些中文测试子。कुछ हिंदी पाठ", String.valueOf(StandardCharsets.UTF_8));

        final DataCommand cmd = new DataCommand(msg);
        Assert.assertSame(msg, cmd.getMessage());
        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII), "DATA\r\n",
                "Expected results mismatched");

        // actual output
        final String message = cmd.getNextCommandLineAfterContinuation(new SmtpResponse("354 Go Ahead")).toString(StandardCharsets.UTF_8);

        // Expected
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        msg.writeTo(output);
        final String rawEmail = output.toString();

        Assert.assertEquals(message.substring(0, message.length() - 5), rawEmail); // strip away the terminating chars
        Assert.assertTrue(message.endsWith("\r\n.\r\n"));
    }

    /**
     * Tests the behavior of {@code getNextCommandLineAfterContinuation} when given a bad response.
     *
     * @throws IOException will not throw in this test
     * @throws MessagingException will not throw in this test
     */
    @Test
    public void testSmtpMessageFail() throws IOException, MessagingException, SmtpAsyncClientException {
        final SMTPMessage msg = Mockito.mock(SMTPMessage.class);
        final DataCommand cmd = new DataCommand(msg);

        Mockito.doThrow(new IOException()).when(msg).writeTo(Mockito.any());
        Assert.assertNull(cmd.getNextCommandLineAfterContinuation(new SmtpResponse("555 bad")));

        Mockito.doThrow(new MessagingException()).when(msg).writeTo(Mockito.any());
        Assert.assertNull(cmd.getNextCommandLineAfterContinuation(new SmtpResponse("555 bad")));
    }

    /**
     * Tests the {@code getCommandType} method.
     */
    @Test
    public void testGetCommandType() {
        Assert.assertSame(new DataCommand(new SMTPMessage((Session) null)).getCommandType(), SmtpCommandType.DATA);
    }

    /**
     * Tests the {@code isCommandLineSensitive} method.
     */
    @Test
    public void testIsCommandSensitive() {
        Assert.assertFalse(new DataCommand(new SMTPMessage((Session) null)).isCommandLineDataSensitive());
    }

    /**\
     * Tests the {@code getDebugData} method.
     */
    @Test
    public void testGetDebugData() {
        Assert.assertNull(new QuitCommand().getDebugData());
    }
}
