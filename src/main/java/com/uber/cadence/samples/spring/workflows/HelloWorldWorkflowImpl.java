package com.uber.cadence.samples.spring.workflows;

import com.uber.cadence.samples.spring.models.SampleMessage;
import com.uber.cadence.workflow.Workflow;
import org.slf4j.Logger;

public class HelloWorldWorkflowImpl implements HelloWorldWorkflow {
  private final Logger logger = Workflow.getLogger(HelloWorldWorkflowImpl.class);

  @Override
  public String sayHello(SampleMessage message) {
    logger.info("executing HelloWorldWorkflow::sayHello");

    String result = "Hello, " + message.GetMessage();
    logger.info("output: " + result);
    return result;
  }
}
