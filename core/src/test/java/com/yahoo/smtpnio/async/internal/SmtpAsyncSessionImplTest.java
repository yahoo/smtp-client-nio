/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.internal;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.yahoo.smtpnio.async.client.SmtpAsyncSession;
import com.yahoo.smtpnio.async.client.SmtpAsyncSession.DebugMode;
import com.yahoo.smtpnio.async.client.SmtpFuture;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException.FailureType;
import com.yahoo.smtpnio.async.internal.SmtpAsyncSessionImpl.SmtpChannelClosedListener;
import com.yahoo.smtpnio.async.request.ExtendedHelloCommand;
import com.yahoo.smtpnio.async.request.QuitCommand;
import com.yahoo.smtpnio.async.request.SmtpRequest;
import com.yahoo.smtpnio.async.response.SmtpAsyncResponse;
import com.yahoo.smtpnio.async.response.SmtpResponse;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * Unit test for {@link SmtpAsyncSessionImpl}.
 */
public class SmtpAsyncSessionImplTest {

    /** Dummy session id. */
    private static final long SESSION_ID = 123456L;

    /** Dummy user id. */
    private static final String USER_ID = "john.smith@classical.user";

    /** Timeout in milliseconds for getting the future. */
    private static final long FUTURE_GET_TIMEOUT_MILLIS = 5L;

    /**
     * Tests the whole life cycle flow: construct the session, execute, handle server response success, close session.
     *
     * @throws SmtpAsyncClientException will not throw
     * @throws TimeoutException will not throw
     * @throws ExecutionException will not throw
     * @throws InterruptedException will not throw
     */
    @Test
    public void testHandleChannelResponse() throws SmtpAsyncClientException, InterruptedException, ExecutionException, TimeoutException {

        final Channel channel = Mockito.mock(Channel.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(channel.pipeline()).thenReturn(pipeline);
        Mockito.when(channel.isActive()).thenReturn(true);
        final ChannelPromise authWritePromise = Mockito.mock(ChannelPromise.class);
        final ChannelPromise authWritePromise2 = Mockito.mock(ChannelPromise.class);
        final ChannelPromise authWritePromise3 = Mockito.mock(ChannelPromise.class);
        final ChannelPromise closePromise = Mockito.mock(ChannelPromise.class);
        Mockito.when(channel.newPromise()).thenReturn(authWritePromise).thenReturn(authWritePromise2).thenReturn(authWritePromise3)
                .thenReturn(closePromise);

        final Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isDebugEnabled()).thenReturn(false);
        Mockito.when(logger.isTraceEnabled()).thenReturn(false);

        // construct, both class level and session level debugging are off
        final SmtpAsyncSessionImpl aSession = new SmtpAsyncSessionImpl(channel, logger, DebugMode.DEBUG_OFF, SESSION_ID, pipeline, USER_ID);

        // Simulate an authentication command with a mock since we do not have the actual Authenticate command yet
        {
            final SmtpRequest cmd = Mockito.mock(SmtpRequest.class);
            Mockito.when(cmd.getCommandLineBytes()).thenReturn(Unpooled.buffer().writeBytes("AUTH PLAIN\r\n".getBytes(StandardCharsets.US_ASCII)));

            final SmtpFuture<SmtpAsyncResponse> future = aSession.execute(cmd);
            Mockito.verify(authWritePromise, Mockito.times(1)).addListener(Mockito.any(SmtpAsyncSessionImpl.class));
            Mockito.verify(channel, Mockito.times(1)).writeAndFlush(Mockito.anyString(), Mockito.isA(ChannelPromise.class));
            Mockito.verify(logger, Mockito.times(0)).debug(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                    Mockito.anyString());

            // simulate write to server completed successfully
            Mockito.when(authWritePromise.isSuccess()).thenReturn(true);
            aSession.operationComplete(authWritePromise);

            // handle server response
            final SmtpResponse serverResp1 = new SmtpResponse("334 enter key");

            // following will call getNextCommandLineAfterContinuation
            Mockito.when(cmd.getNextCommandLineAfterContinuation(serverResp1))
                    .thenReturn(Unpooled.buffer().writeBytes("my_passphrase_here_in_base64".getBytes(StandardCharsets.US_ASCII)));
            aSession.handleChannelResponse(serverResp1);
            Mockito.verify(channel, Mockito.times(2)).writeAndFlush(Mockito.anyString(), Mockito.isA(ChannelPromise.class));

            final SmtpResponse serverResp2 = new SmtpResponse("235 accepted");
            aSession.handleChannelResponse(serverResp2);

            // verify that future should be done now
            Assert.assertTrue(future.isDone(), "isDone() should be true now");
            final SmtpAsyncResponse asyncResp = future.get(FUTURE_GET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            final Collection<SmtpResponse> lines = asyncResp.getResponseLines();
            Assert.assertEquals(lines.size(), 2, "responses count mismatched.");
            final Iterator<SmtpResponse> it = lines.iterator();
            final SmtpResponse continuationResp = it.next();
            Assert.assertNotNull(continuationResp, "Result mismatched.");
            Assert.assertTrue(continuationResp.isContinuation(), "Response.isContinuation() mismatched.");
            final SmtpResponse endingResp = it.next();
            Assert.assertNotNull(endingResp, "Result mismatched.");
            Assert.assertSame(endingResp.getReplyType(), SmtpResponse.ReplyType.POSITIVE_COMPLETION,
                    "Response.isPositiveCompletionReply() mismatched.");
            //Assert.assertEquals(endingResp.getTag(), "a1", "tag mismatched.");
            // verify no log messages
            Mockito.verify(logger, Mockito.times(0)).debug(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                    Mockito.anyString());
        }

        {
            // setting debug on for the session
            Mockito.when(logger.isDebugEnabled()).thenReturn(true);
            aSession.setDebugMode(DebugMode.DEBUG_ON);

            final SmtpRequest cmd = new ExtendedHelloCommand("User1");
            final SmtpFuture<SmtpAsyncResponse> future = aSession.execute(cmd);

            Mockito.verify(authWritePromise3, Mockito.times(1)).addListener(Mockito.any(SmtpAsyncSessionImpl.class));
            Mockito.verify(channel, Mockito.times(3)).writeAndFlush(Mockito.anyString(), Mockito.isA(ChannelPromise.class));
            Mockito.verify(logger, Mockito.times(1)).debug(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                    Mockito.anyString());

            // simulate write to server completed successfully
            Mockito.when(authWritePromise3.isSuccess()).thenReturn(true);
            aSession.operationComplete(authWritePromise3);

            // handle server response
            final SmtpResponse serverRespGreet = new SmtpResponse("250-myDomain.com welcome!");
            aSession.handleChannelResponse(serverRespGreet);

            final SmtpResponse serverResp1 = new SmtpResponse("250-AUTH LOGIN PLAIN XOAUTH2 PLAIN-CLIENTTOKEN OAUTHBEARER XOAUTH");
            aSession.handleChannelResponse(serverResp1);

            final SmtpResponse serverResp2 = new SmtpResponse("250 SMTPUTF8");
            aSession.handleChannelResponse(serverResp2);

            // verify that future should be done now
            Assert.assertTrue(future.isDone(), "isDone() should be true now");
            final SmtpAsyncResponse asyncResp = future.get(FUTURE_GET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

            final Collection<SmtpResponse> lines = asyncResp.getResponseLines();
            Assert.assertEquals(lines.size(), 3, "responses count mismatched.");
            final Iterator<SmtpResponse> it = lines.iterator();

            final SmtpResponse greetingResp = it.next();
            Assert.assertFalse(greetingResp.isLastLineResponse());
            Assert.assertFalse(greetingResp.isContinuation());

            final SmtpResponse extensionsResp = it.next();
            Assert.assertNotNull(extensionsResp, "Result mismatched.");
            Assert.assertFalse(extensionsResp.isLastLineResponse());
            Assert.assertFalse(extensionsResp.isContinuation(), "Response.isContinuation() mismatched.");

            final SmtpResponse endingResp = it.next();
            Assert.assertNotNull(endingResp, "Result mismatched.");
            Assert.assertSame(endingResp.getReplyType(), SmtpResponse.ReplyType.POSITIVE_COMPLETION,
                    "Response.isPositiveCompletionReply() mismatched.");
            Assert.assertTrue(endingResp.isLastLineResponse(), "Ending response should be the last response");
            // verify logging messages
            final ArgumentCaptor<Object> allArgsCapture = ArgumentCaptor.forClass(Object.class);
            Mockito.verify(logger, Mockito.times(4)).debug(Mockito.anyString(), allArgsCapture.capture(),
                    allArgsCapture.capture(), allArgsCapture.capture());

            // since it is vargs, 4 calls with 3 parameters all accumulate to one list
            final List<Object> logArgs = allArgsCapture.getAllValues();
            Assert.assertNotNull(logArgs, "log messages mismatched.");
            Assert.assertEquals(logArgs.size(), 12, "log messages mismatched.");

            Assert.assertEquals(logArgs.get(0), SESSION_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(1), USER_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(2), "EHLO User1\r\n", "log messages from client mismatched.");

            Assert.assertEquals(logArgs.get(3), SESSION_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(4), USER_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(5), "250-myDomain.com welcome!");

            Assert.assertEquals(logArgs.get(6), SESSION_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(7), USER_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(8), "250-AUTH LOGIN PLAIN XOAUTH2 PLAIN-CLIENTTOKEN OAUTHBEARER XOAUTH",
                    "Error message mismatched.");

            Assert.assertEquals(logArgs.get(9), SESSION_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(10), USER_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(11), "250 SMTPUTF8", "Error message mismatched.");
        }

        // perform close session
        Mockito.when(closePromise.isSuccess()).thenReturn(true);
        final SmtpFuture<Boolean> closeFuture = aSession.close();
        final ArgumentCaptor<SmtpChannelClosedListener> listenerCaptor = ArgumentCaptor.forClass(SmtpChannelClosedListener.class);
        Mockito.verify(closePromise, Mockito.times(1)).addListener(listenerCaptor.capture());
        Assert.assertEquals(listenerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpChannelClosedListener.");
        final SmtpChannelClosedListener closeListener = listenerCaptor.getAllValues().get(0);
        closeListener.operationComplete(closePromise);
        // close future should be done successfully
        Assert.assertTrue(closeFuture.isDone(), "close future should be done");
        final Boolean closeResp = closeFuture.get(FUTURE_GET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(closeResp, "close() response mismatched.");
        Assert.assertTrue(closeResp, "close() response mismatched.");
    }

    /**
     * Tests the correctness of the implementation through the interface.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     */
    @Test
    public void testInterface() throws SmtpAsyncClientException {
        final Channel channel = Mockito.mock(Channel.class);
        final Logger logger = Mockito.mock(Logger.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        final SmtpAsyncSession session = new SmtpAsyncSessionImpl(channel, logger, DebugMode.DEBUG_OFF, SESSION_ID, pipeline, USER_ID);
        Mockito.when(channel.pipeline()).thenReturn(pipeline);
        Mockito.when(channel.isActive()).thenReturn(true);
        final ChannelPromise writePromise = Mockito.mock(ChannelPromise.class);
        Mockito.when(channel.newPromise()).thenReturn(writePromise);

        session.setDebugMode(DebugMode.DEBUG_ON);
        session.execute(new QuitCommand());
        session.close();
    }

    /**
     * Tests {@code handleChannelResponse} when there are no entries to process.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     */
    @Test
    public void testHandleChannelResponseNoEntry() throws SmtpAsyncClientException {
        final Channel channel = Mockito.mock(Channel.class);
        final Logger logger = Mockito.mock(Logger.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        final SmtpAsyncSessionImpl impl = new SmtpAsyncSessionImpl(channel, logger, DebugMode.DEBUG_OFF, SESSION_ID, pipeline, USER_ID);
        impl.handleChannelResponse(new SmtpResponse("500 error"));
    }

    /**
     * Tests server idle event happens while command queue is NOT empty and command in queue is in {@code REQUEST_SENT} state.
     *
     * @throws SmtpAsyncClientException will not throw
     * @throws TimeoutException will not throw
     * @throws InterruptedException will not throw
     */
    @Test
    public void testHandleIdleEventQueueNotEmptyAndCommandSentToServer() throws SmtpAsyncClientException, InterruptedException, TimeoutException {
        final Channel channel = Mockito.mock(Channel.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(channel.pipeline()).thenReturn(pipeline);
        Mockito.when(channel.isActive()).thenReturn(true);
        final ChannelPromise writePromise = Mockito.mock(ChannelPromise.class);
        Mockito.when(channel.newPromise()).thenReturn(writePromise);

        final Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isDebugEnabled()).thenReturn(true);

        // construct, turn on session level debugging by having logger.isDebugEnabled() true and session level debug on

        final SmtpAsyncSessionImpl aSession = new SmtpAsyncSessionImpl(channel, logger, DebugMode.DEBUG_ON, SESSION_ID, pipeline, USER_ID);

        // execute
        final SmtpRequest cmd = new QuitCommand();
        final SmtpFuture<SmtpAsyncResponse> future = aSession.execute(cmd);

        // simulate command sent to server
        final ChannelFuture writeCompleteFuture = Mockito.mock(ChannelFuture.class);
        Mockito.when(writeCompleteFuture.isSuccess()).thenReturn(true);
        aSession.operationComplete(writeCompleteFuture);

        // idle event happened
        final IdleStateEvent idleEvent = null;
        //noinspection ConstantConditions
        aSession.handleIdleEvent(idleEvent);

        // verify that future should be done now since channel timeout exception happens
        Assert.assertTrue(future.isDone(), "isDone() should be true now");

        try {
            future.get(FUTURE_GET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            Assert.fail("Expected exception to be thrown");
        } catch (final ExecutionException ex) {
            final Throwable cause = ex.getCause();
            Assert.assertNotNull(cause, "Expected ExecutionException.getCause() to be present.");
            Assert.assertEquals(cause.getClass(), SmtpAsyncClientException.class, "Expected result mismatched.");
            final SmtpAsyncClientException asyncEx = (SmtpAsyncClientException) cause;
            Assert.assertEquals(asyncEx.getFailureType(), FailureType.CHANNEL_TIMEOUT, "Failure type mismatched.");
        }
    }

    /**
     * Tests server idle event happens while command queue is NOT empty, and command is in {@code NOT_SENT} state.
     *
     * @throws SmtpAsyncClientException will not throw
     */
    @Test
    public void testHandleIdleEventQueueNotEmptyCommandNotSentToServer() throws SmtpAsyncClientException {

        final Channel channel = Mockito.mock(Channel.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(channel.pipeline()).thenReturn(pipeline);
        Mockito.when(channel.isActive()).thenReturn(true);
        final ChannelPromise writePromise = Mockito.mock(ChannelPromise.class);
        Mockito.when(channel.newPromise()).thenReturn(writePromise);

        final Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isDebugEnabled()).thenReturn(true);

        // construct, turn on session level debugging by having logger.isDebugEnabled() true and session level debug on

        final SmtpAsyncSessionImpl aSession = new SmtpAsyncSessionImpl(channel, logger, DebugMode.DEBUG_ON, SESSION_ID, pipeline, USER_ID);

        // execute
        final SmtpRequest cmd = new QuitCommand();
        final SmtpFuture<SmtpAsyncResponse> future = aSession.execute(cmd);

        // idle event is triggered but command in queue is in NOT_SENT state
        final IdleStateEvent idleEvent = null;
        //noinspection ConstantConditions
        aSession.handleIdleEvent(idleEvent);

        // verify that future should NOT be done since channel timeout exception did not happen
        Assert.assertFalse(future.isDone(), "isDone() should be true now");
    }

    /**
     * Tests server idles event happens while command queue is empty.
     */
    @Test
    public void testHandleIdleEventQueueEmpty() {
        final Channel channel = Mockito.mock(Channel.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(channel.pipeline()).thenReturn(pipeline);
        Mockito.when(channel.isActive()).thenReturn(true);
        final ChannelPromise writePromise = Mockito.mock(ChannelPromise.class);
        final ChannelPromise closePromise = Mockito.mock(ChannelPromise.class);
        Mockito.when(channel.newPromise()).thenReturn(writePromise).thenReturn(closePromise);

        final Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isDebugEnabled()).thenReturn(true);

        // construct
        final SmtpAsyncSessionImpl aSession = new SmtpAsyncSessionImpl(channel, logger, DebugMode.DEBUG_ON, SESSION_ID, pipeline, USER_ID);

        // command queue is empty
        aSession.handleIdleEvent(Mockito.mock(IdleStateEvent.class));
    }

    /**
     * Tests constructing the session, executing, flushing to server failed.
     *
     * @throws SmtpAsyncClientException will not throw
     * @throws TimeoutException will not throw
     * @throws ExecutionException will not throw
     * @throws InterruptedException will not throw
     */
    @Test
    public void testExecuteAndFlushToServerFailedCloseSessionFailed() throws SmtpAsyncClientException,
            InterruptedException, ExecutionException, TimeoutException {

        final Channel channel = Mockito.mock(Channel.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(channel.pipeline()).thenReturn(pipeline);
        Mockito.when(channel.isActive()).thenReturn(true);
        final ChannelPromise writeToServerPromise = Mockito.mock(ChannelPromise.class);
        final ChannelPromise closePromise = Mockito.mock(ChannelPromise.class);
        Mockito.when(closePromise.isSuccess()).thenReturn(true);
        Mockito.when(channel.newPromise()).thenReturn(writeToServerPromise).thenReturn(closePromise);

        final Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isDebugEnabled()).thenReturn(true);
        Mockito.when(logger.isTraceEnabled()).thenReturn(true);

        // construct

        final SmtpAsyncSessionImpl aSession = new SmtpAsyncSessionImpl(channel, logger, DebugMode.DEBUG_ON, SESSION_ID, pipeline, USER_ID);

        // execute
        final SmtpRequest cmd = new QuitCommand();
        final SmtpFuture<SmtpAsyncResponse> cmdFuture = aSession.execute(cmd);

        Mockito.verify(writeToServerPromise, Mockito.times(1)).addListener(Mockito.any(SmtpAsyncSessionImpl.class));
        Mockito.verify(channel, Mockito.times(1)).writeAndFlush(Mockito.anyString(), Mockito.isA(ChannelPromise.class));
        Mockito.verify(logger, Mockito.times(1))
                .debug(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

        // simulate write to server completed with isSuccess() false
        Mockito.when(writeToServerPromise.isSuccess()).thenReturn(false);
        aSession.operationComplete(writeToServerPromise);
        // Above operationComplete() will call requestDoneWithException(), which will call close() to close the channel. Simulating it by making
        // channel inactive
        Mockito.when(channel.isActive()).thenReturn(false);

        // verify that future should be done now since exception happens
        Assert.assertTrue(cmdFuture.isDone(), "isDone() should be true now");
        {
            try {
                cmdFuture.get(FUTURE_GET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                Assert.fail("Expected exception to be thrown");
            } catch (final ExecutionException ex) {
                final Throwable cause = ex.getCause();
                Assert.assertNotNull(cause, "Expect cause.");
                Assert.assertEquals(cause.getClass(), SmtpAsyncClientException.class, "Expected result mismatched.");
                final SmtpAsyncClientException asyncEx = (SmtpAsyncClientException) cause;
                Assert.assertEquals(asyncEx.getFailureType(), FailureType.CHANNEL_EXCEPTION, "Failure type mismatched.");
            }
        }

        // since write to server failed, session.close() would be called by requestDoneWithException(), verify that operationComplete listener is
        // registered
        final ArgumentCaptor<SmtpChannelClosedListener> listenerCaptor = ArgumentCaptor.forClass(SmtpChannelClosedListener.class);
        Mockito.verify(closePromise, Mockito.times(1)).addListener(listenerCaptor.capture());
        Assert.assertEquals(listenerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpChannelClosedListener.");
        final SmtpChannelClosedListener closeListener = listenerCaptor.getAllValues().get(0);

        // simulate channel calling SmtpChannelClosedListener.operationComplete()
        closeListener.operationComplete(closePromise);

        // simulate channel triggering handleChannelClosed()
        aSession.handleChannelClosed();
        // verify channel is closed / cleared
        Assert.assertTrue(aSession.isChannelClosed(), "channel should be closed by now.");

        // call operationComplete() again, it is an no-op and method should not throw
        aSession.operationComplete(writeToServerPromise);

        // perform close session again from caller, it should just return since session is closed already
        Mockito.when(closePromise.isSuccess()).thenReturn(false);
        final SmtpFuture<Boolean> closeFuture = aSession.close();

        // close future should be done successfully even for 2nd time
        Assert.assertTrue(closeFuture.isDone(), "close future should be done");
        final Boolean isSuccess = closeFuture.get(FUTURE_GET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(isSuccess, "Result mismatched.");
        Assert.assertTrue(isSuccess, "Result mismatched.");

        // verify logging messages
        final ArgumentCaptor<Object> allArgsCapture = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(logger, Mockito.times(3)).debug(Mockito.anyString(), allArgsCapture.capture(),
                allArgsCapture.capture(), allArgsCapture.capture());

        // since it is vargs, 3 calls with 3 parameters all accumulate to one list
        final List<Object> logArgs = allArgsCapture.getAllValues();
        Assert.assertNotNull(logArgs, "log messages mismatched.");
        Assert.assertEquals(logArgs.size(), 9, "log messages mismatched.");
        Assert.assertEquals(logArgs.get(0), SESSION_ID, "log messages mismatched.");
        Assert.assertEquals(logArgs.get(1), USER_ID, "log messages mismatched.");
        Assert.assertEquals(logArgs.get(2), "QUIT\r\n", "log messages from client mismatched.");
        Assert.assertEquals(logArgs.get(3), SESSION_ID, "log messages mismatched.");
        Assert.assertEquals(logArgs.get(4), USER_ID, "log messages mismatched.");
        Assert.assertEquals(logArgs.get(5), "Closing the session via close().", "log messages from client mismatched.");
        Assert.assertEquals(logArgs.get(6), SESSION_ID, "log messages mismatched.");
        Assert.assertEquals(logArgs.get(7), USER_ID, "log messages mismatched.");
        Assert.assertEquals(logArgs.get(8), "Session is confirmed closed.", "Error message mismatched.");

        final ArgumentCaptor<Object> errCapture = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(logger, Mockito.times(1)).error(Mockito.anyString(), errCapture.capture(),
                errCapture.capture(), errCapture.capture());
        final List<Object> errArgs = errCapture.getAllValues();
        Assert.assertEquals(errArgs.size(), 3, "log message mismatched.");
        Assert.assertEquals(errArgs.get(2).getClass(), SmtpAsyncClientException.class, "class mismatched.");
        final SmtpAsyncClientException e = (SmtpAsyncClientException) errCapture.getAllValues().get(2);
        Assert.assertNotNull(e, "Log error for exception is missing");
        Assert.assertEquals(e.getFailureType(), FailureType.CHANNEL_EXCEPTION, "Class mismatched.");

        // calling setDebugMode() on a closed session, should not throw NPE
        aSession.setDebugMode(DebugMode.DEBUG_ON);
    }

    /**
     * Tests the scenario when the channel becomes inactive during the authentication process.
     *
     * @throws SmtpAsyncClientException will not throw for this test
     * @throws InterruptedException will not throw for this test
     * @throws TimeoutException will not throw for this test
     */
    @Test
    public void testExecuteAuthHandleResponseChannelInactive() throws SmtpAsyncClientException, InterruptedException, TimeoutException {
        final Channel channel = Mockito.mock(Channel.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(channel.pipeline()).thenReturn(pipeline);
        Mockito.when(channel.isActive()).thenReturn(true);
        final ChannelPromise authWritePromise = Mockito.mock(ChannelPromise.class);
        final ChannelPromise authWritePromise2 = Mockito.mock(ChannelPromise.class);
        final ChannelPromise authWritePromise3 = Mockito.mock(ChannelPromise.class);
        final ChannelPromise closePromise = Mockito.mock(ChannelPromise.class);
        Mockito.when(channel.newPromise()).thenReturn(authWritePromise).thenReturn(authWritePromise2).thenReturn(authWritePromise3)
                .thenReturn(closePromise);

        final Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isDebugEnabled()).thenReturn(true);

        // construct, both class level and session level debugging are off
        final SmtpAsyncSessionImpl aSession = new SmtpAsyncSessionImpl(channel, logger, DebugMode.DEBUG_OFF, SESSION_ID, pipeline, USER_ID);

        // Simulate authentication command
        {
            final SmtpRequest cmd = Mockito.mock(SmtpRequest.class);

            Mockito.when(cmd.getCommandLineBytes()).thenReturn(Unpooled.buffer().writeBytes("AUTH PLAIN\r\n".getBytes(StandardCharsets.US_ASCII)));

            final SmtpFuture<SmtpAsyncResponse> future = aSession.execute(cmd);
            Mockito.verify(authWritePromise, Mockito.times(1)).addListener(Mockito.any(SmtpAsyncSessionImpl.class));
            Mockito.verify(channel, Mockito.times(1)).writeAndFlush(Mockito.anyString(), Mockito.isA(ChannelPromise.class));
            Mockito.verify(logger, Mockito.times(0)).debug(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                    Mockito.anyString());

            // simulate write to server completed successfully
            Mockito.when(authWritePromise.isSuccess()).thenReturn(true);
            aSession.operationComplete(authWritePromise);

            // handle server response
            final SmtpResponse serverResp1 = new SmtpResponse("334 enter challenge response\r\n");

            // simulate that channel is closed
            Mockito.when(channel.isActive()).thenReturn(false);
            aSession.setDebugMode(DebugMode.DEBUG_ON);

            Mockito.when(cmd.getNextCommandLineAfterContinuation(serverResp1))
                    .thenReturn(Unpooled.buffer().writeBytes("my password".getBytes(StandardCharsets.US_ASCII)));
            // following will call getNextCommandLineAfterContinuation
            aSession.handleChannelResponse(serverResp1);

            // verify that future should be done now
            Assert.assertTrue(future.isDone(), "isDone() should be true now");
            try {
                future.get(FUTURE_GET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                Assert.fail("Expected exception to be thrown");
            } catch (final ExecutionException ex) {
                final Throwable cause = ex.getCause();
                Assert.assertNotNull(cause, "Expect ExecutionException.getCause() to be present.");
                Assert.assertEquals(cause.getClass(), SmtpAsyncClientException.class, "Expected result mismatched.");
                final SmtpAsyncClientException asyncEx = (SmtpAsyncClientException) cause;
                Assert.assertEquals(asyncEx.getFailureType(), FailureType.CHANNEL_EXCEPTION, "Failure type mismatched.");
            }

            // verify logging messages
            final ArgumentCaptor<Object> allArgsCapture = ArgumentCaptor.forClass(Object.class);
            Mockito.verify(logger, Mockito.times(2)).debug(Mockito.anyString(), allArgsCapture.capture(),
                    allArgsCapture.capture(),
                    allArgsCapture.capture());

            // since it is vargs, 2 calls with 3 parameters all accumulate to one list, 2 * 3 =6
            final List<Object> logArgs = allArgsCapture.getAllValues();
            Assert.assertNotNull(logArgs, "log messages mismatched.");
            Assert.assertEquals(logArgs.size(), 6, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(0), SESSION_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(1), USER_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(2), "334 enter challenge response\r\n", "log messages from server mismatched.");
            Assert.assertEquals(logArgs.get(3), SESSION_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(4), USER_ID, "log messages mismatched.");
            Assert.assertEquals(logArgs.get(5), "my password", "log messages from client mismatched.");

            final ArgumentCaptor<Object> errCapture = ArgumentCaptor.forClass(Object.class);
            Mockito.verify(logger, Mockito.times(1)).error(Mockito.anyString(), errCapture.capture(), errCapture.capture(),
                    errCapture.capture());
            final List<Object> errArgs = errCapture.getAllValues();
            Assert.assertEquals(errArgs.size(), 3, "log message mismatched.");
            Assert.assertEquals(errArgs.get(2).getClass(), SmtpAsyncClientException.class, "class mismatched.");
            final SmtpAsyncClientException e = (SmtpAsyncClientException) errCapture.getAllValues().get(2);
            Assert.assertNotNull(e, "Log error for exception is missing");
            Assert.assertEquals(e.getFailureType(), FailureType.CHANNEL_EXCEPTION, "Class mismatched.");

            // no more responses in the queue, calling handleResponse should return right away without any indexOutOfBound
            aSession.handleChannelResponse(serverResp1);
        }
    }

    /**
     * Tests constructing the session, execute, and channel is closed abruptly before server response is back.
     *
     * @throws SmtpAsyncClientException will not throw
     * @throws TimeoutException will not throw
     * @throws InterruptedException will not throw
     * @throws IllegalArgumentException will not throw
     */
    @Test
    public void testExecuteChannelCloseBeforeServerResponseArrived() throws SmtpAsyncClientException, InterruptedException, TimeoutException,
            IllegalArgumentException {

        final Channel channel = Mockito.mock(Channel.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(channel.pipeline()).thenReturn(pipeline);
        Mockito.when(channel.isActive()).thenReturn(true);
        final ChannelPromise writePromise = Mockito.mock(ChannelPromise.class);
        Mockito.when(channel.newPromise()).thenReturn(writePromise);

        final Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isDebugEnabled()).thenReturn(false);
        Mockito.when(logger.isTraceEnabled()).thenReturn(true);

        // construct, class level logging is off, session level logging is on

        final SmtpAsyncSessionImpl aSession = new SmtpAsyncSessionImpl(channel, logger, DebugMode.DEBUG_ON, SESSION_ID, pipeline, USER_ID);

        // execute
        final SmtpRequest cmd = new QuitCommand();
        final SmtpFuture<SmtpAsyncResponse> future = aSession.execute(cmd);

        Mockito.verify(writePromise, Mockito.times(1)).addListener(Mockito.any(SmtpAsyncSessionImpl.class));
        Mockito.verify(channel, Mockito.times(1)).writeAndFlush(Mockito.anyString(), Mockito.isA(ChannelPromise.class));
        Mockito.verify(logger, Mockito.times(1))
                .debug(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

        // simulate channel closed
        aSession.handleChannelClosed();

        // verify that future should be done now since exception happens
        Assert.assertTrue(future.isDone(), "isDone() should be true now");
        try {
            future.get(FUTURE_GET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            Assert.fail("Expected exception to be thrown");
        } catch (final ExecutionException ex) {
            final Throwable cause = ex.getCause();
            Assert.assertNotNull(cause, "Expect cause.");
            Assert.assertEquals(cause.getClass(), SmtpAsyncClientException.class, "Expected result mismatched.");
            final SmtpAsyncClientException asyncEx = (SmtpAsyncClientException) cause;
            Assert.assertEquals(asyncEx.getFailureType(), FailureType.CHANNEL_DISCONNECTED, "Failure type mismatched.");
        }

        // verify logging messages
        final ArgumentCaptor<Object> allArgsCapture = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(logger, Mockito.times(3)).debug(Mockito.anyString(), allArgsCapture.capture(),
                allArgsCapture.capture(), allArgsCapture.capture());

        // since it is vargs, 3 calls with 3 parameters all accumulate to one list
        final List<Object> logArgs = allArgsCapture.getAllValues();
        Assert.assertNotNull(logArgs, "log messages mismatched.");
        Assert.assertEquals(logArgs.size(), 9, "log messages mismatched.");
        Assert.assertEquals(logArgs.get(0), SESSION_ID, "log messages mismatched.");
        Assert.assertEquals(logArgs.get(1), USER_ID, "log messages mismatched.");
        Assert.assertEquals(logArgs.get(2), "QUIT\r\n", "log messages from client mismatched.");
        Assert.assertEquals(logArgs.get(3), SESSION_ID, "log messages mismatched.");
        Assert.assertEquals(logArgs.get(4), USER_ID, "log messages mismatched.");
        Assert.assertEquals(logArgs.get(5), "Session is confirmed closed.", "log messages from client mismatched.");
        Assert.assertEquals(logArgs.get(6), SESSION_ID, "log messages mismatched.");
        Assert.assertEquals(logArgs.get(7), USER_ID, "log messages mismatched.");
        Assert.assertEquals(logArgs.get(8), "Closing the session via close().", "Error message mismatched.");

        final ArgumentCaptor<Object> errCapture = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(logger, Mockito.times(1))
                .error(Mockito.anyString(), errCapture.capture(), errCapture.capture(), errCapture.capture());
        final List<Object> errArgs = errCapture.getAllValues();
        Assert.assertEquals(errArgs.size(), 3, "log message mismatched.");
        Assert.assertEquals(errArgs.get(2).getClass(), SmtpAsyncClientException.class, "class mismatched.");
        final SmtpAsyncClientException e = (SmtpAsyncClientException) errCapture.getAllValues().get(2);
        Assert.assertNotNull(e, "Log error for exception is missing");
        Assert.assertEquals(e.getFailureType(), FailureType.CHANNEL_DISCONNECTED, "Class mismatched.");
    }

    /**
     * Tests constructing the session, execute, and channel is closed abruptly before server response is back. In this test, the log level is in info,
     * not in debug, we want to make sure it does not call {@code debug}.
     *
     * @throws SmtpAsyncClientException will not throw
     * @throws TimeoutException will not throw
     * @throws InterruptedException will not throw
     * @throws IllegalArgumentException will not throw
     */
    @Test
    public void testExecuteChannelCloseBeforeServerResponseArrivedLogLevelInfo() throws SmtpAsyncClientException, InterruptedException,
            TimeoutException, IllegalArgumentException {

        final Channel channel = Mockito.mock(Channel.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(channel.pipeline()).thenReturn(pipeline);
        Mockito.when(channel.isActive()).thenReturn(true);
        final ChannelPromise writePromise = Mockito.mock(ChannelPromise.class);
        Mockito.when(channel.newPromise()).thenReturn(writePromise);

        final Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isInfoEnabled()).thenReturn(true);
        Mockito.when(logger.isDebugEnabled()).thenReturn(false);

        // construct, class level logging is off, session level logging is on

        final SmtpAsyncSessionImpl aSession = new SmtpAsyncSessionImpl(channel, logger, DebugMode.DEBUG_ON, SESSION_ID, pipeline, USER_ID);

        // execute
        final SmtpRequest cmd = new QuitCommand();
        final SmtpFuture<SmtpAsyncResponse> future = aSession.execute(cmd);

        Mockito.verify(writePromise, Mockito.times(1)).addListener(Mockito.any(SmtpAsyncSessionImpl.class));
        Mockito.verify(channel, Mockito.times(1)).writeAndFlush(Mockito.anyString(), Mockito.isA(ChannelPromise.class));
        // Ensure there is no call to debug() method
        Mockito.verify(logger, Mockito.times(0))
                .debug(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

        // simulate channel closed
        aSession.handleChannelClosed();

        // verify that future should be done now since exception happens
        Assert.assertTrue(future.isDone(), "isDone() should be true now");
        try {
            future.get(FUTURE_GET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            Assert.fail("Expected exception to be thrown");
        } catch (final ExecutionException ex) {
            final Throwable cause = ex.getCause();
            Assert.assertNotNull(cause, "Expect cause.");
            Assert.assertEquals(cause.getClass(), SmtpAsyncClientException.class, "Expected result mismatched.");
            final SmtpAsyncClientException asyncEx = (SmtpAsyncClientException) cause;
            Assert.assertEquals(asyncEx.getFailureType(), FailureType.CHANNEL_DISCONNECTED, "Failure type mismatched.");
        }

        // verify logging messages
        final ArgumentCaptor<Object> logCapture = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(logger, Mockito.times(0))
                .debug(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), logCapture.capture());
        final List<Object> logMsgs = logCapture.getAllValues();
        Assert.assertNotNull(logMsgs, "log messages mismatched.");
        Assert.assertEquals(logMsgs.size(), 0, "log messages mismatched.");

        final ArgumentCaptor<Object> errCapture = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(logger, Mockito.times(1))
                .error(Mockito.anyString(), errCapture.capture(), errCapture.capture(), errCapture.capture());
        final List<Object> errArgs = errCapture.getAllValues();
        Assert.assertEquals(errArgs.size(), 3, "log message mismatched.");
        Assert.assertEquals(errArgs.get(2).getClass(), SmtpAsyncClientException.class, "class mismatched.");
        final SmtpAsyncClientException e = (SmtpAsyncClientException) errCapture.getAllValues().get(2);
        Assert.assertNotNull(e, "Log error for exception is missing");
        Assert.assertEquals(e.getFailureType(), FailureType.CHANNEL_DISCONNECTED, "Class mismatched.");
    }

    /**
     * Tests execute method when command queue is not empty.
     *
     * @throws SmtpAsyncClientException will not throw
     */
    @Test
    public void testExecuteFailedDueToQueueNotEmpty() throws SmtpAsyncClientException {

        final Channel channel = Mockito.mock(Channel.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(channel.pipeline()).thenReturn(pipeline);
        Mockito.when(channel.isActive()).thenReturn(true);
        final ChannelPromise writePromise = Mockito.mock(ChannelPromise.class);
        Mockito.when(channel.newPromise()).thenReturn(writePromise);

        final Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isDebugEnabled()).thenReturn(true);

        final SmtpAsyncSessionImpl aSession = new SmtpAsyncSessionImpl(channel, logger, DebugMode.DEBUG_ON, SESSION_ID, pipeline, USER_ID);
        final SmtpRequest cmd = new QuitCommand();
        aSession.execute(cmd);

        Mockito.verify(writePromise, Mockito.times(1)).addListener(Mockito.any(SmtpAsyncSessionImpl.class));
        Mockito.verify(channel, Mockito.times(1)).writeAndFlush(Mockito.anyString(), Mockito.isA(ChannelPromise.class));

        try {
            // execute again, queue is not empty
            aSession.execute(cmd);
            Assert.fail("Expected exception to be thrown");
        } catch (final SmtpAsyncClientException asyncEx) {
            Assert.assertNotNull(asyncEx, "Exception should occur.");
            Assert.assertEquals(asyncEx.getFailureType(), FailureType.COMMAND_NOT_ALLOWED, "Failure type mismatched.");
        }
        Mockito.verify(logger, Mockito.times(1)).debug(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString());
    }

    /**
     * Tests {@code execute} method when channel is inactive, then call {@code close} to close session.
     *
     * @throws TimeoutException will not throw
     * @throws ExecutionException will not throw
     * @throws InterruptedException will not throw
     */
    @Test
    public void testExecuteFailedChannelInactiveAndCloseChannel()
            throws InterruptedException, ExecutionException, TimeoutException {

        final Channel channel = Mockito.mock(Channel.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(channel.pipeline()).thenReturn(pipeline);
        Mockito.when(channel.isActive()).thenReturn(false);
        final ChannelPromise writePromise = Mockito.mock(ChannelPromise.class);
        final ChannelPromise closePromise = Mockito.mock(ChannelPromise.class);
        Mockito.when(channel.newPromise()).thenReturn(writePromise).thenReturn(closePromise);

        final Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isDebugEnabled()).thenReturn(true);

        // constructor, class level debug is off, but session level is on

        final SmtpAsyncSessionImpl aSession = new SmtpAsyncSessionImpl(channel, logger, DebugMode.DEBUG_ON, SESSION_ID, pipeline, USER_ID);
        final SmtpRequest cmd = new QuitCommand();
        try {
            // execute again, queue is not empty
            aSession.execute(cmd);
            Assert.fail("Expected exception to be thrown");
        } catch (final SmtpAsyncClientException asyncEx) {
            Assert.assertEquals(asyncEx.getFailureType(), FailureType.OPERATION_PROHIBITED_ON_CLOSED_CHANNEL, "Failure type mismatched.");
        }

        Mockito.verify(writePromise, Mockito.times(0)).addListener(Mockito.any(SmtpAsyncSessionImpl.class));
        Mockito.verify(channel, Mockito.times(0)).writeAndFlush(Mockito.anyString(), Mockito.isA(ChannelPromise.class));
        // encountering the above exception in execute(), will not log the command sent over the wire
        Mockito.verify(logger, Mockito.times(0)).debug(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString());

        Mockito.when(closePromise.isSuccess()).thenReturn(true);
        final SmtpFuture<Boolean> closeFuture = aSession.close();
        // close future should be done successfully
        Assert.assertTrue(closeFuture.isDone(), "close future should be done");
        final Boolean closeResp = closeFuture.get(FUTURE_GET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(closeResp, "close() response mismatched.");
        Assert.assertTrue(closeResp, "close() response mismatched.");
    }

    /**
     * Tests {@code close} method and its close listener. Specifically testing {@code operationComplete} with a future that returns
     * {@code false} for {@code isSuccess}.
     *
     * @throws TimeoutException will not throw
     * @throws InterruptedException will not throw
     */
    @Test
    public void testCloseSessionOperationCompleteFutureIsUnsuccessful() throws InterruptedException, TimeoutException {
        final Channel channel = Mockito.mock(Channel.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(channel.pipeline()).thenReturn(pipeline);
        Mockito.when(channel.isActive()).thenReturn(true);
        final ChannelPromise closePromise = Mockito.mock(ChannelPromise.class);
        Mockito.when(channel.newPromise()).thenReturn(closePromise);

        final Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isDebugEnabled()).thenReturn(false);
        Mockito.when(logger.isTraceEnabled()).thenReturn(false);

        // construct, both class level and session level debugging are off

        final SmtpAsyncSessionImpl aSession = new SmtpAsyncSessionImpl(channel, logger, DebugMode.DEBUG_OFF, SESSION_ID, pipeline, USER_ID);

        // perform close session, isSuccess() returns false
        Mockito.when(closePromise.isSuccess()).thenReturn(false);
        final SmtpFuture<Boolean> closeFuture = aSession.close();
        final ArgumentCaptor<SmtpChannelClosedListener> listenerCaptor = ArgumentCaptor.forClass(SmtpChannelClosedListener.class);
        Mockito.verify(closePromise, Mockito.times(1)).addListener(listenerCaptor.capture());
        Assert.assertEquals(listenerCaptor.getAllValues().size(), 1, "Unexpected count of SmtpChannelClosedListener.");
        final SmtpChannelClosedListener closeListener = listenerCaptor.getAllValues().get(0);
        closeListener.operationComplete(closePromise);
        // close future should be done successfully
        Assert.assertTrue(closeFuture.isDone(), "close future should be done");

        try {
            // execute again, queue is not empty
            closeFuture.get(FUTURE_GET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            Assert.fail("Expected exception to be thrown");
        } catch (final ExecutionException e) {
            final Throwable actual = e.getCause();
            Assert.assertEquals(actual.getClass(), SmtpAsyncClientException.class, "Class type mismatched.");
            final SmtpAsyncClientException asyncClientEx = (SmtpAsyncClientException) actual;
            Assert.assertEquals(asyncClientEx.getFailureType(), FailureType.CLOSING_CONNECTION_FAILED, "Failure type mismatched.");
        }
    }

    /**
     * Tests {@link DebugMode} enum.
     */
    @Test
    public void testDebugModeEnum() {
        final DebugMode[] enumList = DebugMode.values();
        Assert.assertEquals(enumList.length, 2, "The enum count mismatched.");
        final DebugMode value = DebugMode.valueOf("DEBUG_OFF");
        Assert.assertSame(value, DebugMode.DEBUG_OFF, "Enum does not match.");
    }

    /**
     * Test the behavior of the debugger when encountering sensitive data. For example, user's login information.
     *
     * @throws SmtpAsyncClientException will not throw in this test
     */
    @Test
    public void testDebugSensitiveData() throws SmtpAsyncClientException {
        final Channel channel = Mockito.mock(Channel.class);
        final ChannelPipeline pipeline = Mockito.mock(ChannelPipeline.class);
        Mockito.when(channel.pipeline()).thenReturn(pipeline);
        Mockito.when(channel.isActive()).thenReturn(true);
        final ChannelPromise writePromise = Mockito.mock(ChannelPromise.class);
        Mockito.when(channel.newPromise()).thenReturn(writePromise);

        final Logger logger = Mockito.mock(Logger.class);
        Mockito.when(logger.isTraceEnabled()).thenReturn(true);

        final SmtpAsyncSessionImpl aSession = new SmtpAsyncSessionImpl(channel, logger, DebugMode.DEBUG_ON, SESSION_ID, pipeline, null);

        // execute
        final SmtpRequest cmd = Mockito.spy(QuitCommand.class);
        Mockito.when(cmd.isCommandLineDataSensitive()).thenReturn(true); // Simulate sensitive data
        final SmtpFuture<SmtpAsyncResponse> future = aSession.execute(cmd);

        Mockito.verify(writePromise, Mockito.times(1)).addListener(Mockito.any(SmtpAsyncSessionImpl.class));
        Mockito.verify(channel, Mockito.times(1)).writeAndFlush(Mockito.anyString(), Mockito.isA(ChannelPromise.class));
        Mockito.verify(logger, Mockito.times(1))
                .debug(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

        // simulate channel closed
        aSession.handleChannelClosed();

        // verify that future should be done now since exception happens
        Assert.assertTrue(future.isDone(), "isDone() should be true now");
        try {
            future.get(FUTURE_GET_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            Assert.fail("Expected exception to be thrown");
        } catch (final ExecutionException | InterruptedException | TimeoutException ex) {
            final Throwable cause = ex.getCause();
            Assert.assertNotNull(cause, "Expect cause.");
            Assert.assertEquals(cause.getClass(), SmtpAsyncClientException.class, "Expected result mismatched.");
            final SmtpAsyncClientException asyncEx = (SmtpAsyncClientException) cause;
            Assert.assertEquals(asyncEx.getFailureType(), FailureType.CHANNEL_DISCONNECTED, "Failure type mismatched.");
        }

        // verify logging messages
        final ArgumentCaptor<Object> allArgsCapture = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(logger, Mockito.times(3)).debug(Mockito.anyString(), allArgsCapture.capture(),
                allArgsCapture.capture(), allArgsCapture.capture());

        // since it is vargs, 3 calls with 3 parameters all accumulate to one list
        final List<Object> logArgs = allArgsCapture.getAllValues();
        Assert.assertNotNull(logArgs, "log messages mismatched.");
        Assert.assertEquals(logArgs.size(), 9, "log messages mismatched.");
        Assert.assertEquals(logArgs.get(0), SESSION_ID, "log messages mismatched.");
        Assert.assertNull(logArgs.get(1), "log messages mismatched.");
        Assert.assertEquals(logArgs.get(2), "", "log message for sensitive data does not match the expected result");
        Assert.assertEquals(logArgs.get(3), SESSION_ID, "log messages mismatched.");
        Assert.assertNull(logArgs.get(4), "log messages mismatched.");
        Assert.assertEquals(logArgs.get(5), "Session is confirmed closed.", "log messages from client mismatched.");
        Assert.assertEquals(logArgs.get(6), SESSION_ID, "log messages mismatched.");
        Assert.assertNull(logArgs.get(7), "log messages mismatched.");
        Assert.assertEquals(logArgs.get(8), "Closing the session via close().", "Error message mismatched.");

        final ArgumentCaptor<Object> errCapture = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(logger, Mockito.times(1))
                .error(Mockito.anyString(), errCapture.capture(), errCapture.capture(), errCapture.capture());
        final List<Object> errArgs = errCapture.getAllValues();
        Assert.assertEquals(errArgs.size(), 3, "log message mismatched.");
        Assert.assertEquals(errArgs.get(2).getClass(), SmtpAsyncClientException.class, "class mismatched.");
        final SmtpAsyncClientException e = (SmtpAsyncClientException) errCapture.getAllValues().get(2);
        Assert.assertNotNull(e, "Log error for exception is missing");
        Assert.assertEquals(e.getFailureType(), FailureType.CHANNEL_DISCONNECTED, "Class mismatched.");
    }
}
