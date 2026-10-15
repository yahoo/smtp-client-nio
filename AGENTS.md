# AGENTS.md

Guidance for AI coding agents (and humans) working in this repository. This file holds the rules for every change. Area-specific detail
lives in `.claude/rules/`. Claude Code loads each page automatically when matching files are touched; other agents should read the
matching page before changing that area:

| Changing | Read |
|---|---|
| a command class in `async/request/` | `.claude/rules/commands.md` |
| `async/internal/`, `async/netty/`, or `async/client/` (session, pipeline, SSL / STARTTLS setup) | `.claude/rules/session-and-pipeline.md` |
| any test | `.claude/rules/testing.md` |

## What this is

`smtp-client-nio` is an open-source Java library (Apache 2.0, published to Maven Central as `com.yahoo.smtpnio:smtpnio.core`) that
provides a non-blocking SMTP client built on Netty. Callers create an `SmtpAsyncClient`, open an `SmtpAsyncSession` per server
connection with `createSession(...)`, and `execute(...)` `SmtpRequest` commands that complete an `SmtpFuture<SmtpAsyncResponse>`.
The response is the list of raw `SmtpResponse` reply lines; there is no mapper to typed results.

This is a public repository. Everything committed here (code, comments, commit messages, docs, PR text) must make sense to an outside
contributor: no links to private systems, internal ticket ids, internal hostnames, or customer data.

## Build and verify

CI builds with **JDK 17**. JDK 21 also builds locally. `core` compiles with `-source/-target 9`.

```bash
# Full build with every gate: checkstyle, compile, tests, JaCoCo coverage check, SpotBugs, PMD (~15s, works offline once deps are cached)
mvn clean install -Dgpg.skip=true

# Compile + checkstyle + SpotBugs + PMD without tests: proves it compiles and is clean, not that it works
mvn clean install -DskipTests -Dgpg.skip=true

# One test class / one method (checkstyle still runs in the validate phase; JaCoCo check only runs at verify)
mvn -pl core test -Dtest=RecipientCommandTest
mvn -pl core test -Dtest='RecipientCommandTest#testGetCommandLine'

# Coverage report after a build: core/target/jacoco/index.html
```

A change is done only when `mvn clean install -Dgpg.skip=true` passes. Don't guess at style or formatting: run the build and fix
what the tools report. The gates are strict:

| Gate | Where | What fails the build |
|---|---|---|
| Checkstyle (`checkstyle.xml`) | `validate`, main **and** test sources | any violation (every enabled check is severity `error`) |
| JaCoCo (`core/pom.xml`) | `verify` | instruction/line/branch/complexity < 98%, any method or class not covered (100%) |
| SpotBugs (`spotbugs-include.xml`) | `verify` | medium+ findings in main code that match a bug pattern listed in the include filter |
| PMD (`pmd-ruleset.xml`) | `verify` | `StringInstantiation`, unguarded log statements, beyond the one violation already allowed |

Coverage is near-total and **every method must be covered**, so every new method and branch needs a test. Plan the tests together
with the code.

`SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder"` during tests is expected (no logger binding on the test classpath).

## Layout

Single Maven module `core/` under the parent `pom.xml`. The parent pom says "DO NOT ADD DEPENDENCIES TO THIS FILE": declare versions in the
parent's `<dependencyManagement>` and the dependency itself in `core/pom.xml`.

```
core/src/main/java/com/yahoo/smtpnio/
  async/client/     Public entry points: SmtpAsyncClient (bootstrap, TLS, pipeline), SmtpAsyncSession (interface),
                    SmtpAsyncSessionData (host, port, SSL, SNI, SSLContext), SmtpAsyncSessionConfig (timeouts, STARTTLS),
                    SmtpFuture, SmtpAsyncCreateSessionResponse; plus connection-setup handlers SslDetectHandler and StarttlsHandler
  async/internal/   SmtpAsyncSessionImpl: the in-flight command queue, continuations, idle timeout, logging
  async/netty/      Channel handlers: greeting check (SmtpClientConnectHandler), response dispatch (SmtpClientCommandRespHandler)
  async/request/    One class per SMTP command + SmtpArgumentFormatter (validation of caller arguments)
  async/response/   SmtpResponse (one reply line: code, reply type, last-line and continuation checks), SmtpAsyncResponse
  async/exception/  SmtpAsyncClientException and its FailureType enum
  client/           SmtpClientRespReader: frames server bytes into CRLF-terminated lines
  command/          SmtpClientRespDecoder: String -> SmtpResponse
core/src/test/java/ Mirrors main, one *Test per class (TestNG + Mockito)
```

## How a command flows

1. `SmtpAsyncClient.createSession` connects. The shared pipeline is: IdleStateHandler → SmtpClientRespReader → StringDecoder/Encoder →
   ChunkedWriteHandler → SmtpClientRespDecoder. The session mode then adds handlers:
   - SSL only: an `SslHandler` first, then `SmtpClientConnectHandler`.
   - SSL and STARTTLS: also `SslDetectHandler`. If the server does not speak TLS (`NotSslRecordException`), it reconnects in plain text
     and runs the STARTTLS flow.
   - STARTTLS only: `StarttlsHandler` reads the greeting, sends `EHLO`, requires the `STARTTLS` capability, sends `STARTTLS`, expects 220,
     then adds an `SslHandler` and builds the session when the handshake completes.
   - Neither: `SmtpClientConnectHandler`.
   Every `SslHandler` comes from `SmtpAsyncClient.createSSLHandler`, which turns on host-name verification (SNI name if given, else the host).
2. `SmtpClientConnectHandler` waits for the server greeting and then removes itself. On 220 it creates `SmtpAsyncSessionImpl`, which
   adds `SmtpClientCommandRespHandler`, and completes the session future; otherwise it fails the future and closes the channel.
3. `session.execute(cmd)` queues the entry and writes `cmd.getCommandLineBytes()`. SMTP has no tags.
4. Each reply line goes to `handleChannelResponse`. A 354 or 334 reply is a continuation and asks the command for
   `encodeCommandAfterContinuation` (by default, write `getNextCommandLineAfterContinuation`). The first line without a hyphen after the
   code completes the future with every line collected. On a channel error, write failure, timeout or disconnect the future fails and
   the session closes.
5. The caller reads `getResponseLines()` and decides what each code means. A 5xx reply is a successful future, not an exception.

## Invariants: do not break these

- **One command in flight per session.** `execute` throws `COMMAND_NOT_ALLOWED` while the queue is non-empty.
- **Caller input never reaches the wire unchecked.** Write every caller string through `SmtpArgumentFormatter`
  (`formatArgument`, `formatEsmtpKeyword`, `formatEsmtpValue`). Never concatenate it into the line. A raw CR/LF would inject a second
  SMTP command, and NUL / SOH would split the SASL fields of `AUTH PLAIN` / `AUTH XOAUTH2`.
- **Wire bytes are printable US-ASCII.** RFC 5321 section 4.1.2 allows no control or non-ASCII characters in command arguments and
  offers no escaping, so such input is rejected with `INVALID_INPUT`, never encoded or replaced. Error messages name the argument and
  the offending index, never the value.
- **Secrets are never logged.** A command that carries credentials returns `true` from `isCommandLineDataSensitive()` and returns a
  redacted `getDebugData()` (`<secret>`, `<username>`, `<password>`).
- **`cleanup()` nulls every non-primitive, non-static field**, including inherited ones. Command tests check this by reflection.
- **TLS host-name verification stays on** for the SSL and the STARTTLS paths, including for a caller-supplied `SSLContext`.
- **Errors are `SmtpAsyncClientException` with a `FailureType`.** It is a checked exception; add a new enum constant when none fits.
- **Never throw an unchecked exception, in main or test code.** That means `RuntimeException` and every subclass
  (`IllegalArgumentException`, `IllegalStateException`, `NullPointerException`, `UnsupportedOperationException`, ...) and `Error`.
  Reject bad input with `SmtpAsyncClientException(FailureType.INVALID_INPUT, ...)`, not `IllegalArgumentException`. Where a
  dependency (Netty, javax.mail) can throw one, catch it at that call and wrap it, as the session does. Test helpers
  return values or declare a specific checked exception. Two older spots break this rule, the `IllegalArgumentException` for a null
  host in `SmtpAsyncClient.createSession` and the `RuntimeException` wrapping an `IOException` in `DataStreamCommand`; don't copy them.
- **Public classes and methods are a published contract.** Do not change or remove public signatures. Add an overload and keep the old
  one delegating. The version is set in both `pom.xml` and `core/pom.xml`, and in the `README.md` dependency snippet. Maintainers bump it
  in a release PR, not in feature changes.

## Code style (enforced by checkstyle unless noted)

- Javadoc with complete `@param`/`@return`/`@throws`: checkstyle requires it on public classes, methods and fields, and by convention
  every non-public member, constants included, has one too.
- All parameters `final`. No star imports. Lines ≤ 150 chars. Braces always. A `default` in every switch.
- No `System.out/err/in/exit`. Do not catch `Exception` or `Throwable`; catch specific types (a `SmtpAsyncClientException | RuntimeException`
  multi-catch is used deliberately in the session).
- Guard log calls with `logger.isDebugEnabled()` etc. (PMD). The log prefix is `"[{},{}] ..."` with `sessionId, sessionCtx`.
- Annotate nullability with `javax.annotation.Nonnull` / `@Nullable`. Constants are `static final`.
- Prefer instance methods over static ones, test helpers included (constants are fine as `static final`). *(maintainer convention)*
- Keep main code to Java 9 APIs. The compiler uses `-source/-target 9` without `--release`, so a newer library call compiles on JDK 17 and
  then fails on an older runtime.
- Match the surrounding code: write command lines with chained `Unpooled.buffer(...).writeBytes(...).writeByte(...)`, keep keywords as
  `private static final String` constants, and take separators from `SmtpClientConstants`.

## Design rules (judgment the tools cannot enforce)

- **Narrow types over wide ones.** In new public classes and methods, use an enum instead of a `boolean` flag (as `DebugMode` does) and a
  small value class instead of loose strings when arguments belong together (as `MailCommand.MailParameter` does).
- **Immutable by default.** New value classes have `final` fields set in the constructor and no setters, or a builder as
  `SmtpAsyncSessionData` has. `SmtpAsyncSessionConfig` is the deliberate mutable exception.
- **No lambdas, streams or method references in main code.** Use loops and anonymous classes, as the rest of the library does.
- **Wire bytes go into a Netty `ByteBuf`** (`Unpooled.buffer`), never through `String` concatenation, and every `getBytes` call names
  its charset (`StandardCharsets.US_ASCII`; never the platform default).
- **Size an `ArrayList` up front** when the count is known.
- **No throwaway temporaries.** Chain calls instead of naming a value used once, unless the name adds real clarity.
- **Use the RFC's words.** Name things after RFC 5321 terms (*sender*, *recipient*, *mail parameter*, *esmtp-keyword*, *esmtp-value*,
  *reply code*), as the existing classes do.
- **No mutable static state.** State lives in instances, so sessions stay independent. `ARGUMENT_FORMATTER` is shared only because it
  is stateless.

## Comments

- Default to no inline comment. Write one only when a competent reader would otherwise make a wrong change. One line, two at most.
- Explain *why*, not *what*: cite the RFC section when the reason is protocol behavior (e.g. "RFC 5321 section 4.1.2"), or name the hazard.
- No narration of the next line and no history ("used to", "previously", "replaced"). Git and the PR hold that.
- Javadoc that checkstyle requires is not optional. The class Javadoc is the place for the longer protocol rationale, as
  `SmtpArgumentFormatter` shows.
- A `TODO` / `FIXME` names a GitHub issue. Otherwise fix it now or leave it out.

## Tests

See `.claude/rules/testing.md`. Short version: TestNG `Assert.assertEquals(actual, expected, message)`, Mockito 1.10 (`mockito-all`, old
API), no real network, specific `throws` clauses, no new threads (mock instead), no static helpers.
For each change, add unit tests that cover only the new or changed code, not the entire stack.

## Commits and PRs

- Commit subject: imperative, ≤ 72 chars, describes the behavior change ("Verify the server certificate against the server name").
  The body explains what was wrong and why the fix is right, wrapped at ~72 columns. Don't restate the diff.
- One logical change per commit and per PR. Don't fold unrelated cleanups in.
- Stage files by name. Never `git add -A` / `git add .`: it keeps scratch files out of commits.
- Never use `--no-verify`. Never push directly to `yahoo/smtp-client-nio`; open PRs from a fork. Force-push only your own PR branch, and
  only with `--force-with-lease`. Once a PR has review comments, don't amend or force-push it without the author's agreement.
- Fill in `pull_request_template.md`. Add or update tests for every change and update `README.md` when public behavior changes.
- Before opening a PR, run the `review-change` skill (`.claude/skills/review-change/`) or walk its checklist by hand.
- Never commit `target/`, IDE files, `ci/deploy/` (decrypted signing keys), or any key material. Do not edit `ci/*.enc` or `ci/deploy.sh`
  unless the task is about release automation.
- CI is Travis (`.travis.yml`, JDK 17, `mvn clean install -Dgpg.skip=true`). Pushes to `master` deploy to Maven Central via `ci/deploy.sh`.
  Treat merging to `master` as releasing.

## Known gotchas

- `spotbugs-exclude.xml` starts with a DOCTYPE, which SpotBugs refuses to read ("Unable to read filter ... DOCTYPE is disallowed"), so the
  exclude list is currently ignored. Only the include filter applies.
- Checkstyle runs in `validate`, so even `mvn test` fails on a style violation in a test file.
- PMD allows exactly one violation (`pmdViolationCount` in `core/pom.xml`), and the connect-result debug log in `SmtpAsyncClient`
  already uses it: PMD doesn't recognize its `isTraceEnabled() || DEBUG_ON` guard. Any new unguarded log call fails the build.
- Artifact signing lives only in the `ossrh` release profile, which `ci/deploy.sh` activates. `-Dgpg.skip=true` in the commands above
  mirrors CI and is harmless locally.
- `StringDecoder` / `StringEncoder` in `SmtpClientChannelInitializer` use the JVM default charset. Surefire pins tests to US-ASCII
  (`-Dfile.encoding=ANSI_X3.4-1968` in the parent pom), so a test can pass while a UTF-8 production JVM behaves differently.
- When debug logging is on, `SmtpAsyncSessionImpl.sendContinuation` calls `getNextCommandLineAfterContinuation` once to log it and again
  through `encodeCommandAfterContinuation` to send it, unless the command is sensitive. A command whose continuation consumes state (an
  `InputStream`, a step counter) must return `true` from `isCommandLineDataSensitive()`, as `DataStreamCommand` and the AUTH commands do.
- The idle handler is configured for `ALL_IDLE` only (`IdleStateHandler(0, 0, readTimeout)`). `SmtpClientCommandRespHandler` reacts to
  `ALL_IDLE`, but the `READER_IDLE` branches in `SmtpClientConnectHandler` and `StarttlsHandler` never fire on a real pipeline.
- `SmtpAsyncSessionImpl.execute` queues the entry before it calls `getCommandLineBytes()`. If building the line throws (for example
  `INVALID_INPUT` for a rejected argument), the entry stays queued and every later `execute` on that session throws
  `COMMAND_NOT_ALLOWED`. Close the session after such an error.
