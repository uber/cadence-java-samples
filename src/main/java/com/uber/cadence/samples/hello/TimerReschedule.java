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
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerFactory;
import com.uber.cadence.workflow.SignalMethod;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;

public class TimerReschedule {

  static final String TASK_LIST = "TimerReschedule";

  public interface TimerRescheduleWorkflow {
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 10, taskList = TASK_LIST)
    String start();

    @SignalMethod
    void extend();
  }

  public static class TimerRescheduleWorkflowImpl implements TimerRescheduleWorkflow {

    private long expirationTimeMs;
    private static long TIMEOUT = 60 * 1000; // 1 minute

    @Override
    public String start() {
      expirationTimeMs = Workflow.currentTimeMillis() + TIMEOUT;
      while (Workflow.currentTimeMillis() < expirationTimeMs) {
        Workflow.sleep(expirationTimeMs - Workflow.currentTimeMillis());
      }
      return "completed";
    }

    @Override
    public void extend() {
      // extend for 1 minute
      expirationTimeMs = Workflow.currentTimeMillis() + 60 * 1000;
    }
  }

  public static void main(String[] args) {
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
    worker.registerWorkflowImplementationTypes(TimerRescheduleWorkflowImpl.class);
    // Start listening to the workflow and activity task lists.
    factory.start();

    // Get a workflow stub using the same task list the worker uses.
    TimerRescheduleWorkflow workflow =
        workflowClient.newWorkflowStub(TimerRescheduleWorkflow.class);
    // Execute a workflow waiting for it to complete.
    String greeting = workflow.start();
    System.out.println(greeting);
    System.exit(0);
  }
}
