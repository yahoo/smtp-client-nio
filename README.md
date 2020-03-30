# SMTP NIO

A Java library that supports Non-blocking I/O (NIO) Simple Mail Transfer Protocol (SMTP) operations.

The SMTP NIO client provides a framework to interact with SMTP servers and send messages asynchronously using a non-blocking I/O mechanism. This grants great efficiency, flexibility and scalability for clients especially intended to handle large amounts of traffic.


## Table of Contents

- [Background](#background)
- [Install](#install)
- [Usage](#usage)
- [Release](#release)
- [Contribute](#contribute)
- [License](#license)


## Background

Java's current built-in API, [JavaMail](https://www.oracle.com/technetwork/java/javamail/index.html) is limited to blocking operations on server interactions. This can especially be inconvenient as threads block while waiting for requests to finish. This framework is designed to use asynchronous, non-blocking operations to perform transactions so threads do not need to wait upon making a request. This design improves thread utilization and provides an overall performance improvement.


Some of the more distinguishing features of this library are:
- Highly customizable thread model and server/client idle max limit
- Leverages the well-established framework [Netty](https://netty.io/)
- Future-based design enables a clean separation of the SMTP client threads pool and the consumers threads pool
- Simple Mail Transfer Protocol (SMTP) support [RFC 5321](https://tools.ietf.org/html/rfc5321)
- **EHLO** command support [RFC 1869 (Section 4)](https://tools.ietf.org/html/rfc1869#section-4)
- **QUIT** command support [RFC 5321 (Section 4.1.1.10)](https://tools.ietf.org/html/rfc5321#section-4.1.1.10)


This project is ideal for applications that have a high requirement to optimize thread utilization and improve overall resource capacity. Specifically, this is best for situations where users need to perform extensive communications the SMTP servers.
 
## Install

This library is built and managed using [maven](https://maven.apache.org/what-is-maven.html). Update your project's [pom.xml](https://maven.apache.org/guides/introduction/introduction-to-the-pom.html) file to include the follow dependency:
```xml
<dependency>
  <groupId>com.yahoo.smtpnio</groupId>
  <artifactId>smtpnio.core</artifactId>
  <version>1.1.0</version>
</dependency>
```

Install the framework via:
```shell script
$ mvn clean install
```

For contributors run deploy to do a push to nexus servers:

```shell script
$ mvn clean deploy -Dgpg.passphrase=[passPhrase]
```

## Usage

The following code snippets demonstrate the basic functionality of creating a session with a SMTP server, issuing a command, and retrieving the response.

### Create a client with a desired number of threads
```java
final int numOfThreads = 4;
final SmtpAsyncClient smtpClient = new SmtpAsyncClient(numOfThreads);
```
### Establish a SMTP server connection and create a session
```java

// Use SSL?
final boolean enableSsl = true;

// This example defines a connection to smtp.example.com through port 465 over SSL
final SmtpAsyncSessionData sessionData = SmtpAsyncSessionData.newBuilder("smtp.example.com", 465, enableSsl)
        .setSessionContext("client007") // optional parameter used as an ID for debugging purposes
        .setSniNames(null) // optional list of SNI names
        .setLocalAddress(null); // optional local address if present
        .build();

// Session configurations such as timeouts
final SmtpAsyncSessionConfig sessionConfig = new SmtpAsyncSessionConfig()
        .setConnectionTimeout(5000)
        .setReadTimeout(7000);

// Asynchronous call to create a Future containing the session data
final Future<SmtpAsyncCreateSessionResponse> sessionFuture = smtpClient.createSession(
    sessionData, sessionConfig, SmtpAsyncSession.DebugMode.DEBUG_OFF
);

// Check if future is done using the isDone method (non-blocking)
if (sessionFuture.isDone()) {
    System.out.println("Future is ready, can access data without blocking");
} else {
    System.out.println("Future is not done yet");
}

// Gets the session data from the future. This operation will block only if future is not yet complete
final SmtpAsyncCreateSessionResponse sessionResponse = sessionFuture.get();

// The actual session that can be used to issue commands
final SmtpAsyncSession session = sessionResponse.getSession();

```

### Execute the SMTP command
The follow code snippet demonstrates how to execute an **EHLO** command.

```java
// Issue an "EHLO" command asynchronously
final Future<SmtpAsyncResponse> ehloResponseFuture = session.execute(
    new ExtendedHelloCommand("my_domain")
);
```

### Handle the response from the server
Obtain the response from the future, block if necessary. The following example shows how to read the responses from the result.

```java
try {
    // Get the response, this may block for at most 5 seconds if not completed already
    final SmtpAsyncResponse ehloResponse = ehloResponseFuture.get(5, TimeUnit.SECONDS);
    final Collection<SmtpResponse> responsesLines = ehloResponse.getResponseLines();

    // Access responses
    for (final SmtpResponse response : responsesLines) {
        System.out.println("Raw response: " + response.toString());
        System.out.println("Reply code: " + response.getCode());
        System.out.println("Message: " + response.getMessage());
    }
} catch (TimeoutException e) {
    System.err.println("future did not finish in time");
}
```

### Cleanup

Close the session and client when operations are done.
```java
session.close();
smtpClient.shutdown();
```

## Release

This release, version 1.1.0, is the second official release. The current supported SMTP commands are:
- **EHLO**
- **HELO**
- **DATA**
- **EXPN**
- **HELO**
- **MAIL**
- **HELP**
- **NOOP**
- **VRFY**
- **RSET**
- **RCPT**
- **AUTH**
- **QUIT**


## Contribute

We welcome questions, issues, and pull requests. Please refer to [contributing.md](Contributing.md) for information about how to get involved. 

## License

This project is licensed under the terms of the [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0) open source license. Please refer to [LICENSE](LICENSE) for details.
