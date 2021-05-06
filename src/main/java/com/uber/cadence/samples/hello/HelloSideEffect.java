/*
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package com.uber.cadence.samples.hello;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.workflow.QueryMethod;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import java.util.UUID;

/**
 * Hello SideEffect Cadence workflow that sets a SideEffect. The set value can be queried Requires a
 * local instance the Cadence service to be running.
 */
public class HelloSideEffect {

  static final String TASK_LIST = "HelloSideEffect";

  /** Workflow interface has to have at least one method annotated with @WorkflowMethod. */
  public interface SideEffectWorkflow {
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 10, taskList = TASK_LIST)
    void start();

    /** @return set value */
    @QueryMethod
    String get();
  }

  public static class SideEffectWorkflowImpl implements SideEffectWorkflow {

    private String value = "";

    @Override
    public void start() {
      this.value =
          Workflow.sideEffect(
              String.class,
              () -> {
                return UUID.randomUUID().toString();
              });
    }

    @Override
    public String get() {
      return this.value;
    }
  }

  public static void main(String[] args) {
    // Start a worker that hosts both workflow and activity implementations.
    Worker.Factory factory = new Worker.Factory(DOMAIN);
    Worker worker = factory.newWorker(TASK_LIST);
    // Workflows are stateful. So you need a type to create instances.
    worker.registerWorkflowImplementationTypes(SideEffectWorkflowImpl.class);
    // Start listening to the workflow and activity task lists.
    factory.start();

    // Start a workflow execution. Usually this is done from another program.
    WorkflowClient workflowClient = WorkflowClient.newInstance(DOMAIN);
    // Get a workflow stub using the same task list the worker uses.
    SideEffectWorkflow workflow = workflowClient.newWorkflowStub(SideEffectWorkflow.class);
    // Execute a workflow waiting for it to complete.
    workflow.start();
    // Query and print the set value
    System.out.println(workflow.get());
    System.exit(0);
  }
}
