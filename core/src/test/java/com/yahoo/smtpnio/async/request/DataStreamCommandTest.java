/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.request;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import javax.mail.MessagingException;
import javax.mail.Session;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.sun.mail.smtp.SMTPMessage;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.response.SmtpResponse;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPromise;
import io.netty.handler.stream.ChunkedStream;

/**
 * Unit test for {@link DataCommand}.
 */
public class DataStreamCommandTest {

    /** Fields to check for cleanup. */
    private final Set<Field> fieldsToCheck = new HashSet<>();

    /**
     * Setup reflection.
     */
    @BeforeClass
    public void setUp() {
        final Class<?> classUnderTest = DataStreamCommand.class;
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
     * Tests the correctness of the constructor.
     *
     * @throws IllegalAccessException will not throw in this test
     */
    @Test
    public void testConstructor() throws IllegalAccessException {
        final ByteArrayInputStream payload = new ByteArrayInputStream("data".getBytes());
        final DataStreamCommand cmd = new DataStreamCommand(payload);
        Assert.assertSame(cmd.getMessage(), payload, "Wrong message");
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
     * @throws IOException will not throw in this test
     */
    @Test
    public void testSetHeaders() throws SmtpAsyncClientException, IllegalAccessException, MessagingException, IOException {
        SMTPMessage msg = new SMTPMessage((Session) null);
        msg.setText("");

        msg.setHeader("Cc", "Eric");
        msg.setHeader("Bcc", "Alice");
        msg.setHeader("From", "a good friend");
        msg.setHeader("To", "my best friend");
        msg.setSubject("long time no see!", "UTF-8");
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        msg.writeTo(byteArrayOutputStream);
        final SmtpRequest cmd = new DataStreamCommand(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));

        Assert.assertEquals(cmd.getCommandLineBytes().toString(StandardCharsets.US_ASCII), "DATA\r\n",
                "Expected results mismatched");
        final String message = cmd.getNextCommandLineAfterContinuation(new SmtpResponse("200 123-Not needed"))
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
     * Tests that errors are propagated.
     *
     * @throws IOException will not throw in this test
     */
    @Test
    public void testWritingFails() throws IOException {
        final SmtpRequest cmd = new DataStreamCommand(new FileInputStream(Files.createTempFile("prefix", "suffix").toFile()) {
            @Override
            public synchronized int read(final byte[] b) throws IOException {
                throw new IOException("Fails");
            }
        });

        Assert.assertThrows(new Assert.ThrowingRunnable() {
            @Override
            public void run() throws Throwable {
                cmd.getNextCommandLineAfterContinuation(new SmtpResponse("200 123-Not needed"));
            }
        });
    }

    /**
     * Tests the correctness of encodeCommandAfterContinuation.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     * @throws IllegalAccessException will not throw in this test
     * @throws MessagingException will not throw in this test
     * @throws IOException will not throw in this test
     */
    @Test
    public void testEncodeCommandAfterContinuation() throws SmtpAsyncClientException, IllegalAccessException, MessagingException, IOException {
        SMTPMessage msg = new SMTPMessage((Session) null);
        msg.setText("");

        msg.setHeader("Cc", "Eric");
        msg.setHeader("Bcc", "Alice");
        msg.setHeader("From", "a good friend");
        msg.setHeader("To", "my best friend");
        msg.setSubject("long time no see!", "UTF-8");
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        msg.writeTo(byteArrayOutputStream);
        final SmtpRequest cmd = new DataStreamCommand(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));

        final Channel channel = Mockito.mock(Channel.class);
        final ChannelPromise channelPromise = Mockito.mock(ChannelPromise.class);
        cmd.encodeCommandAfterContinuation(channel, new Supplier<ChannelPromise>() {
            @Override
            public ChannelPromise get() {
                return channelPromise;
            }
        }, Mockito.mock(SmtpResponse.class));

        Mockito.verify(channel, Mockito.times(1)).write(Mockito.any(ChunkedStream.class), Mockito.eq(channelPromise));
        Mockito.verify(channel, Mockito.times(1)).writeAndFlush(Mockito.any(ByteBuf.class));

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
        Assert.assertSame(new DataStreamCommand(new ByteArrayInputStream("".getBytes())).getCommandType(), SmtpRFCSupportedCommandType.DATA);
    }

    /**
     * Tests the {@code isCommandLineSensitive} method.
     */
    @Test
    public void testIsCommandSensitive() {
        Assert.assertTrue(new DataStreamCommand(new ByteArrayInputStream("".getBytes())).isCommandLineDataSensitive());
    }

    /**\
     * Tests the {@code getDebugData} method.
     */
    @Test
    public void testGetDebugData() {
        Assert.assertEquals(new DataStreamCommand(new ByteArrayInputStream("".getBytes())).getDebugData(), "DATA stream");
    }


    /**\
     * Tests the {@code cleanup} method when closing the stream fails.
     */
    @Test
    public void testCloseShouldSwallowFailure() {
        final ByteArrayInputStream message = new ByteArrayInputStream("".getBytes()) {
            /**
             * Just throw an exception
             *
             * @throws IOException just throws
             */
            @Override
            public void close() throws IOException {
                throw new IOException("exception");
            }
        };
        new DataStreamCommand(message).cleanup();
    }

}
