package com.uber.cadence.samples.spring.workflows;

import com.uber.cadence.samples.spring.models.SampleMessage;
import com.uber.cadence.workflow.SignalMethod;
import com.uber.cadence.workflow.WorkflowMethod;

public interface SignalWorkflow {
  @WorkflowMethod
  void getGreeting(SampleMessage sampleMessage);

  @SignalMethod
  void waitForGreeting(String greeting);

  @SignalMethod
  void cancel();
}
