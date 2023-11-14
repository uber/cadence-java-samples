package com.uber.cadence.samples.spring.workflows.impl;

import com.uber.cadence.samples.spring.models.SampleMessage;
import com.uber.cadence.samples.spring.workflows.SignalWorkflow;
import com.uber.cadence.workflow.Workflow;
import java.util.Optional;
import org.slf4j.Logger;

public class SignalWorkflowImpl implements SignalWorkflow {
  private final Logger logger = Workflow.getLogger(SignalWorkflowImpl.class);
  private String name;
  private String greetingMsg = "";
  private boolean cancel = false;

  @Override
  public void getGreeting(SampleMessage sampleMessage) {
    logger.info("executing SignalWorkflow::getGreeting");
    this.name = sampleMessage.GetMessage();
    int count = 0;

    while (!cancel) {
      Workflow.await(() -> cancel || !this.greetingMsg.isEmpty());
      if (!this.greetingMsg.isEmpty()) {
        logger.info(++count + ": " + this.greetingMsg + "!");
        this.greetingMsg = "";

        // A workflow execution cannot receive infinite number of signals due to history limit
        // By default 10000 is MaximumSignalsPerExecution which can be configured by DynamicConfig of Cadence cluster.
        // But it's recommended to do continueAsNew after receiving certain number of signals.
        // in production, use a number <1000.
        if (count == 3) {
          Workflow.continueAsNew(
              Optional.of("SignalWorkflow::getGreeting"), Optional.empty(), sampleMessage);
          return;
        }
      }
    }

    logger.info("workflow canceled");
  }

  @Override
  public void waitForGreeting(String greeting) {
    if (cancel) {
      logger.info("signal workflow failed because it is already cancelled");
      return;
    }

    logger.info("received signal from SignalWorkflow:waitForName");
    this.greetingMsg = String.format("%s, %s!", greeting, this.name);
  }

  @Override
  public void cancel() {
    this.cancel = true;
  }
}
