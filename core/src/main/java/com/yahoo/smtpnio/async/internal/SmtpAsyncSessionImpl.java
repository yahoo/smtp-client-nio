/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.internal;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.NotThreadSafe;

import org.slf4j.Logger;

import com.yahoo.smtpnio.async.client.SmtpAsyncSession;
import com.yahoo.smtpnio.async.client.SmtpFuture;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException.FailureType;
import com.yahoo.smtpnio.async.netty.SmtpClientCommandRespHandler;
import com.yahoo.smtpnio.async.netty.SmtpCommandChannelEventProcessor;
import com.yahoo.smtpnio.async.request.SmtpRequest;
import com.yahoo.smtpnio.async.response.SmtpAsyncResponse;
import com.yahoo.smtpnio.async.response.SmtpResponse;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * This class defines the implementation that establishes a session with a SMTP server, as well handling commands and events.
 */
@NotThreadSafe
public class SmtpAsyncSessionImpl implements SmtpAsyncSession, SmtpCommandChannelEventProcessor, ChannelFutureListener {

    /**
     * This class handles and manages responses from the server and determines whether the job for this request is done.
     * When the request is done, it sets the future to done and returns the appropriate status to the caller via the {@code handleResponse} method.
     */
    private static class SmtpCommandEntry {

        /**
         * State of the command in its life cycle.
         */
        public enum CommandState {

            /** Request (command line) is in preparation to be generated and sent, but not yet sent to server. */
            REQUEST_IN_PREPARATION,

            /** Request (command line) is confirmed sent to the server. */
            REQUEST_SENT,

            /** Server done with responses for the given client request. Server is not obligated to send more responses per given request. */
            RESPONSES_DONE
        }

        /** the SMTP command. */
        @Nonnull
        private final SmtpRequest cmd;

        /** List of response lines. */
        @Nonnull
        private final ConcurrentLinkedQueue<SmtpResponse> responses;

        /** The future for the response. */
        @Nonnull
        private final SmtpFuture<SmtpAsyncResponse> future;

        /** Current state of the command. */
        @Nonnull
        private CommandState state;

        /**
         * Initializes a {@link SmtpCommandEntry} object so that it can handle the command responses and determine whether the request is done.
         *
         * @param cmd {@link SmtpRequest} instance
         * @param future {@link SmtpFuture} instance
         */
        SmtpCommandEntry(@Nonnull final SmtpRequest cmd, @Nonnull final SmtpFuture<SmtpAsyncResponse> future) {
            this.cmd = cmd;
            this.state = CommandState.REQUEST_IN_PREPARATION;
            this.responses = new ConcurrentLinkedQueue<>();
            this.future = future;
        }

        /**
         * @return the state of the command
         */
        @Nonnull
        CommandState getState() {
            return state;
        }

        /**
         * @param state the target state to set
         */
        void setState(@Nonnull final CommandState state) {
            this.state = state;
        }

        /**
         * @return the responses list
         */
        Collection<SmtpResponse> getResponses() {
            return responses;
        }

        /**
         * @return the future for the SMTP command
         */
        @Nonnull
        SmtpFuture<SmtpAsyncResponse> getFuture() {
            return future;
        }

        /**
         * @return the SMTP command
         */
        SmtpRequest getRequest() {
            return cmd;
        }
    }

    /**
     * Listener for the channel close event.
     */
    class SmtpChannelClosedListener implements ChannelFutureListener {

        /** Future for the {@link SmtpAsyncSession} client. */
        private final SmtpFuture<Boolean> smtpSessionCloseFuture;

        /**
         * Initializes a {@link SmtpChannelClosedListener} with a {@link SmtpFuture} instance.
         *
         * @param smtpFuture the future for caller of {@link SmtpAsyncSession}'s {@code close} method
         */
        SmtpChannelClosedListener(@Nonnull final SmtpFuture<Boolean> smtpFuture) {
            smtpSessionCloseFuture = smtpFuture;
        }

        @Override
        public void operationComplete(@Nonnull final ChannelFuture future) {
            if (future.isSuccess()) {
                smtpSessionCloseFuture.done(Boolean.TRUE);
            } else {
                smtpSessionCloseFuture.done(new SmtpAsyncClientException(FailureType.CLOSING_CONNECTION_FAILED,
                        future.cause(), sessionId, sessionCtx));
            }
        }
    }

    /** Debug log record for the session, first {} is {@code sessionId}, 2nd user information. */
    private static final String SESSION_LOG_REC = "[{},{}] {}";

    /** Error record for the session, first {} is {@code sessionId}, 2nd user information. */
    private static final String SESSION_LOG_WITH_EXCEPTION = "[{},{}]";

    /** Debug log record for server, first {} is {@code sessionId}, 2nd user information, 3rd for server message. */
    private static final String SERVER_LOG_REC = "[{},{}] S:{}";

    /** Debug log record for client, first {} is {@code sessionId}, 2nd user information, 3rd for client message. */
    private static final String CLIENT_LOG_REC = "[{},{}] C:{}";

    /** The Netty channel object. */
    private final AtomicReference<Channel> channelRef = new AtomicReference<>();

    /** Session Id. */
    private final long sessionId;

    /** Instance that stores the client context. */
    @Nullable
    private final Object sessionCtx;

    /** Producer queue. */
    private final ConcurrentLinkedQueue<SmtpCommandEntry> requestsQueue;

    /** {@link Logger} instance. */
    private final Logger logger;

    /** Debug mode. */
    private final AtomicReference<DebugMode> debugModeRef = new AtomicReference<>();

    /**
     * Initializes a SMTP session that supports async operations.
     *
     * @param channel {@link Channel} object established for this session
     * @param logger {@link Logger} instance
     * @param debugMode Flag for debugging
     * @param sessionId the session id
     * @param pipeline the {@link ChannelPipeline} instance
     * @param sessionCtx context for client to store information
     */
    public SmtpAsyncSessionImpl(@Nonnull final Channel channel, @Nonnull final Logger logger, @Nonnull final DebugMode debugMode,
                                final long sessionId, final ChannelPipeline pipeline, @Nullable final Object sessionCtx) {
        this.channelRef.set(channel);
        this.logger = logger;
        this.debugModeRef.set(debugMode);
        this.sessionId = sessionId;
        this.requestsQueue = new ConcurrentLinkedQueue<>();
        this.sessionCtx = sessionCtx;
        pipeline.addLast(SmtpClientCommandRespHandler.HANDLER_NAME, new SmtpClientCommandRespHandler(this));
    }

    /**
     * @return returns the user information
     */
    @Nullable
    private String getUserInfo() {
        return sessionCtx != null ? sessionCtx.toString() : null;
    }

    /**
     * @return true if debugging is enabled either for the session or for all sessions
     */
    private boolean isDebugEnabled() {
        // when trace is enabled, log for all sessions
        // when debug is enabled && session debug is on, we print specific session
        return logger.isTraceEnabled() || (logger.isDebugEnabled() && debugModeRef.get() == DebugMode.DEBUG_ON);
    }

    @Override
    public void setDebugMode(@Nonnull final DebugMode newOption) {
        debugModeRef.set(newOption);
    }

    @Override
    public SmtpFuture<SmtpAsyncResponse> execute(@Nonnull final SmtpRequest command) throws SmtpAsyncClientException {
        if (isChannelClosed()) { // fail fast instead of entering to sendRequest() to fail
            throw new SmtpAsyncClientException(FailureType.OPERATION_PROHIBITED_ON_CLOSED_CHANNEL, sessionId, sessionCtx);
        }
        if (!requestsQueue.isEmpty()) { // when prior command is in process, do not allow the new one
            throw new SmtpAsyncClientException(FailureType.COMMAND_NOT_ALLOWED, sessionId, sessionCtx);
        }

        final SmtpFuture<SmtpAsyncResponse> cmdFuture = new SmtpFuture<>();
        requestsQueue.add(new SmtpCommandEntry(command, cmdFuture));

        final ByteBuf buf = command.getCommandLineBytes();
        sendRequest(buf, command);

        return cmdFuture;
    }

    /**
     * @return true if and only if the channel is closed
     */
    boolean isChannelClosed() {
        return !channelRef.get().isActive();
    }

    /**
     * Sends the given request to the server.
     *
     * @param request the message of the request
     * @param command the SMTP request to be issued
     * @throws SmtpAsyncClientException when channel is closed
     */
    private void sendRequest(@Nonnull final ByteBuf request, @Nonnull final SmtpRequest command) throws SmtpAsyncClientException {
        if (isDebugEnabled()) {
            // log given request if it not sensitive, otherwise log the debug data decided by command
            logger.debug(CLIENT_LOG_REC, sessionId, getUserInfo(),
                    (!command.isCommandLineDataSensitive()) ? request.toString(StandardCharsets.UTF_8) : command.getDebugData());
        }

        // ChannelPromise is the suggested ChannelFuture that allows caller to setup listener before the action is made
        // this is useful for light-speed operation.
        final Channel channel = channelRef.get();
        final ChannelPromise writeFuture = channel.newPromise();
        writeFuture.addListener(this); // "this" listens to write future done in operationComplete() to handle exception in writing.
        channel.writeAndFlush(request, writeFuture);
    }

    /**
     * Sends the given request to the server.
     *
     * @param command the SMTP request to be issued
     * @throws SmtpAsyncClientException when channel is closed
     */
    private void sendContinuation(@Nonnull final SmtpRequest command, final SmtpResponse serverResponse) throws SmtpAsyncClientException {
        if (isDebugEnabled()) {
            // log given request if it not sensitive, otherwise log the debug data decided by command
            logger.debug(CLIENT_LOG_REC, sessionId, getUserInfo(),
                (!command.isCommandLineDataSensitive())
                    ? command.getNextCommandLineAfterContinuation(serverResponse).toString(StandardCharsets.UTF_8)
                    : command.getDebugData());
        }
        if (isChannelClosed()) {
            throw new SmtpAsyncClientException(FailureType.OPERATION_PROHIBITED_ON_CLOSED_CHANNEL, sessionId, sessionCtx);
        }

        // ChannelPromise is the suggested ChannelFuture that allows caller to setup listener before the action is made
        // this is useful for light-speed operation.
        final Channel channel = channelRef.get();
        command.encodeCommandAfterContinuation(channel, writeFuture(channel), serverResponse);
    }

    private Supplier<ChannelPromise> writeFuture(final Channel channel) {
        SmtpAsyncSessionImpl session = this;
        return new Supplier<ChannelPromise>() {
            @Override
            public ChannelPromise get() {
                final ChannelPromise writeFuture = channel.newPromise();
                // "this" listens to write future done in operationComplete() to handle exception in writing.
                writeFuture.addListener(session);
                return writeFuture;
            }
        };
    }

    /**
     * Listens to write to server complete future.
     *
     * @param future {@link ChannelFuture} instance to check whether the future has completed successfully
     */
    @Override
    public void operationComplete(final ChannelFuture future) {
        final SmtpCommandEntry entry = requestsQueue.peek();
        if (entry != null) {
            // set the state to REQUEST_SENT regardless success or not
            entry.setState(SmtpCommandEntry.CommandState.REQUEST_SENT);
        }

        if (!future.isSuccess()) { // failed to write to server
            handleChannelException(new SmtpAsyncClientException(FailureType.WRITE_TO_SERVER_FAILED, future.cause(), sessionId, sessionCtx));
        }
    }

    @Override
    public void handleChannelClosed() {
        if (isDebugEnabled()) {
            logger.debug(SESSION_LOG_REC, sessionId, getUserInfo(), "Session is confirmed closed.");
        }
        // set the future done if there is any
        requestDoneWithException(new SmtpAsyncClientException(FailureType.CHANNEL_DISCONNECTED, sessionId, sessionCtx));
    }

    /**
     * Removes the first entry in the queue if it exists.
     *
     * @return the removed entry, or {@code null} if queue is empty
     */
    @Nullable
    private SmtpCommandEntry removeFirstEntry() {
        return requestsQueue.poll();
    }

    /**
     * @return the current in-progress request.
     */
    private SmtpCommandEntry getFirstEntry() {
        return requestsQueue.peek();
    }

    /**
     * Sets the future to done when the command is executed unsuccessfully.
     *
     * @param cause the cause of why the operation failed
     */
    private void requestDoneWithException(@Nonnull final SmtpAsyncClientException cause) {
        final SmtpCommandEntry entry = removeFirstEntry();
        if (entry == null) {
            return;
        }

        // log at error level
        logger.error(SESSION_LOG_WITH_EXCEPTION, sessionId, getUserInfo(), cause);
        entry.getFuture().done(cause);

        // close session when encountering channel exception since the health of session is frail/unknown.
        close();
    }

    @Override
    public void handleChannelException(@Nonnull final Throwable cause) {
        requestDoneWithException(new SmtpAsyncClientException(FailureType.CHANNEL_EXCEPTION, cause, sessionId, sessionCtx));
    }

    @Override
    public void handleIdleEvent(@Nonnull final IdleStateEvent idleEvent) {
        final SmtpCommandEntry curEntry = getFirstEntry();
        // only throws channel timeout when a request is sent and we are waiting for the responses to come
        if (curEntry == null || curEntry.getState() != SmtpCommandEntry.CommandState.REQUEST_SENT) {
            return;
        }
        // error out for any other commands sent but server is not responding
        requestDoneWithException(new SmtpAsyncClientException(FailureType.CHANNEL_TIMEOUT, sessionId, sessionCtx));
    }

    @Override
    public void handleChannelResponse(@Nonnull final SmtpResponse serverResponse) {
        final SmtpCommandEntry curEntry = getFirstEntry();
        if (curEntry == null) {
            return;
        }

        final SmtpRequest currentCmd = curEntry.getRequest();
        final Collection<SmtpResponse> responses = curEntry.getResponses();
        responses.add(serverResponse);

        if (isDebugEnabled()) { // logging all server responses when enabled
            logger.debug(SERVER_LOG_REC, sessionId, getUserInfo(), serverResponse.toString());
        }

        // server sends continuation message for next request. Eg. DATA command
        if (serverResponse.isContinuation()) {
            try {
                curEntry.setState(SmtpCommandEntry.CommandState.RESPONSES_DONE);
                curEntry.setState(SmtpCommandEntry.CommandState.REQUEST_IN_PREPARATION); // preparing to send request so setting to correct state
                sendContinuation(currentCmd, serverResponse);
            } catch (final SmtpAsyncClientException | RuntimeException e) { // when encountering an error on building request from client
                requestDoneWithException(new SmtpAsyncClientException(FailureType.CHANNEL_EXCEPTION, e, sessionId, sessionCtx));
            }
        } else if (serverResponse.isLastLineResponse()) {
            curEntry.setState(SmtpCommandEntry.CommandState.RESPONSES_DONE);

            // Completion is indicated when the response does not have a hyphen (-) after the code
            final SmtpAsyncResponse doneResponse = new SmtpAsyncResponse(responses);
            removeFirstEntry();
            curEntry.getFuture().done(doneResponse);
        }
        // Multiline responses go here and continues. EHLO is an example
    }

    @Override
    public SmtpFuture<Boolean> close() {
        final SmtpFuture<Boolean> closeFuture = new SmtpFuture<>();
        if (isChannelClosed()) {
            closeFuture.done(Boolean.TRUE);
        } else {
            if (isDebugEnabled()) {
                logger.debug(SESSION_LOG_REC, sessionId, getUserInfo(), "Closing the session via close().");
            }
            final Channel channel = channelRef.get();
            final ChannelPromise channelPromise = channel.newPromise();
            final SmtpChannelClosedListener channelClosedListener = new SmtpChannelClosedListener(closeFuture);
            channelPromise.addListener(channelClosedListener);
            // this triggers handleChannelDisconnected() hence no need to handle queue here. We use close() instead of disconnect() to ensure it is
            // clearly a close action regardless TCP or UDP
            channel.close(channelPromise);
        }
        return closeFuture;
    }
}
