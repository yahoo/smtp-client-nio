---
paths:
  - "core/src/test/**"
---

# Testing conventions (`core/src/test/`)

- **Framework:** TestNG (`org.testng.annotations.Test`, `org.testng.Assert`). Argument order is `Assert.assertEquals(actual, expected, message)`,
  the reverse of JUnit. Pass a message in new asserts.
- **Mocking:** Mockito **1.10.19** (`mockito-all`). Use `org.mockito.Mockito`, `ArgumentCaptor` and `Answer`, as the existing tests do.
- **Layout:** one `<ClassName>Test` per main class, same package, so package-private members are reachable. Constructors that take a
  `Logger`, `Bootstrap` or `EventLoopGroup` exist for injection. Use them instead of reflection where possible.
- **No threads.** Don't start a `Thread`, executor or event loop in a unit test. Mock the collaborator (`Channel`, `ChannelPromise`,
  `EventLoopGroup`), capture its listener with `ArgumentCaptor`, and call it from the test thread. The one existing exception is the
  public-constructor test in `SmtpAsyncClientTest`, which needs a real event loop. Don't copy it.
- **No network, no sleeps.** Drive Netty with mocked `Channel` / `ChannelPipeline` / `ChannelHandlerContext` and captured listeners. For
  TLS, run the handshake in memory between two `SSLEngine`s, and give each case its own client `SSLContext`: a shared one resumes the
  cached session for the same host and port, and the JDK client then drops its SNI name. The test certificates live in
  `src/test/resources/tls`.
- **Test the change, not the stack.** Add unit tests only for the code the change adds or alters, in the `<ClassName>Test` of the class
  you touched. Don't add tests for unchanged code, don't reorganize existing tests, and don't drive a change through the whole stack
  (client → session → handler → command) when a unit test of the changed class covers it. The coverage gate is met by covering the
  new methods and branches.
- **Specific `throws` clauses.** Declare the checked exceptions a test can throw (`SmtpAsyncClientException`, `GeneralSecurityException`,
  `IOException`, ...), never `throws Exception`. Older tests that declare `throws Exception` predate this rule; don't copy them.
- **Independent tests.** Every `@Test` builds its own state. Fields set once in `@BeforeClass` and never changed are fine (e.g. the
  cleanup-check field set). Don't add new fields that one test changes and another reads. Never change static or shared state.
- **Mocks only where needed.** Prefer real objects: `new SmtpResponse("250 OK")`, `Unpooled` buffers, real commands. Mock `Channel`,
  `Logger` and the like, and create those mocks inside the test. Don't mock the internals of Netty or javax.mail.
- **Hostile and non-ASCII inputs.** Any code that writes caller text needs cases with CR, LF, CRLF followed by an injected command, NUL,
  SOH (for SASL fields), DEL, and non-ASCII text such as accented Latin, CJK and emoji, all of which must be rejected with `INVALID_INPUT`.
  ESMTP values also need SPACE and `=`.
- **Asserts in `@Test` methods.** New helpers return values (e.g. a boolean or the captured object) and the test asserts on them.
- **No static helpers.** Helpers are instance methods (constants may be `private static final`).
- **Javadoc and checkstyle apply to tests too:** every test method gets a Javadoc with `@throws` for each declared exception (the house
  style writes "will not throw in a successful test"), and parameters are `final`. `MagicNumber` and `JavadocPackage` are suppressed under
  `test/`.
- **Command tests** check the exact wire string (`"RCPT TO:<a@example.com>\r\n"`), `getCommandType()`, `getDebugData()`, and that
  `cleanup()` nulled every non-primitive field. Copy the reflection block in `@BeforeClass` from an existing command test such as
  `RecipientCommandTest`.
- **Coverage:** the build fails below 98% line/branch coverage or with any method left uncovered. After
  `mvn clean install -Dgpg.skip=true`, read `core/target/jacoco/index.html` to find what is missed.
- Surefire excludes the TestNG groups `notIsolate` and `EventListenersRegression`. Don't tag normal tests with them.
- Tests run with `-Dfile.encoding=ANSI_X3.4-1968` (US-ASCII). Always pass a charset explicitly (`toString(StandardCharsets.US_ASCII)`),
  never rely on the default.
