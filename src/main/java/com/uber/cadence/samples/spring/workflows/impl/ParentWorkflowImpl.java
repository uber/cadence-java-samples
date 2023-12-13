package com.uber.cadence.samples.spring.workflows.impl;

import com.uber.cadence.samples.spring.models.SampleMessage;
import com.uber.cadence.samples.spring.workflows.ChildWorkflow;
import com.uber.cadence.samples.spring.workflows.ParentWorkflow;
import com.uber.cadence.workflow.Workflow;

public class ParentWorkflowImpl implements ParentWorkflow {
  @Override
  public String getGreetingInParent(SampleMessage sampleMessage) {
    // Workflows are stateful. So a new stub must be created for each new child.
    ChildWorkflow childWorkflow = Workflow.newChildWorkflowStub(ChildWorkflow.class);
    return childWorkflow.greetInChild(sampleMessage.GetMessage());
  }
}
