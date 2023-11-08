package com.uber.cadence.samples.spring.workflows.impl;

import com.uber.cadence.samples.spring.models.SampleMessage;
import com.uber.cadence.samples.spring.workflows.SignalWorkflow;
import com.uber.cadence.workflow.Workflow;
import org.slf4j.Logger;

public class SignalWorkflowImpl implements SignalWorkflow {
  private final Logger logger = Workflow.getLogger(SignalWorkflowImpl.class);
  private String name;
  private boolean cancel = false;

  @Override
  public void getGreeting(SampleMessage sampleMessage) {
    logger.info("executing SignalWorkflow::getGreeting");
    this.name = sampleMessage.GetMessage();
    while (true) {
      Workflow.await(() -> cancel);
      if (cancel) {
        logger.info("SignalWorkflow cancelled");
        return;
      }
    }
  }

  @Override
  public void waitForGreeting(String greeting) {
    logger.info("received signal from SignalWorkflow:waitForName");
    logger.info(greeting + " " + name + "!");
  }

  @Override
  public void cancel() {
    this.cancel = true;
  }
}
