---
name: review-change
description: Review a branch or working-tree change in smtp-client-nio before it becomes a PR. Checks the diff against the repo's AGENTS.md and .claude/rules (wire-encoding safety, secrets, cleanup, TLS, public compatibility, design and test conventions), runs the Maven gate, and reports BLOCKER/WARNING findings with file:line. Use when asked to review changes, check a branch before a PR, or self-review work just finished.
---

# Review a smtp-client-nio change

Read-only. Don't edit files, stage or commit while reviewing. Report the findings, and fix only when asked.

## 1. Collect the change

```bash
git fetch https://github.com/yahoo/smtp-client-nio.git master:refs/remotes/upstream-master   # or use an existing upstream remote
git diff --stat upstream-master...HEAD
git diff upstream-master          # committed + uncommitted changes as one patch
git status --short                # untracked files: ask which ones are part of the change before reading them
```

Read every changed file in full, plus `AGENTS.md` and the `.claude/rules/` page for each area the change touches.

## 2. Check, in this order

**Protocol and security: BLOCKER if violated**
- Caller-supplied text reaches the wire only via `SmtpArgumentFormatter` (`formatArgument`, `formatEsmtpKeyword`, `formatEsmtpValue`).
  Look for `+`, `append`, `writeBytes` or `getBytes` of raw arguments.
- Every `getBytes` names its charset (never the platform default), and every line ends with CRLF.
- Secret-bearing commands: `isCommandLineDataSensitive()` returns true and `getDebugData()` is redacted at every step. No secret or rejected
  value in any log or exception message.
- A continuation that consumes state is marked sensitive, so the extra debug-log call cannot advance or drain it.
- `cleanup()` nulls every non-primitive field, including new ones.
- TLS host-name verification is still applied on the SSL and STARTTLS paths, through `SmtpAsyncClient.createSSLHandler`.
- No `throw` of `RuntimeException`, any of its subclasses, or `Error`, in main or test code. Failures are `SmtpAsyncClientException`.
- Session invariants hold: one command in flight; errors fail the future with an `SmtpAsyncClientException` and close the session.
- No public signature removed or changed incompatibly; additions come as overloads.
- No API newer than Java 9 in main code. No new dependency without a version in the parent `<dependencyManagement>`.

**Design and style: WARNING unless the build fails**
- `AGENTS.md` "Design rules" and "Comments": enum over boolean in new public methods, immutable values, no lambdas/streams in main
  code, `ByteBuf` for wire bytes, RFC 5321 vocabulary, no mutable static state, no narration or history comments.
- Logging is guarded and uses the `"[{},{}] ..."` prefix.
- Nothing internal or private in code, comments or the commit message (public repository).

**Tests: BLOCKER if behavior is untested**
- Every new method and branch has a test, including error paths. The JaCoCo gate is 98%, with 100% of methods.
- Tests are scoped to the change: unit tests in the touched class's `*Test`, with no tests added for unchanged code and no
  whole-stack tests where a unit test would do. WARNING if not.
- Command tests: the exact wire string with `\r\n`, `getCommandType()`, `getDebugData()`, cleanup reflection, and CR/LF/NUL/non-ASCII
  cases. New arguments are added to `CommandArgumentValidationTest`.
- `.claude/rules/testing.md`: TestNG assert order with messages, specific `throws` clauses, asserts in `@Test`, instance helpers (no
  static), no threads (mock instead), no network or sleeps, a fresh `SSLContext` per TLS case, independent tests.

**Hygiene**
- Commit subject is imperative and ≤ 72 chars, and the body says why. One logical change.
- No `target/`, IDE files, key material or scratch files in the diff.

## 3. Run the gate

```bash
mvn clean install -Dgpg.skip=true
```

Report the test count and pass/fail. If the build cannot run, say so explicitly rather than skipping silently.

## 4. Report

```
## Review: <branch>

### Findings
- BLOCKER: <what is wrong and why it matters> (path/File.java:line)
- WARNING: <...> (path/File.java:line)

### Build
- Tests run: N, Failures: N. Checkstyle / JaCoCo / SpotBugs / PMD: pass | fail (<first error>)

### Verdict
READY FOR PR | NEEDS WORK (N blockers, N warnings)
```

Flag only real violations, each with a cited rule and a file:line. Don't pad the report with style nits that the build already enforces.
