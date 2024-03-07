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
import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.client.WorkflowException;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.client.WorkflowStub;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerFactory;
import com.uber.cadence.workflow.CancellationScope;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import java.time.Duration;
import java.util.concurrent.CancellationException;

/** Demonstrates how cancellation populated from parent to child workflow */
public class HelloCancelChild {

  static final String TASK_LIST = "HelloCancelChild";

  public interface GreetingWorkflow {
    @WorkflowMethod
    String getGreeting(String name);
  }

  public interface GreetingChild {
    @WorkflowMethod
    String composeGreeting(String greeting, String name);
  }

  public interface GreetingActivities {
    String sayGoodbye(String name);
  }

  /** Parent implementation that calls GreetingChild#composeGreeting. */
  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    @Override
    public String getGreeting(String name) {
      GreetingChild child = Workflow.newChildWorkflowStub(GreetingChild.class);
      return child.composeGreeting("Hello", name);
    }
  }

  /** Child workflow implementation. */
  public static class GreetingChildImpl implements GreetingChild {

    private final GreetingActivities activities =
        Workflow.newActivityStub(
            GreetingActivities.class,
            new ActivityOptions.Builder()
                .setScheduleToCloseTimeout(Duration.ofSeconds(20))
                .build());

    @Override
    public String composeGreeting(String greeting, String name) {
      try {
        Workflow.sleep(Duration.ofDays(10));
        return greeting + name;
        // This exception is thrown when a cancellation is requested on the current workflow
      } catch (CancellationException e) {
        // clean up on cancellation
        /**
         * Any call to an activity or a child workflow after the workflow is cancelled is going to
         * fail immediately with the CancellationException. the DetachedCancellationScope doesn't
         * inherit its cancellation status from the enclosing scope. Thus it allows running a
         * cleanup activity even if the workflow cancellation was requested.
         */
        CancellationScope scope =
            Workflow.newDetachedCancellationScope(() -> activities.sayGoodbye(name));
        scope.run();
        throw e;
      }
    }
  }

  static class GreetingActivitiesImpl implements GreetingActivities {
    @Override
    public String sayGoodbye(String name) {
      try {
        Thread.sleep(1000 * 10);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      return name;
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
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class, GreetingChildImpl.class);
    worker.registerActivitiesImplementations(new GreetingActivitiesImpl());
    factory.start();

    WorkflowOptions workflowOptions =
        new WorkflowOptions.Builder()
            .setTaskList(TASK_LIST)
            .setExecutionStartToCloseTimeout(Duration.ofSeconds(30))
            .build();
    GreetingWorkflow workflow =
        workflowClient.newWorkflowStub(GreetingWorkflow.class, workflowOptions);
    try {
      // NOTE: strongly typed workflow stub doesn't cancel method.
      WorkflowStub stub =
          workflowClient.newUntypedWorkflowStub("GreetingWorkflow::getGreeting", workflowOptions);

      stub.start("World");
      // wait for child workflow to start
      Thread.sleep(1000);

      // issue cancellation request. This will trigger a CancellationException on the workflow.
      stub.cancel();

      stub.getResult(String.class);
    } catch (WorkflowException | InterruptedException e) {
      Throwable cause = Throwables.getRootCause(e);
      // prints "Hello World!"
      System.out.println(cause.getMessage());
      System.out.println("\nStack Trace:\n" + Throwables.getStackTraceAsString(e));
    }
    System.exit(0);
  }
}
