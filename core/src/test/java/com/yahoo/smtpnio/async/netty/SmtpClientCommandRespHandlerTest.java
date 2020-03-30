/*
 * Copyright Verizon Media
 * Licensed under the terms of the Apache 2.0 license. See LICENSE file in project root for terms.
 */
package com.yahoo.smtpnio.async.netty;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import org.mockito.Mockito;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.yahoo.smtpnio.async.exception.SmtpAsyncClientException;
import com.yahoo.smtpnio.async.response.SmtpResponse;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * Unit test for {@link SmtpClientCommandRespHandler}.
 */
public class SmtpClientCommandRespHandlerTest {

    /** Fields to check for cleanup. */
    private Set<Field> fieldsToCheck;

    /**
     * Setup reflection.
     */
    @BeforeClass
    public void setUp() {
        // Use reflection to get all declared non-primitive non-static fields (We do not care about inherited fields)
        final Class<?> c = SmtpClientCommandRespHandler.class;
        fieldsToCheck = new HashSet<>();

        for (final Field declaredField : c.getDeclaredFields()) {
            if (!declaredField.getType().isPrimitive() && !Modifier.isStatic(declaredField.getModifiers())) {
                declaredField.setAccessible(true);
                fieldsToCheck.add(declaredField);
            }
        }
    }

    /**
     * Tests {@code decode} method.
     *
     * @throws IllegalArgumentException will not throw in this test
     * @throws SmtpAsyncClientException will not throw in this test
     */
    @Test
    public void testDecode() throws IllegalArgumentException, SmtpAsyncClientException {
        final SmtpCommandChannelEventProcessor processor = Mockito.mock(SmtpCommandChannelEventProcessor.class);
        final SmtpClientCommandRespHandler handler = new SmtpClientCommandRespHandler(processor);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final SmtpResponse resp = new SmtpResponse("354 Ok go ahead");
        final List<Object> out = new ArrayList<>();
        handler.decode(ctx, resp, out);
        Mockito.verify(processor, Mockito.times(1)).handleChannelResponse(resp);
    }

    /**
     * Tests {@code exceptionCaught} method.
     *
     * @throws IllegalArgumentException will not throw in this test
     */
    @Test
    public void testExceptionCaught() throws IllegalArgumentException {
        final SmtpCommandChannelEventProcessor processor = Mockito.mock(SmtpCommandChannelEventProcessor.class);
        final SmtpClientCommandRespHandler handler = new SmtpClientCommandRespHandler(processor);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final TimeoutException ex = new TimeoutException("too late, my friend");
        handler.exceptionCaught(ctx, ex);
        Mockito.verify(processor, Mockito.times(1)).handleChannelException(ex);
    }

    /**
     * Tests {@code userEventTriggered} method and the event is {@link IdleStateEvent}, state is {@code READ_IDLE}.
     *
     * @throws IllegalArgumentException will not throw in this test
     */
    @Test
    public void testUserEventTriggeredIdleStateEventReadIdle() throws IllegalArgumentException {
        final SmtpCommandChannelEventProcessor processor = Mockito.mock(SmtpCommandChannelEventProcessor.class);
        final SmtpClientCommandRespHandler handler = new SmtpClientCommandRespHandler(processor);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final IdleStateEvent idleEvent = Mockito.mock(IdleStateEvent.class);
        Mockito.when(idleEvent.state()).thenReturn(IdleState.ALL_IDLE);
        handler.userEventTriggered(ctx, idleEvent);
        Mockito.verify(processor, Mockito.times(1)).handleIdleEvent(idleEvent);
    }

    /**
     * Tests {@code userEventTriggered} method and the event is {@link IdleStateEvent}, state is NOT {@code READ_IDLE}.
     *
     * @throws IllegalArgumentException will not throw in this test
     */
    @Test
    public void testUserEventTriggeredIdleStateEventNotReadIdle() throws IllegalArgumentException {
        final SmtpCommandChannelEventProcessor processor = Mockito.mock(SmtpCommandChannelEventProcessor.class);
        final SmtpClientCommandRespHandler handler = new SmtpClientCommandRespHandler(processor);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final IdleStateEvent idleEvent = Mockito.mock(IdleStateEvent.class);
        Mockito.when(idleEvent.state()).thenReturn(IdleState.WRITER_IDLE);
        handler.userEventTriggered(ctx, idleEvent);
        Mockito.verify(processor, Mockito.times(0)).handleIdleEvent(Mockito.any(IdleStateEvent.class));
    }

    /**
     * Tests {@code userEventTriggered} method and the event is NOT {@link IdleStateEvent}.
     *
     * @throws IllegalArgumentException will not throw in this test
     */
    @Test
    public void testUserEventTriggeredNotIdleStateEvent() throws IllegalArgumentException {
        final SmtpCommandChannelEventProcessor processor = Mockito.mock(SmtpCommandChannelEventProcessor.class);
        final SmtpClientCommandRespHandler handler = new SmtpClientCommandRespHandler(processor);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        final String otherEvent = "test event";
        handler.userEventTriggered(ctx, otherEvent);
        Mockito.verify(processor, Mockito.times(0)).handleIdleEvent(Mockito.any(IdleStateEvent.class));
    }

    /**
     * Tests {@code channelInactive} method.
     *
     * @throws IllegalAccessException will not throw in this test
     * @throws IllegalArgumentException will not throw in this test
     */
    @Test
    public void testChannelInactive() throws IllegalArgumentException, IllegalAccessException {
        final SmtpCommandChannelEventProcessor processor = Mockito.mock(SmtpCommandChannelEventProcessor.class);
        final SmtpClientCommandRespHandler handler = new SmtpClientCommandRespHandler(processor);

        final ChannelHandlerContext ctx = Mockito.mock(ChannelHandlerContext.class);
        handler.channelInactive(ctx);
        Mockito.verify(processor, Mockito.times(1)).handleChannelClosed();
        handler.channelInactive(ctx); // cleanup already called, it should return right away and have no additional effect

        // Verify if cleanup happened correctly.
        for (final Field field : fieldsToCheck) {
            Assert.assertNull(field.get(handler), "Cleanup should set " + field.getName() + " as null");
        }
    }
}
