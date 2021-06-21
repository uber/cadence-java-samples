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

import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * Demonstrates triggering an activity in response to a cancellation request. Requires a local
 * instance of Cadence server to be running.
 */
public class HelloCancellation {

  static final String TASK_LIST = "HelloCancellation";

  /** Workflow interface has to have at least one method annotated with @WorkflowMethod. */
  public interface GreetingWorkflow {
    /** @return greeting string */
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 30, taskList = TASK_LIST)
    String getGreeting(String name);
  }

  /** Activity interface is just a POJI. */
  public interface GreetingActivities {
    @ActivityMethod(scheduleToCloseTimeoutSeconds = 2)
    String composeGreeting(String greeting, String name);

    @ActivityMethod(scheduleToCloseTimeoutSeconds = 2)
    String sayGoodbye(String name);
  }

  /** GreetingWorkflow implementation that calls GreetingsActivities#composeGreeting. */
  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    /**
     * Activity stub implements activity interface and proxies calls to it to Cadence activity
     * invocations. Because activities are reentrant, only a single stub can be used for multiple
     * activity invocations.
     */
    private final GreetingActivities activities =
        Workflow.newActivityStub(GreetingActivities.class);

    @Override
    public String getGreeting(String name) {
      try {
        final String result = activities.composeGreeting("Hello", name);
        Workflow.sleep(Duration.ofDays(10));
        return result;
        // This exception is thrown when a cancellation is requested on the current workflow
      } catch (CancellationException e) {
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

    private final List<String> invocations = new ArrayList<>();

    @Override
    public String composeGreeting(String greeting, String name) {
      invocations.add("composeGreeting");
      return greeting + " " + name + "!";
    }

    @Override
    public String sayGoodbye(String name) {
      invocations.add("sayGoodbye");
      return "Goodbye " + name + "!";
    }

    List<String> getInvocations() {
      return invocations;
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
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
    // Activities are stateless and thread safe. So a shared instance is used.
    // A shared instance is used to show activity invocations.
    GreetingActivitiesImpl activities = new GreetingActivitiesImpl();
    worker.registerActivitiesImplementations(activities);
    // Start listening to the workflow and activity task lists.
    factory.start();

    WorkflowOptions workflowOptions =
        new WorkflowOptions.Builder()
            .setTaskList(HelloCancellation.TASK_LIST)
            .setExecutionStartToCloseTimeout(Duration.ofDays(30))
            .build();
    // NOTE: strongly typed workflow stub doesn't cancel method.
    WorkflowStub client =
        workflowClient.newUntypedWorkflowStub("GreetingWorkflow::getGreeting", workflowOptions);

    client.start("World");

    // issue cancellation request. This will trigger a CancellationException on the workflow.
    client.cancel();

    try {
      client.getResult(String.class);
    } catch (CancellationException ignored) {
      System.out.println("workflow cancelled. Cancellation exception thrown");
    }

    System.out.println(activities.getInvocations());
    System.exit(0);
  }
}
