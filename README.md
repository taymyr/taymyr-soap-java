[![Gitter](https://img.shields.io/badge/chat-gitter-purple.svg)](https://gitter.im/taymyr/taymyr)
[![Gitter_RU](https://img.shields.io/badge/chat-russian%20channel-purple.svg)](https://gitter.im/taymyr/taymyr_ru)
[![Build Status](https://travis-ci.org/taymyr/lagom-soap-client.svg?branch=master)](https://travis-ci.org/taymyr/lagom-soap-client)
[![codecov](https://codecov.io/gh/taymyr/lagom-soap-client/branch/master/graph/badge.svg)](https://codecov.io/gh/taymyr/lagom-soap-client)
[![Javadocs](https://www.javadoc.io/badge/org.taymyr.lagom/lagom-soap-client-java_2.12.svg)](https://www.javadoc.io/doc/org.taymyr.lagom/lagom-soap-client-java_2.12)
[![Maven Central](https://img.shields.io/maven-central/v/org.taymyr.lagom/lagom-soap-client-java_2.12.svg)](https://search.maven.org/search?q=a:lagom-soap-client-java_2.12%20AND%20g:org.taymyr.lagom)

# Lagom SOAP client

Lagom SOAP allows a Lagom application to make calls on a remote web service using SOAP. 
It provides a reactive interface to doing so, making HTTP requests asynchronously and returning futures of the result.
Lagom SOAP use [Play SAOP](https://github.com/playframework/play-soap) library and 
[Circuit Breaker](https://www.lagomframework.com/documentation/current/scala/ServiceClients.html#Circuit-Breakers) feature of Lagom.

## Versions compatibility

| Lagom Soap Client | Lagom                      | Scala                    |
|-------------------|----------------------------|--------------------------|
| 1.+               | 1.4.+ <br> 1.5.+ <br> 1.6.+| 2.11 <br> 2.12 <br> 2.13 |

## How to use

### 1. Generate async client for external SOAP service

* Use [Play SAOP](https://github.com/playframework/play-soap) for generate async client.
* Add generated client to dependencies of Lagom service. For example:
```scala
val externalSoapClient = "foo.bar" %% "external-async-client" % "X.Y.Z"
...
.settings(
  libraryDependencies ++= Seq(
    externalSoapClient
  )
)

```

### 2. Add the dependency

See [Adding the dependency](#adding-the-dependency)

### 3. Register async client

Extend your Guice module by [ServiceGuiceSupport](java/src/main/kotlin/org/taymyr/lagom/soap/ServiceGuiceSupport.kt) 
and use `bindSoapClient` method for registration client. Also you can register SOAP message handler for this service.
For example:

```java
import org.taymyr.lagom.soap.ServiceGuiceSupport;
    
public class MyServiceModule extends AbstractModule implements ServiceGuiceSupport {
    @Override
    protected void configure() {
        bindSoapClient(Service.class, ServicePort.class, new DisableJaxbValidationHandler());
    }
}

```

### 4. Inject SOAP client

#### 4.1 Inject SOAP client directly

```java
public class MyServiceImpl implements MyService {

    private final ServicePort soapService;

    @Inject
    public MyServiceImpl(ServicePort soapService) {
        this.soapService = soapService;
    }
}

```

#### 4.2 Inject provider of SOAP client

You can inject provider of SOAP client for using custom handling every SOAP message.

```java
public class MyServiceImpl implements MyService {

    private final ServiceProvider<ServicePort> serviceProvider;

    @Inject
    public MyServiceImpl(ServiceProvider<ServicePort> serviceProvider) {
        this.serviceProvider = serviceProvider;
    }
}

```

### 5. Invoke method of SOAP client

You can get SOAP client from the provider, passing it a list message handlers, that will be used in addition to the list passed during registration.
Within these handlers, you can implement the query logic that depends on the current context. For example, put the header `User-Agent` in the outgoing SOAP request
depending on the headers of the current request.

```java
import static org.taymyr.lagom.soap.WebFaultException.processWebFault;
import static org.taymyr.lagom.soap.handler.SetUserAgentHandler.userAgent;

public class MyServiceImpl implements MyService {

    @Override
    public HeaderServiceCall<NotUsed, String> myMethod() {
        return (headers, request) -> {
            ServicePort service = serviceProvider.get(userAgent("Agent"));
            return service.foo(new Bar())
                .thenApplyAsync(result -> ok("Foo: " + result))
                .exceptionally(throwable -> processWebFault(throwable, e -> {
                    if (e instanceof ServiceLogicException) {
                        // do something
                    } else {
                        throw new TransportException(InternalServerError, new ExceptionMessage("", ""));
                    }
                }));
        };
    }
}
```

### 6. Configuration

* Add `org.taymyr.lagom.soap.WebFaultException` to Circuit Breakers whitelist (`lagom.circuit-breaker.default.exception-whitelist`), 
  because all checked SOAP exceptions boxing to `WebFaultException`. Otherwise Circuit Breaker will be opened for all SOAP exceptions.

* Configure Circuit Breaker for SOAP client `lagom.circuit-breaker.<SERVICE_CLASS>`.
  To configure methods use `lagom.circuit-breaker.<SERVICE_CLASS>.methods.<METHOD_NAME>`
  Highly recommended configuring Circuit Breaker (see all available settings in [Lagom docs](https://www.lagomframework.com/documentation/current/scala/ServiceClients.html#Circuit-Breaker-Configuration)) 
  in `play.soap.services` block and use reference to this configuration in `lagom.circuit-breaker` block.
   
* Configure address external SOAP service `play.soap.services.<SERVICE_CLASS>.address`. See details in [Play SOAP docs](https://playframework.github.io/play-soap/PlaySoapClient.html).

* Configure value of _User-Agent_ header `play.soap.services.<SERVICE_CLASS>.browser-type`. (Default value `lagom`).

* Configure SOAP client `play.soap.services.<SERVICE_CLASS>.singleton` as Singleton for better performance. *Warning*: It can be **NOT** thread-safe. More details see on [CXF docs](http://cxf.apache.org/faq.html#FAQ-AreJAX-WSclientproxiesthreadsafe%3F) (Default value `false`)

* Configure max log size of you soap-client `play.soap.services.<SERVICE_CLASS>.log-size` (Default value 48 kB). When you use log-size more than 128 kbytes CXF creates temporary files that contains messages, and CXF deletes files by itself.

* Configure logging shadowing properties with `play.soap.services.<SERVICE_CLASS>.fields-shadowing`. This settings are used when `play.soap.services.<SERVICE_CLASS>.debugLog=true` (Logging interceptors are enabled). Custom LoggingInterceptors embed necessary string in a soap request and response when it finds a message with a desired soapAction message. 

```HOCON
lagom.circuit-breaker {
  default.exception-whitelist = [
    org.taymyr.lagom.soap.WebFaultException
  ]

  com.foo.bar.service.Service = ${play.soap.services.com.foo.bar.service.Service.breaker}
  com.foo.bar.service.Service.methods.method1 = ${play.soap.services.com.foo.bar.service.Service.methods.method1.breaker}
  com.foo.bar.service.Service.methods.method2 = ${play.soap.services.com.foo.bar.service.Service.methods.method2.breaker}
}


play.soap.services {
  com.foo.bar.service.Service {
    address = "http://domain:PORT/service"
    browser-type = "Lagom MyService"
    singleton: false
    log-size : 1MB
    fields-shadowing : [
      {
        soap-method = "\"soapAction\""
        replacing-symbols = "********"
        request-patterns = [
          "//books/title",
        ]
        response-patterns = [
          "//books/book/date",
          "//books/book/author/name"
        ]
      }
    ]
    breaker = {
      call-timeout = 10s
    }
    methods {
      method1 {
        breaker {
          call-timeout = 20s
        }
      }
      method2 {
        breaker {
          call-timeout = 30s
        }
      }
    }
  }
}
```

## Adding the dependency

All **released** artifacts are available in the [Maven central repository](https://search.maven.org/search?q=a:lagom-soap-client-java_2.12%20AND%20g:org.taymyr.lagom).
Just add a `lagom-soap-client` to your service dependencies:

* **SBT**

```scala
libraryDependencies += "org.taymyr.lagom" %% "lagom-soap-client-java" % "X.Y.Z"
```

* **Maven**

```xml
<dependency>
  <groupId>org.taymyr.lagom</groupId>
  <artifactId>lagom-soap-client-java_${scala.binary.version}</artifactId>
  <version>X.Y.Z</version>
</dependency>
```

All **snapshot** artifacts are available in the [Sonatype snapshots repository](https://oss.sonatype.org/content/repositories/snapshots/org/taymyr/lagom).
This repository must be added in your build system. 

* **SBT**

```scala
resolvers ++= Resolver.sonatypeRepo("snapshots")
```

* **Maven**
```xml
<repositories>
  <repository>
    <id>snapshots-repo</id>
    <url>https://oss.sonatype.org/content/repositories/snapshots</url>
    <releases><enabled>false</enabled></releases>
    <snapshots><enabled>true</enabled></snapshots>
  </repository>
</repositories>
``` 

## Contributions

Contributions are very welcome.

## License

Copyright © 2019 Digital Economy League (https://www.digitalleague.ru/).

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
