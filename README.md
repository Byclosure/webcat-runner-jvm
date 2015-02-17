This library provides a runner for your 
features (see [Cucumber wiki](https://github.com/cucumber/cucumber/wiki/Feature-Introduction) to know more about features)
and a reporter that sends run reports to the [Webcat](http://www.webcat.byclosure.com/) server.

This library is part of the [Webcat Project](http://www.webcat.byclosure.com/).

#Installation#
To install this library, add the following to your `pom.xml`.

```
<dependency>
    <groupId>com.byclosure.webcat</groupId>
    <artifactId>webcat-runner-jvm</artifactId>
    <version>1.0.0</version>
</dependency>
```

#Usage#
The Webcat runner is based on the Cucumber-JVM Junit runner. Refer to the
[Cucumber-JVM project](https://github.com/cucumber/cucumber-jvm) on how to write and run Features using this library.

To use the Webcat runner, do the following:

1. Create a Junit Runner

    ```java
    @RunWith(WebcatRunner.class)
    @CucumberOptions(format = {"pretty", "junit:target/junit-report.xml"},
        monochrome = true)
    public class FeatureRunnerTest {
    }
    ```
2. Write your features and step implementations
3. Run the Features with
```
mvn test
```


#Contributing#
We are happy to accept contributions.
To contribute to this library, do the following:

1. Fork the repository
2. Create a new branch
3. Write your code in the new branch (tests included)
4. Submit a pull request

#Copyright#
Copyright (c) 2015 Byclosure. See LICENSE for details.
