package com.uber.cadence.samples.spring.workflows;

import com.uber.cadence.workflow.WorkflowMethod;

public interface ChildWorkflow {
  @WorkflowMethod
  String greetInChild(String msg);
}
