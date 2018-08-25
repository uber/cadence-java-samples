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

import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.client.WorkflowStub;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

public class HelloCancellation {
  static final String TASK_LIST = "HelloCancellation";

  /** Workflow interface has to have at least one method annotated with @WorkflowMethod. */
  public interface GreetingWorkflow {
    /** @return greeting string. If cancelled then it will invoke the sayGoodbye activity. */
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 20, taskList = TASK_LIST)
    String getGreeting(String name) throws Exception;
  }

  /** Activity interface is just a POJI. */
  public interface GreetingActivities {
    @ActivityMethod(scheduleToCloseTimeoutSeconds = 2)
    String composeGreeting(String greeting, String name) throws Exception;

    @ActivityMethod(scheduleToCloseTimeoutSeconds = 2)
    String sayGoodbye(String name);
  }

  /** GreetingWorkflow implementation that calls GreetingsActivities#composeGreeting. */
  public static class GreetingWorkflowImpl implements HelloCancellation.GreetingWorkflow {

    /**
     * Activity stub implements activity interface and proxies calls to it to Cadence activity
     * invocations. Because activities are reentrant, only a single stub can be used for multiple
     * activity invocations.
     */
    private final HelloCancellation.GreetingActivities activities =
        Workflow.newActivityStub(HelloCancellation.GreetingActivities.class);

    @Override
    public String getGreeting(String name) throws Exception {
      try {
        Workflow.sleep(Duration.ofSeconds(10));
        return activities.composeGreeting("Hello", name);
      } catch (
          CancellationException
              e) { // This exception is thrown when a cancellation is requested on the current
                   // workflow
        /**
         * Issue a goodbye activity in response to cancellation in a detached scope. This means that
         * the cancellation issued on the parent workflow will NOT trigger cancelation on child
         * workflows
         */
        Workflow.newDetachedCancellationScope(() -> activities.sayGoodbye(name));
      }
      return "Greeting took too long. So we said goodbye.";
    }
  }

  static class GreetingActivitiesImpl implements HelloCancellation.GreetingActivities {

    private List<String> invocations = new ArrayList<>();

    @Override
    public String composeGreeting(String greeting, String name) throws Exception {
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
    // Start a worker that hosts both workflow and activity implementations.
    Worker.Factory factory = new Worker.Factory(DOMAIN);
    Worker worker = factory.newWorker(TASK_LIST);

    // Workflows are stateful. So you need a type to create instances.
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);

    // A shared instance is used to show activity invocations.
    GreetingActivitiesImpl activities = new GreetingActivitiesImpl();
    worker.registerActivitiesImplementations(activities);

    // Start listening to the workflow and activity task lists.
    factory.start();

    // Start a workflow execution. Usually this is done from another program.
    WorkflowClient workflowClient = WorkflowClient.newInstance(DOMAIN);
    WorkflowOptions workflowOptions =
        new WorkflowOptions.Builder()
            .setTaskList(HelloCancellation.TASK_LIST)
            .setExecutionStartToCloseTimeout(Duration.ofDays(30))
            .build();
    WorkflowStub client =
        workflowClient.newUntypedWorkflowStub("GreetingWorkflow::getGreeting", workflowOptions);

    client.start("World");

    // issue cancellation request. This will trigger a CancellationException on the workflow which
    // can be ignored since it's expected
    client.cancel();

    try {
      client.getResult(String.class);
    } catch (CancellationException ignored) {
    }

    System.out.println(activities.getInvocations());
    System.exit(0);
  }
}
