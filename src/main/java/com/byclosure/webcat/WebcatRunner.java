package com.byclosure.webcat;

import com.byclosure.webcat.helpers.LoggerHelper;
import com.byclosure.webcat.reporter.StepDefinitionCollector;
import com.byclosure.webcat.reporter.WebcatReporter;
import cucumber.api.CucumberOptions;
import cucumber.runtime.*;
import cucumber.runtime.Runtime;
import cucumber.runtime.io.*;
import cucumber.runtime.junit.Assertions;
import cucumber.runtime.junit.FeatureRunner;
import cucumber.runtime.junit.JUnitReporter;
import cucumber.runtime.model.CucumberFeature;
import gherkin.formatter.Formatter;
import gherkin.formatter.Reporter;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.ParentRunner;
import org.junit.runners.model.InitializationError;

import java.io.*;
import java.util.*;

/**
 * <p>
 * Classes annotated with {@code @RunWith(Cucumber.class)} will run a Cucumber Feature.
 * The class should be empty without any fields or methods.
 * </p>
 * <p>
 * Cucumber will look for a {@code .feature} file on the classpath, using the same resource
 * path as the annotated class ({@code .class} substituted by {@code .feature}).
 * </p>
 * Additional hints can be given to Cucumber by annotating the class with {@link CucumberOptions}.
 *
 * @see CucumberOptions
 */
public class WebcatRunner extends ParentRunner<FeatureRunner> {
    private final JUnitReporter jUnitReporter;
    private final List<FeatureRunner> children = new ArrayList<FeatureRunner>();
    private final Runtime runtime;

    /**
     * Constructor called by JUnit.
     *
     * @param clazz the class with the @RunWith annotation.
     * @throws java.io.IOException                         if there is a problem
     * @throws org.junit.runners.model.InitializationError if there is another problem
     */
    public WebcatRunner(Class clazz) throws InitializationError, IOException {
        super(clazz);

        final EnvironmentConfig envConfiguration = new EnvironmentConfig();

        if(envConfiguration.isDebug()) {
            LoggerHelper.setDebugOn();
        }

        ClassLoader classLoader = clazz.getClassLoader();
        Assertions.assertNoCucumberAnnotatedMethods(clazz);

        WebcatRuntimeOptionsFactory runtimeOptionsFactory = new WebcatRuntimeOptionsFactory(clazz);
        final RuntimeOptions runtimeOptions;

        if(envConfiguration.shouldPublishResults()) {
            runtimeOptions = runtimeOptionsFactory.create(envConfiguration.getIntent());
        } else {
            runtimeOptions = runtimeOptionsFactory.create();
        }

        ResourceLoader resourceLoader = new MultiLoader(classLoader);
        runtime = createRuntime(resourceLoader, classLoader, runtimeOptions);
        Glue glue = runtime.getGlue();

        Formatter formatter = runtimeOptions.formatter(classLoader);
        Reporter reporter = runtimeOptions.reporter(classLoader);
        
        
        final StepDefinitionCollector stepDefinitionCollector = new StepDefinitionCollector();
        
        glue.reportStepDefinitions(stepDefinitionCollector);
        
        final Appendable outWebcatReporter;

        if(envConfiguration.isDebug()) {
            outWebcatReporter = new UTF8OutputStreamWriter(new URLOutputStream(Utils.toURL("target/webcat-report.json")));
        } else {
            outWebcatReporter = null;
        }
        final WebcatReporter webcatReporter = new WebcatReporter(outWebcatReporter, envConfiguration, stepDefinitionCollector.getStepDefinitions());

        runtimeOptions.addPlugin(webcatReporter);

        final List<CucumberFeature> cucumberFeatures = runtimeOptions.cucumberFeatures(resourceLoader);
        jUnitReporter = new JUnitReporter(reporter, formatter, runtimeOptions.isStrict());
        addChildren(cucumberFeatures);
    }

    /**
     * Create the Runtime. Can be overridden to customize the runtime or backend.
     *
     * @param resourceLoader used to load resources
     * @param classLoader    used to load classes
     * @param runtimeOptions configuration
     * @return a new runtime
     * @throws InitializationError if a JUnit error occurred
     * @throws IOException if a class or resource could not be loaded
     */
    protected Runtime createRuntime(ResourceLoader resourceLoader, ClassLoader classLoader,
                                    RuntimeOptions runtimeOptions) throws InitializationError, IOException {
        ClassFinder classFinder = new ResourceLoaderClassFinder(resourceLoader, classLoader);
        return new Runtime(resourceLoader, classFinder, classLoader, runtimeOptions);
    }

    @Override
    public List<FeatureRunner> getChildren() {
        return children;
    }

    @Override
    protected Description describeChild(FeatureRunner child) {
        return child.getDescription();
    }

    @Override
    protected void runChild(FeatureRunner child, RunNotifier notifier) {
        child.run(notifier);
    }

    @Override
    public void run(RunNotifier notifier) {
        super.run(notifier);
        jUnitReporter.done();
        jUnitReporter.close();
        runtime.printSummary();
    }

    private void addChildren(List<CucumberFeature> cucumberFeatures) throws InitializationError {
        for (CucumberFeature cucumberFeature : cucumberFeatures) {
            children.add(new FeatureRunner(cucumberFeature, runtime, jUnitReporter));
        }
    }
}
