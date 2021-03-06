# Log4Shell RCE

### *Vulnerability Analysis Report - Follow Up(s)*

## Introduction

There is a critical 0-day vulnerability reported in Log4j versions ```2.0 <= Log4j <= 2.14.1```. It is tracked as ```CVE-2021-44228```. See my detailed analysis report on that CVE at [Log4Shell RCE - CVE-2021-44228 analysis report](https://github.com/sschakraborty/SecurityPOC/tree/main/log4shell).

Understanding the above CVE is a pre-requisite to understanding these follow-up Denial-of-Service (DoS) vulnerabilities that were reported after it. These vulnerabilities are tracked as ```CVE-2021-45046``` and ```CVE-2021-45105```.


## Affected Versions

The vulnerability is related to a Log4j 2 feature called *Lookups*. *Lookups* are available only in **Log4j 2** (version 2.x) and not in **Log4j** (version 1.x). For more information on *Lookups* visit [Log4j 2 Lookups](https://logging.apache.org/log4j/2.x/manual/lookups.html).

```CVE-2021-45046``` was patched in Log4j version ```2.16.0``` by removing the JNDI lookup feature entirely. ```CVE-2021-45105``` was patched in Log4j version ```2.17.0``` by disabling recursive lookup evaluation. Starting from version ```2.17.0``` only the top-level pattern of a recursive lookup meta-variable will be evaluated.

For example:

In ```2.16.0```, recursive evaluation of ```${java:${env:${env:FOO}}}``` will be as follows:
```text
export $FOO = "INSTRUCTION"
export $INSTRUCTION = "version"
${java:${env:${env:FOO}}} to ${java:${env:INSTRUCTION}} to ${java:version} to JDK 11.0.12
```

However, in version ```2.17.0```, the same pattern will be evaluated as follows:
```text
export $FOO = "INSTRUCTION"
export $INSTRUCTION = "version"
${java:${env:${env:FOO}}} to ${java:${env:INSTRUCTION}}
```

It's recommended for Log4j core dependencies to be upgraded to versions ```2.17.0``` or above.

## ```CVE-2021-45046```

1. [MITRE Report](https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2021-45046)
2. [National Vulnerability Database](https://nvd.nist.gov/vuln/detail/CVE-2021-45046)
3. [SSA-661247: Apache Log4j Vulnerabilities - Impact to Siemens Products](https://cert-portal.siemens.com/productcert/pdf/ssa-661247.pdf)
4. [Apache's JIRA issue](https://issues.apache.org/jira/browse/LOG4J2-3221)

## ```CVE-2021-45105```

1. [MITRE Report](https://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2021-45105)
2. [National Vulnerability Database](https://nvd.nist.gov/vuln/detail/CVE-2021-45105)
4. [Apache's JIRA issue](https://issues.apache.org/jira/browse/LOG4J2-3230)


# The Vulnerability

## Facts

The previous vulnerability ```CVE-2021-44228``` was fixed in version ```2.15.0``` by making best-efforts in restricting the JNDI lookups to localhost. In version ```2.15.0```, JNDI lookups to any remote service will fail with the message ```WARN Attempt to access ldap server not in allowed list```.

An additional point to note here is that the flag ```-Dlog4j2.formatMsgNoLookups=true``` or environment variable ```LOG4J_FORMAT_MSG_NO_LOOKUPS``` used for disabling lookups in message patterns, works only for messages sent to logger. This flag does not apply to other pattern layout meta-variables present in configuration.

For example, the Thread Context lookup meta-variable (```$${ctx:...}```) in pattern ```%d %p %C{1.} [%t] Hostname: $${ctx:hostName} Message: %m %n``` won't be affected by the above flag. Those values will still be evaluated and dumped into logs.

For complete background of the above discussions, see [Log4Shell RCE - CVE-2021-44228 analysis report](https://github.com/sschakraborty/SecurityPOC/tree/main/log4shell).


## ```CVE-2021-45046``` - JNDI Injection (DoS)

Consider the following Java code:

```java
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

public class MainVerticle extends AbstractVerticle {
    private static final Logger logger = LogManager.getLogger(MainVerticle.class);

    @Override
    public void start(Promise<Void> startPromise) {
        vertx.createHttpServer().requestHandler(request -> {

            // Set a ThreadContext variable "hostName"
            ThreadContext.put("hostName", request.host());

            // Invoke logger
            logger.info("Received a connection!");

            request.response().putHeader("content-type", "text/plain").end("Hello World!");
        }).listen(8888, http -> {
            if (http.succeeded()) {
                logger.info("HTTP server started on port 8888");
                startPromise.complete();
            } else {
                logger.error("HTTP server started on port 8888");
                startPromise.fail(http.cause());
            }
        });
    }
}
```

This code extracts the hostname from the HTTP request and sets it into the current thread's Log4j ```ThreadContext``` with key ```hostName```. Combined with the following pattern in any Log4j appender, the mitigations made in Log4j ```2.15.0``` are rendered useless.

```properties
appender.rolling.layout.pattern=%d %p %C{1.} [%t] Hostname: $${ctx:hostName} Message: %m %n
```
Even if the message lookups are disabled using the flag mentioned above, an attacker can inject meta-variables in ```Host: ...``` field of HTTP request and Log4j will evaluate them.

For example:

```text
GET /helloWorld HTTP/1.1
Host: ${env:SESSION_MANAGER}
Origin: https://pi.local:8888
```

will result in the following log being dumped:

```text
Hostname: local/unix:@/tmp/.ICE-unix/1804,unix/unix:/tmp/.ICE-unix/1804 Message: Received a connection!
```

irrespective of whether message lookups are disabled or not.

Therefore, JNDI payloads can be injected as well. However, due to the previously mentioned fact that Log4j ```2.15.0``` makes best-effort attempt to stop remote JNDI calls, an attacker (probably!) won't be able to make calls to remote server. All JNDI calls will be restricted to localhost.

But what happens if the JNDI call is made to the vulnerable web server itself?

For example, the above web server listens to port ```8888``` and the following HTTP request
```text
GET /helloWorld HTTP/1.1
Host: ${jndi:ldap://localhost:8888/foo/bar}
Origin: https://pi.local:8888
```
is sent to the server. This will trigger a JNDI lookup to the web server itself and there's nothing restricting the request as it is made to a local address (and not a remote one)! This will lead to arresting the thread that tries to process this HTTP request. Sending multiple such requests to the web server will eventually lead to arresting all threads in the thread-pool and consequently Denial-of-Service.

### The Arrest

Since the JNDI request, received by the web server, is not a complete and compliant HTTP request, the server never returns a response. And the JNDI lookup client that Log4j uses is a synchronous / blocking client. The client keeps waiting indefinitely for a response and never receives one. Therefore, the thread never exits.

### Exploit PoC

Follow the steps to get a PoC setup working. First step is to run a vulnerable web server:

1. Clone this repo ```git clone https://github.com/sschakraborty/SecurityPOC.git```
2. Change current directory into *log4shell_2* - ```cd log4shell_2```
3. Build using Maven - ```mvn clean package -D skipTests```
4. Run the vulnerable web server fat JAR - ```java -Dlog4j2.formatMsgNoLookups=true -jar target/log4shell_2-1.0.0-SNAPSHOT-fat.jar```
5. This web server will be listening on port ```8888``` and will be appending logs into two appenders. One appender is the console appender, which is not vulnerable as Thread Context lookup is not present in default pattern configuration. The other one is the rolling file appender which is vulnerable as ```%d %p %C{1.} [%t] Hostname: $${ctx:hostName} Message: %m %n``` is set as the pattern configuration and hostname is under attacker's control.

The rolling file appender logs into ```target/rolling/rollingtest.log```.

The next step is to simply execute the Python 3 exploit script as ```python3 exploit/exploit.py```. You should see that the server's thread is blocked forever and does not exit (The server will be logging warnings for being blocked. This is a feature in Vert.x framework). The Python 3 client will exit after some time because of timeout but the server thread will continue to be blocked.

**Note:** The blocked server cannot be terminated using ```Ctrl + C``` signal. Try ```sudo kill -9 <PID>```.


## ```CVE-2021-45105``` - Infinite Recursion

It was reported in this [Apache's JIRA ticket](https://issues.apache.org/jira/browse/LOG4J2-3230) that Log4j's feature of resolving lookup meta-variable patterns recursively, might end up in infinite recursions blocking the thread and eventually throwing ```StackOverflowException```. One such payload that triggers infinite recursion is ```${${::-${::-$${::-j}}}}```.

However, during my testing of the sample code attached in the ticket, JVM did not exit by throwing a ```StackOverflowException```. It rather dumped a log which looks like the following and continued its operations normally.

```text
...
Constructor
2021-12-18 16:32:32,353 main ERROR An exception occurred processing Appender Console java.lang.IllegalStateException: Infinite loop in property interpolation of ::-${::-$${::-j}}: :
	at org.apache.logging.log4j.core.lookup.StrSubstitutor.checkCyclicSubstitution(StrSubstitutor.java:1025)
	at org.apache.logging.log4j.core.lookup.StrSubstitutor.substitute(StrSubstitutor.java:972)
	at org.apache.logging.log4j.core.lookup.StrSubstitutor.substitute(StrSubstitutor.java:985)
	at org.apache.logging.log4j.core.lookup.StrSubstitutor.substitute(StrSubstitutor.java:872)
	at org.apache.logging.log4j.core.lookup.StrSubstitutor.substitute(StrSubstitutor.java:939)
	at org.apache.logging.log4j.core.lookup.StrSubstitutor.substitute(StrSubstitutor.java:872)
	...
	at org.apache.logging.log4j.spi.AbstractLogger.info(AbstractLogger.java:1297)
	at net.jondotcomdotorg.TestDOS.doAThing(TestDOS.java:20)
	at net.jondotcomdotorg.TestDOS.main(TestDOS.java:26)

Saw output 357
2021-12-18 16:32:32,361 pool-1-thread-1 DEBUG Stopping LoggerContext[name=3d4eac69, org.apache.logging.log4j.core.LoggerContext@3b0fe47a]
2021-12-18 16:32:32,362 pool-1-thread-1 DEBUG Stopping LoggerContext[name=3d4eac69, org.apache.logging.log4j.core.LoggerContext@3b0fe47a]
...
```

The file ```CVE_2021_45105.tar.gz``` is the original example from the JIRA ticket. I tested the code on:
```text
openjdk version "11.0.13" 2021-10-19
OpenJDK Runtime Environment 18.9 (build 11.0.13+8)
OpenJDK 64-Bit Server VM 18.9 (build 11.0.13+8, mixed mode, sharing)
```

As of 18th Dec, 2021, the impact of this CVE, for most Java applications running on modern JVM, is limited to polluting logs by dumping a stack trace. The application might still continue working fine. Investigation on the matter is ongoing. However, the Log4j version ```2.17.0``` addresses this issue effectively.


# Remediation

Upgrade Log4j to a version ```2.17.0``` or above.

The DoS vulnerability is solved in the Log4j version ```2.16.0``` by removing the JNDI lookup feature entirely. The call to resolve / evaluate the JNDI lookup meta-variables, has been removed. Therefore, Log4j version ```2.16.0``` or above will not resolve anything which resembles or which recursively resolves to resemble ```${jndi:...}``` or ```$${jndi:...}```.

The infinite recursion issue has been solved in Log4j version ```2.17.0``` by stopping recursion at the very top level and not expanding further after the first level of evaluation. Therefore, all recursive variables will only be evaluated once.