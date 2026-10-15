---
paths:
  - "core/src/main/java/com/yahoo/smtpnio/async/request/**"
---

# Command classes (`async/request/`)

Each class in `core/src/main/java/com/yahoo/smtpnio/async/request/` builds the client side of one SMTP command. The package is the
security boundary: caller strings become wire bytes in it.

## Adding or changing a command

1. **Pick the closest base class** instead of implementing `SmtpRequest` from scratch:

   | Base | Use for |
   |---|---|
   | `AbstractSmtpCommand`, keyword only | `NOOP`, `QUIT`, `RSET`, `HELP`, `STARTTLS`, ...; the inherited `getCommandLineBytes()` suffices |
   | `AbstractSmtpCommand`, overriding `getCommandLineBytes()` | commands with arguments: `EHLO`, `HELO`, `MAIL`, `RCPT` |
   | `AbstractSmtpCommand`, overriding the continuation methods | `DATA` (`DataCommand`, `DataStreamCommand`) |
   | `AbstractAuthenticationCommand` | `AUTH <mechanism>`; already sensitive and typed `AUTH`. Add the mechanism to its `Mechanism` enum |

2. **Add an `SmtpRFCSupportedCommandType` constant** and return it from `getCommandType()`. Callers may also supply their own
   `SmtpCommandType`, so do not switch exhaustively on the enum elsewhere.
3. **Encode arguments safely** with the shared `ARGUMENT_FORMATTER` (`SmtpArgumentFormatter`, package-private):
   - Free-form text (domain, sender, recipient, credentials): `formatArgument(value, buf, "argument name")`. It rejects control and
     non-ASCII characters with `INVALID_INPUT`.
   - SASL payloads built as a string before base64: the `StringBuilder` overload of `formatArgument`.
   - ESMTP parameters (RFC 5321 section 4.1.2): `formatEsmtpKeyword` and `formatEsmtpValue`, which also refuse SPACE and `=` in a value.
   - Keywords and separators: `private static final String` keyword constants written with `getBytes(StandardCharsets.US_ASCII)`, and
     `SmtpClientConstants` (`SPACE`, `COLON`, `L_ANGLE_BRACKET`, ...) for single characters. End every line with `CRLF_B`.
4. **Continuations.** A command that the server answers with 334 or 354 overrides `getNextCommandLineAfterContinuation`, and
   `encodeCommandAfterContinuation` when it must write something other than one buffer (`DataStreamCommand` writes a `ChunkedStream`).
   - Track multi-step exchanges with a private enum state, as `AuthenticationLoginCommand` does. Advance the state only after the
     argument has passed validation. When there is nothing more to send, throw `MORE_INPUT_THAN_EXPECTED`.
   - The default throws `OPERATION_NOT_SUPPORTED_FOR_COMMAND`. Keep that for commands that never expect a continuation.
   - With debug logging on, the session calls `getNextCommandLineAfterContinuation` an extra time to log it unless the command is
     sensitive. If calling it twice would consume or advance state, make the command sensitive.
5. **Sensitive data.** If the line can carry a secret, return `true` from `isCommandLineDataSensitive()` and return redacted text from
   `getDebugData()` that tracks the current step, as `AuthenticationLoginCommand` does.
6. **`cleanup()`** calls `super.cleanup()` and nulls every non-primitive, non-static field. It also closes resources the command owns
   (`DataStreamCommand` closes its stream). The command's test verifies the nulling by reflection.
7. **Tests** in `core/src/test/java/com/yahoo/smtpnio/async/request/<Name>Test.java`: the exact wire string including `\r\n`, the
   `getCommandType()`, `isCommandLineDataSensitive()`, `getDebugData()`, the cleanup reflection check, and the continuation behaviour.
   For any new caller-supplied argument, add injection cases (CRLF followed by a second command, NUL, and for SASL fields SOH) to
   `CommandArgumentValidationTest`. Character-class cases (each control character, non-ASCII text) live in `SmtpArgumentFormatterTest`;
   extend that when the formatter itself changes.

## Don'ts

- Don't build a line by concatenating caller input, or with a `getBytes()` that relies on the platform charset.
- Don't widen visibility: the `SmtpArgumentFormatter` methods, `SmtpClientConstants` and `SmtpRFCSupportedCommandType` are
  package-private by design.
- Don't put the rejected value in an exception message. It may be a password or token.
- Don't change an existing command's wire output for valid input. Callers and tests depend on it byte for byte.
