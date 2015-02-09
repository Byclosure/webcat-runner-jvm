package cucumber.examples.java.calculator;


import gherkin.deps.com.google.gson.Gson;
import gherkin.deps.com.google.gson.GsonBuilder;
import gherkin.formatter.Formatter;
import gherkin.formatter.NiceAppendable;
import gherkin.formatter.Reporter;
import gherkin.formatter.model.Background;
import gherkin.formatter.model.Examples;
import gherkin.formatter.model.Feature;
import gherkin.formatter.model.Match;
import gherkin.formatter.model.Result;
import gherkin.formatter.model.Scenario;
import gherkin.formatter.model.ScenarioOutline;
import gherkin.formatter.model.Step;

import java.util.ArrayList;
import java.util.List;

import cucumber.runtime.ParameterInfo;
import cucumber.runtime.StepDefinition;
import cucumber.runtime.StepDefinitionMatch;

/**
 * Formatter to measure performance of steps. Aggregated results for all steps can be computed
 * by adding {@link UsageStatisticStrategy} to the usageFormatter
 */
public class StepMatchFormatter implements Formatter, Reporter {
	private final List<StepDefinition> stepDefinitions;
	private final List<StepDefinitionMatch> stepMatches;
    
    private final NiceAppendable out;

    /**
     * Constructor
     *
     * @param out {@link Appendable} to print the result
     */
    public StepMatchFormatter(Appendable out, List<StepDefinition> stepDefinitions) {
    	this.out = new NiceAppendable(out);
    	this.stepDefinitions = stepDefinitions;
    	this.stepMatches =  new ArrayList<StepDefinitionMatch>();
    }

    @Override
    public void uri(String uri) {
    }

    @Override
    public void feature(Feature feature) {
    }

    @Override
    public void background(Background background) {
    }

    @Override
    public void scenario(Scenario scenario) {
    }

    @Override
    public void scenarioOutline(ScenarioOutline scenarioOutline) {
    }

    @Override
    public void examples(Examples examples) {
    }

    @Override
    public void embedding(String mimeType, byte[] data) {
    }

    @Override
    public void write(String text) {
    }

    @Override
    public void step(Step step) {
    }

    @Override
    public void eof() {
    }

    @Override
    public void syntaxError(String state, String event, List<String> legalEvents, String uri, Integer line) {
    }

    @Override
    public void startOfScenarioLifeCycle(Scenario scenario) {
        // NoOp
    }

    @Override
    public void endOfScenarioLifeCycle(Scenario scenario) {
        // NoOp
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
        out.append(gson().toJson(output));
    }

    private Gson gson() {
        return new GsonBuilder().setPrettyPrinting().create();
    }

    @Override
    public void close() {
        out.close();
    }

    @Override
    public void result(Result result) {
    }

    @Override
    public void before(Match match, Result result) {
    }

    @Override
    public void after(Match match, Result result) {
    }    
    
    @Override
    public void match(Match match) {
    	if (match instanceof StepDefinitionMatch) {
    		final StepDefinitionMatch stepMatch = (StepDefinitionMatch)match;
    		stepMatches.add(stepMatch);
    		
    	} else {
    		throw new RuntimeException("Unsupported Match class: `" + match.getClass().getCanonicalName() + "'");
    	}
    	
    }
    
    static class Report {
    	public List<StepDefinitionContainer> stepDefinitions;
    	public List<StepDefinitionMatchContainer> stepDefinitionMatches;
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
