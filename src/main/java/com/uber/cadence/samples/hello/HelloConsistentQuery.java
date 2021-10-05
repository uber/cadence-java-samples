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

import com.uber.cadence.QueryConsistencyLevel;
import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.client.QueryOptions;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.client.WorkflowStub;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerFactory;
import com.uber.cadence.workflow.QueryMethod;
import com.uber.cadence.workflow.SignalMethod;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import java.time.Duration;

/**
 * Demonstrates consistent query capability. Requires a local instance of Cadence server of version
 * >= 0.22.0 to be running.
 */
public class HelloConsistentQuery {

  static final String TASK_LIST = "HelloQuery";

  public interface GreetingWorkflow {

    @WorkflowMethod
    void createGreeting(String name);

    @SignalMethod
    void increase();

    /** Returns greeting as a query value. */
    @QueryMethod
    int getCounter();
  }

  /** GreetingWorkflow implementation that updates greeting after sleeping for 5 seconds. */
  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    private int counter;

    @Override
    public void createGreeting(String name) {
      // Workflow code always uses WorkflowThread.sleep
      // and Workflow.currentTimeMillis instead of standard Java ones.
      Workflow.sleep(Duration.ofDays(2));
    }

    @Override
    public void increase() {
      this.counter++;
    }

    @Override
    public int getCounter() {
      return counter;
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
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
    factory.start();

    // Start a workflow execution. Usually this is done from another program.
    // Get a workflow stub using the same task list the worker uses.
    WorkflowOptions workflowOptions =
        new WorkflowOptions.Builder()
            .setTaskList(TASK_LIST)
            .setExecutionStartToCloseTimeout(Duration.ofSeconds(30))
            .build();
    final WorkflowStub workflow =
        workflowClient.newUntypedWorkflowStub("GreetingWorkflow::createGreeting", workflowOptions);

    // Start workflow asynchronously to not use another thread to query.
    final WorkflowExecution wf = workflow.start("World");
    System.out.println("started workflow " + wf.getWorkflowId() + ", " + wf.getRunId());
    System.out.println("initial value after started");
    System.out.println(
        workflow.queryWithOptions(
            "GreetingWorkflow::getCounter",
            new QueryOptions.Builder()
                .setQueryConsistencyLevel(QueryConsistencyLevel.STRONG)
                .build(),
            Integer.class,
            Integer.class)); // Should print 0

    // Now we can send a signal to it using workflow stub.
    workflow.signal("GreetingWorkflow::increase");
    System.out.println("after increase 1 time");
    System.out.println(
        workflow.queryWithOptions(
            "GreetingWorkflow::getCounter",
            new QueryOptions.Builder()
                .setQueryConsistencyLevel(QueryConsistencyLevel.STRONG)
                .build(),
            Integer.class,
            Integer.class)); // Should print 1

    workflow.signal("GreetingWorkflow::increase");
    workflow.signal("GreetingWorkflow::increase");
    workflow.signal("GreetingWorkflow::increase");
    workflow.signal("GreetingWorkflow::increase");
    System.out.println("after increase 1+4 times");
    System.out.println(
        workflow.queryWithOptions(
            "GreetingWorkflow::getCounter",
            new QueryOptions.Builder()
                .setQueryConsistencyLevel(QueryConsistencyLevel.STRONG)
                .build(),
            Integer.class,
            Integer.class)); // Should print 5
    System.exit(0);
  }
}
