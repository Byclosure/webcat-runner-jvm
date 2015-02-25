package com.byclosure.webcat.reporter;

import com.byclosure.webcat.EnvironmentConfig;
import com.byclosure.webcat.context.Context;
import com.byclosure.webcat.helpers.LoggerHelper;
import cucumber.runtime.ParameterInfo;
import cucumber.runtime.StepDefinition;
import cucumber.runtime.StepDefinitionMatch;
import gherkin.deps.com.google.gson.Gson;
import gherkin.deps.com.google.gson.GsonBuilder;
import gherkin.deps.net.iharder.Base64;
import gherkin.formatter.Formatter;
import gherkin.formatter.NiceAppendable;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.*;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebcatReporter implements Reporter, Formatter {
    private final static Logger logger = LoggerHelper.getLogger(WebcatReporter.class.getName());

    private final List<Map<String, Object>> featureMaps = new ArrayList<Map<String, Object>>();
    private final NiceAppendable out;
    private final EnvironmentConfig config;
    private final List<StepDefinition> stepDefinitions;
    private final ArrayList<StepDefinitionMatch> stepMatches;

    private Map<String, Object> featureMap;
    private String uri;
    private List<Map> beforeHooks = new ArrayList<Map>();

    private enum Phase {step, match, embedding, output, result};

    private Map getCurrentStep(Phase phase) {
        String target = phase.ordinal() <= Phase.match.ordinal()?Phase.match.name():Phase.result.name();
        Map lastWithValue = null;
        for (Map stepOrHook : getSteps()) {
            if (stepOrHook.get(target) == null) {
                return stepOrHook;
            } else {
                lastWithValue = stepOrHook;
            }
        }
        return lastWithValue;
    }


    public WebcatReporter(Appendable out, EnvironmentConfig envConfiguration, List<StepDefinition> stepDefinitions) {
        this.stepDefinitions = stepDefinitions;
        this.config = envConfiguration;

        this.stepMatches =  new ArrayList<StepDefinitionMatch>();

        this.out = new NiceAppendable(out);
    }

    @Override
    public void uri(String uri) {
        this.uri = uri;
    }

    @Override
    public void feature(Feature feature) {
        featureMap = feature.toMap();
        featureMap.put("uri", uri);
        featureMaps.add(featureMap);
    }

    @Override
    public void background(Background background) {
        getFeatureElements().add(background.toMap());
    }

    @Override
    public void scenario(Scenario scenario) {
        getFeatureElements().add(scenario.toMap());
        if (beforeHooks.size() > 0) {
            getFeatureElement().put("before", beforeHooks);
            beforeHooks = new ArrayList<Map>();
        }
    }

    @Override
    public void scenarioOutline(ScenarioOutline scenarioOutline) {
        getFeatureElements().add(scenarioOutline.toMap());
    }

    @Override
    public void examples(Examples examples) {
        getAllExamples().add(examples.toMap());
    }

    @Override
    public void step(Step step) {
        getSteps().add(step.toMap());
    }

    @Override
    public void match(Match match) {
        getCurrentStep(Phase.match).put("match", match.toMap());

        if (match instanceof StepDefinitionMatch) {
            final StepDefinitionMatch stepMatch = (StepDefinitionMatch)match;
            stepMatches.add(stepMatch);
        }
    }

    @Override
    public void embedding(String mimeType, byte[] data) {
        final Map<String, String> embedding = new HashMap<String, String>();
        embedding.put("mime_type", mimeType);
        embedding.put("data", Base64.encodeBytes(data));
        getEmbeddings().add(embedding);
    }

    @Override
    public void write(String text) {
        getOutput().add(text);
    }

    @Override
    public void result(Result result) {
        final Map currentStep = getCurrentStep(Phase.result);
        currentStep.put("result", result.toMap());

        final Context context = Context.getInstance();
        currentStep.put("screenshots", new ArrayList<String>(context.getScreenshots()));
        context.clearScreenshots();
    }

    @Override
    public void before(Match match, Result result) {
        beforeHooks.add(buildHookMap(match,result));
    }

    @Override
    public void after(Match match, Result result) {
        List<Map> hooks = getFeatureElement().get("after");
        if (hooks == null) {
            hooks = new ArrayList<Map>();
            getFeatureElement().put("after", hooks);
        }
        hooks.add(buildHookMap(match,result));
    }

    private Map buildHookMap(final Match match, final Result result) {
        final Map hookMap = new HashMap();
        hookMap.put("match", match.toMap());
        hookMap.put("result", result.toMap());
        return hookMap;
    }

    public void appendDuration(final int timestamp) {
        final Map result = (Map) getCurrentStep(Phase.result).get("result");
        // check to make sure result exists (scenario outlines do not have results yet)
        if (result != null) {
            //convert to nanoseconds
            final long nanos = timestamp * 1000000000L;
            result.put("duration", nanos);
        }
    }

    @Override
    public void eof() {
    }

    @Override
    public void done() {
        final List<StepDefinitionContainer> stepDefinitionContainers = new ArrayList<StepDefinitionContainer>();
        for (StepDefinition stepDefinition : stepDefinitions) {
            final StepDefinitionContainer stepDefinitionContainer = new StepDefinitionContainer(stepDefinition);
            stepDefinitionContainers.add(stepDefinitionContainer);
        }

        final List<StepDefinitionMatchContainer> stepMatchContainers = new ArrayList<StepDefinitionMatchContainer>();
        for(StepDefinitionMatch stepMatch : stepMatches) {
            final StepDefinitionMatchContainer stepMatchContainer = new StepDefinitionMatchContainer(stepMatch);
            stepMatchContainers.add(stepMatchContainer);
        }

        final Report output = new Report();

        output.stepDefinitions = stepDefinitionContainers;
        output.stepDefinitionMatches = stepMatchContainers;
        output.features = featureMaps;
        output.environment = config.getRaw();

        if(config.isDebug()) {
            out.append(gson().toJson(output));
        }

        if(config.shouldPublishResults()) {
            sendResult(output);
        }
    }

    @Override
    public void close() {
        out.close();
    }

    private void sendResult(Report output) {
        logger.log(Level.INFO, "Sending results to " + config.getHost());

        final HttpClient httpclient = HttpClients.createDefault();
        final HttpPost httppost = new HttpPost(config.getHost());
        httppost.setHeader("Content-Type", "application/json");
        httppost.setHeader("Accept-Charset", "utf-8");

        final String reportJSON = gson().toJson(output);
        final StringEntity reportStringEntity = new StringEntity(reportJSON, StandardCharsets.UTF_8);
        httppost.setEntity(reportStringEntity);

        final HttpResponse response;

        //Execute
        try {
            response = httpclient.execute(httppost);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not send report to Webcat: " + e.getMessage());
            return;
        }

        if (response.getStatusLine().getStatusCode() != 200) {
            logger.log(Level.SEVERE, "Could not send report to Webcat. Server responded with error: " + response.getStatusLine());
        }
    }

    @Override
    public void syntaxError(String state, String event, List<String> legalEvents, String uri, Integer line) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void startOfScenarioLifeCycle(Scenario scenario) {
        // NoOp
    }

    @Override
    public void endOfScenarioLifeCycle(Scenario scenario) {
        // NoOp
    }

    private List<Map<String, Object>> getFeatureElements() {
        List<Map<String, Object>> featureElements = (List) featureMap.get("elements");
        if (featureElements == null) {
            featureElements = new ArrayList<Map<String, Object>>();
            featureMap.put("elements", featureElements);
        }
        return featureElements;
    }

    private Map<Object, List<Map>> getFeatureElement() {
        if (getFeatureElements().size() > 0) {
            return (Map) getFeatureElements().get(getFeatureElements().size() - 1);
        } else {
            return null;
        }
    }

    private List<Map> getAllExamples() {
        List<Map> allExamples = getFeatureElement().get("examples");
        if (allExamples == null) {
            allExamples = new ArrayList<Map>();
            getFeatureElement().put("examples", allExamples);
        }
        return allExamples;
    }

    private List<Map> getSteps() {
        List<Map> steps = getFeatureElement().get("steps");
        if (steps == null) {
            steps = new ArrayList<Map>();
            getFeatureElement().put("steps", steps);
        }
        return steps;
    }

    private List<Map<String, String>> getEmbeddings() {
        List<Map<String, String>> embeddings = (List<Map<String, String>>) getCurrentStep(Phase.embedding).get("embeddings");
        if (embeddings == null) {
            embeddings = new ArrayList<Map<String, String>>();
            getCurrentStep(Phase.embedding).put("embeddings", embeddings);
        }
        return embeddings;
    }

    private List<String> getOutput() {
        List<String> output = (List<String>) getCurrentStep(Phase.output).get("output");
        if (output == null) {
            output = new ArrayList<String>();
            getCurrentStep(Phase.output).put("output", output);
        }
        return output;
    }

    protected Gson gson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }

    static class Report {
        public List<StepDefinitionContainer> stepDefinitions;
        public List<StepDefinitionMatchContainer> stepDefinitionMatches;
        public List<Map<String, Object>> features;
        public Map<EnvironmentConfig.Config, String> environment;
    }

    /**
     * Container of Step Definitions (patterns)
     */
    static class StepDefinitionContainer {
        public final String detailedLocation;
        public final String location;
        public final Integer parametersCount;
        public final String pattern;

        public final List<ParameterInfoContainer> parameters;

        public StepDefinitionContainer(StepDefinition stepDefinition) {
            this.detailedLocation = stepDefinition.getLocation(true);
            this.location = stepDefinition.getLocation(false);
            this.parametersCount = stepDefinition.getParameterCount();
            this.pattern = stepDefinition.getPattern();
            this.parameters = getParameters(stepDefinition);
        }

        private static List<ParameterInfoContainer> getParameters(StepDefinition stepDefinition) {
            int paramCount = stepDefinition.getParameterCount();
            List<ParameterInfoContainer> parameters = new ArrayList<ParameterInfoContainer>();
            for(int i = 0; i < paramCount; i++) {
                // argumentType will be ignored by Java implementation
                // the Cucumber usage of #getParameterType occurs only in StepDefinitionMatch and for types
                // String and DataTable
                final ParameterInfo paramInfo = stepDefinition.getParameterType(i, Object.class);

                final ParameterInfoContainer paramContainer = new ParameterInfoContainer(paramInfo);

                parameters.add(paramContainer);
            }
            return parameters;
        }
    }

    static class ParameterInfoContainer {
        // TODO: support transform (arguments) in paramInfo

        public final String type;
        public final String format;
        public final boolean isTransposed;

        public ParameterInfoContainer(ParameterInfo paramInfo) {
            this.type = paramInfo.getType().toString();
            this.format = paramInfo.getFormat();
            this.isTransposed = paramInfo.isTransposed();
        }
    }

    static class StackTraceElementContainer {
        public final String className;
        public final String fileName;
        public final int lineNumber;
        public final String methodName;
        public final boolean isNativeMethod;

        public StackTraceElementContainer(StackTraceElement location) {
            this.className = location.getClassName();
            this.fileName = location.getFileName();
            this.lineNumber = location.getLineNumber();
            this.methodName = location.getMethodName();
            this.isNativeMethod = location.isNativeMethod();
        }
    }

    /**
     * Contains for usage-entries of steps
     */
    static class StepDefinitionMatchContainer {
        public final String location;
        public final String pattern;
        public final String stepName;
        public final StackTraceElementContainer stepLocation;

        public StepDefinitionMatchContainer(StepDefinitionMatch stepMatch) {
            this.location = stepMatch.getLocation();
            this.pattern = stepMatch.getPattern();
            this.stepName = stepMatch.getStepName();
            this.stepLocation = new StackTraceElementContainer(stepMatch.getStepLocation());
        }
    }
}