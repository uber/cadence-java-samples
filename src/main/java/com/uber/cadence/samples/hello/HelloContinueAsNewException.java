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

import com.google.common.base.Throwables;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.client.WorkflowException;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerFactory;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;

/**
 * Demonstrates how to get exception after ContinueAsNew
 *
 * <p>Requires a local instance of Cadence server to be running.
 */
public class HelloContinueAsNewException {

  static final String TASK_LIST = "HelloContinueAsNewException";

  public interface GreetingWorkflow {
    /**
     * Use single fixed ID to ensure that there is at most one instance running. To run multiple
     * instances set different IDs through WorkflowOptions passed to the
     * WorkflowClient.newWorkflowStub call.
     */
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 300, taskList = TASK_LIST)
    void getGreeting(String name, boolean canThrow);
  }

  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    /**
     * ContinueAsNew Stub is used to terminate this workflow run and create the next one with the
     * same ID atomically.
     */
    private final GreetingWorkflow continueAsNew =
        Workflow.newContinueAsNewStub(GreetingWorkflow.class);

    @Override
    public void getGreeting(String name, boolean canThrow) {
      if (canThrow) {
        throw new RuntimeException(name);
      }

      // Current workflow run stops executing after this call.
      continueAsNew.getGreeting(name, true);
      // unreachable line
    }
  }

  public static void main(String[] args) throws InterruptedException {
    // Get a new client
    // NOTE: to set a different options, you can do like this:
    // ClientOptions.newBuilder().setRpcTimeout(5 * 1000).build();
    WorkflowClient workflowClient =
        WorkflowClient.newInstance(
            new WorkflowServiceTChannel(ClientOptions.defaultInstance()),
            WorkflowClientOptions.newBuilder().setDomain(DOMAIN).build());
    // Get worker to poll the task list.
    WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
    Worker worker = factory.newWorker(TASK_LIST);
    // Workflows are stateful. So you need a type to create instances.
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
    // Start listening to the workflow and activity task lists.
    factory.start();

    GreetingWorkflow workflow = workflowClient.newWorkflowStub(GreetingWorkflow.class);
    try {
      workflow.getGreeting("World", false);
    } catch (WorkflowException e) {
      System.out.println("\nStack Trace:\n" + Throwables.getStackTraceAsString(e));
      System.exit(1);
    }
  }
}
