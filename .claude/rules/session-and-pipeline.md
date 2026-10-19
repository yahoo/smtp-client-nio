---
paths:
  - "core/src/main/java/com/yahoo/smtpnio/async/internal/**"
  - "core/src/main/java/com/yahoo/smtpnio/async/netty/**"
  - "core/src/main/java/com/yahoo/smtpnio/async/client/**"
---

# Session, connection setup and channel handlers (`async/internal/`, `async/netty/`, `async/client/`)

`SmtpAsyncSessionImpl`, the handlers in `async/netty/` and the connection-setup handlers in `async/client/` (`SslDetectHandler`,
`StarttlsHandler`) are the concurrency core. This code runs on Netty event-loop threads while callers invoke `execute` / `close` from
their own threads. Changes need care and thorough tests.

## Session model

- A session wraps one `Channel`. `requestsQueue` holds at most one `SmtpCommandEntry`. A second `execute` while one is pending throws
  `COMMAND_NOT_ALLOWED`, and `execute` on a closed channel throws `OPERATION_PROHIBITED_ON_CLOSED_CHANNEL`. Keep that guarantee: response
  routing assumes the head of the queue owns every reply line.
- Entry states: `REQUEST_IN_PREPARATION` → `REQUEST_SENT` (set in the write listener `operationComplete`) → `RESPONSES_DONE`. A
  continuation (354 / 334) moves the entry back to `REQUEST_IN_PREPARATION` while the next part is written.
- The idle timer (`IdleStateHandler` `ALL_IDLE`, from `SmtpAsyncSessionConfig.getReadTimeout()`) fails a command with `CHANNEL_TIMEOUT`
  only in `REQUEST_SENT`.
- A reply completes the command when `SmtpResponse.isLastLineResponse()` is true (no hyphen after the code). Multi-line replies such as
  `EHLO` accumulate until then.
- Any channel exception, write failure, timeout, disconnect, or error while building a continuation completes the future exceptionally
  **and closes the session**. A session is never reused after an error.
- The session never calls `SmtpRequest.cleanup()`. That is the caller's job once the future is done.
- `execute` adds the entry to `requestsQueue` before `getCommandLineBytes()` runs, so a command that throws while building its line
  leaves the session refusing every later command with `COMMAND_NOT_ALLOWED`. Keep this in mind when changing `execute`.

## Connection setup (`async/client/`)

- `SmtpAsyncClient.createSession` picks one of four `SessionMode`s from `SmtpAsyncSessionData.isSslEnabled()` and
  `SmtpAsyncSessionConfig.getEnableStarttls()` and adds the matching handlers after connect. Handlers in `SmtpClientChannelInitializer` are
  shared bootstrap state and must stay session-less. Per-session handlers are added after connect.
- `SslDetectHandler` sits after the `SslHandler` and removes itself once the first decrypted bytes arrive. On a
  `NotSslRecordException` it starts a new plain-text connection through `createStartTlsSession`, hands it the same session future, and
  closes the old channel without failing that future.
- `StarttlsHandler` is a three-state machine (`GET_SERVER_GREETING` → `GET_EHLO_RESP` → `GET_STARTTLS_RESP`). Any unexpected reply fails
  the session future with a specific `FailureType` (`BAD_EHLO_RESPONSE`, `NO_STARTTLS_CAPABILITY`, `BAD_STARTTLS_RESPONSE`, ...) and closes
  the channel. The session is created only after the TLS handshake future succeeds.
- Every `SslHandler` is built by `SmtpAsyncClient.createSSLHandler`, which sets endpoint identification to `HTTPS` and the SNI names on
  both the default-context and caller-`SSLContext` paths. Build new TLS paths through it.
- Handler names (`sslHandler`, `SslDetectHandler`, `StarttlsHandler`, `SmtpClientConnectHandler`, `SmtpClientCommandRespHandler`) are
  used to add and remove handlers, so don't rename them.

## Handlers (`async/netty/`)

- `SmtpClientConnectHandler` lives only until the greeting: it removes itself, then on 220 builds the session (which adds
  `SmtpClientCommandRespHandler`), otherwise fails with `CONNECTION_FAILED_INVALID_GREETING_CODE` and closes.
- `SmtpClientCommandRespHandler` forwards to `SmtpCommandChannelEventProcessor` and drops its reference after `channelInactive`.
- Handlers null their references in a private `cleanup()` once their job is done, and `channelInactive` returns early when that has
  already happened. Keep both when adding fields.

## Logging

- Gate on `isDebugEnabled()`: trace logs every session, and debug logs only sessions whose `DebugMode` is `DEBUG_ON`.
- Format: `"[{},{}] C:{}"` / `"[{},{}] S:{}"` with `sessionId, sessionCtx`. Client lines of sensitive commands log `getDebugData()`, never
  the raw line. Server replies are logged as received.

## Testing here

`SmtpAsyncSessionImplTest`, the `netty` handler tests and the `client` handler tests mock `Channel`, `ChannelPipeline`,
`ChannelHandlerContext`, `ChannelPromise` and `Logger` with Mockito, capture listeners with `ArgumentCaptor`, and drive
`operationComplete`, replies and idle events by hand. Follow that pattern rather than opening sockets. TLS behaviour is tested by running
the handshake in memory between two `SSLEngine`s (`SmtpAsyncClientHostnameVerificationTest`).
