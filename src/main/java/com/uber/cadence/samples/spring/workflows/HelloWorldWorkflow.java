package com.uber.cadence.samples.spring.workflows;

import static com.uber.cadence.samples.spring.common.Constant.TASK_LIST;

import com.uber.cadence.samples.spring.models.SampleMessage;
import com.uber.cadence.workflow.WorkflowMethod;

public interface HelloWorldWorkflow {
  @WorkflowMethod(
    executionStartToCloseTimeoutSeconds = 10,
    taskStartToCloseTimeoutSeconds = 10,
    taskList = TASK_LIST
  )
  String sayHello(SampleMessage message);
}
