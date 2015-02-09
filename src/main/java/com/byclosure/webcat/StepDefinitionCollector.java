package com.byclosure.webcat;

import java.util.ArrayList;
import java.util.List;

import cucumber.api.StepDefinitionReporter;
import cucumber.runtime.StepDefinition;

public class StepDefinitionCollector implements StepDefinitionReporter {
	private final List<StepDefinition> stepDefinitions = new ArrayList<StepDefinition>();
	
	@Override
	public void stepDefinition(StepDefinition stepDefinition) {
		stepDefinitions.add(stepDefinition);
	}
	
	public List<StepDefinition> getStepDefinitions() {
		return stepDefinitions;
	}
}