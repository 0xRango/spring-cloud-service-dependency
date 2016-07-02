# spring-cloud-service-dependency

[![Build Status](https://travis-ci.org/chenyuejie/spring-cloud-service-dependency.png)](https://travis-ci.org/chenyuejie/spring-cloud-service-dependency)

Empower spring cloud project with simple service dependency management capability.

### Features
- Define the required services for spring boot application, prevent calling other services not in the required services list.
- Check required services are available on startup, terminate the application if there are unavailable serivce(s)(Optional)
- Check [dependency cycle](https://en.wikipedia.org/wiki/Circular_dependency) is exist on startup, terminate the application if it is detected(Optional)

### Configuration

pom.xml

    ...
    <repository>
      <id>ossrh</id>
      <name>Sonatype OSSRH</name>
      <url>https://oss.sonatype.org/content/repositories/snapshots</url>
      <snapshots>
        <enabled>true</enabled>
      </snapshots>
    </repository>
    ...
    <dependency>
      <groupId>com.github.chenyuejie</groupId>
      <artifactId>spring-cloud-service-dependency</artifactId>
      <version>1.0.0-SNAPSHOT</version>
    </dependency>
    ...

application.yml

    spring:
      application:
        name: service-b
    service-dependency:
      terminateOnRequiresNotFound: false
      terminateOnDependencyCycleFound: false
      requires:
        - service-c
        - service-d

Application.java

    package com.example;

    import org.springframework.boot.SpringApplication;
    import org.springframework.boot.autoconfigure.SpringBootApplication;
    import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

    import io.spring.cloud.service.dependency.EnableServiceDependency;

    @SpringBootApplication
    @EnableDiscoveryClient
    @EnableServiceDependency
    public class ServiceB {


    	public static void main(String[] args) {
    		SpringApplication.run(ServiceB.class, args);
	    }

    }


### Example
print dependency-tree of service-a

    cyj@mba ~ $ curl http://192.168.2.104:49162/service-dependency/dependency-tree
    +- service-a
      \- service-b
        +- service-c
          \- service-d
            \- service-e
              \- service-c **ERROR** Dependency Cycle detected
        \- service-d
          \- service-e
            \- service-c
              \- service-d **ERROR** Dependency Cycle detected
