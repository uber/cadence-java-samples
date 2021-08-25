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
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerFactory;
import com.uber.cadence.worker.WorkerFactoryOptions;
import com.uber.cadence.worker.WorkerOptions;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;

import java.time.LocalDateTime;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

/**
 * Hello World Cadence workflow that executes a single activity. Requires a local instance the
 * Cadence service to be running.
 */
public class HelloLocalActivity {

  static final String TASK_LIST = "HelloActivity";

  /** Workflow interface has to have at least one method annotated with @WorkflowMethod. */
  public interface GreetingWorkflow {
    /** @return greeting string */
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 10, taskList = TASK_LIST)
    String getGreeting(String name);
  }

  /** Activity interface is just a POJI. */
  public interface GreetingActivities {
    @ActivityMethod(scheduleToCloseTimeoutSeconds = 2)
    String composeGreeting(String greeting, String name);
  }

  /** GreetingWorkflow implementation that calls GreetingsActivities#composeGreeting. */
  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    /**
     * Activity stub implements activity interface and proxies calls to it to Cadence activity
     * invocations. Because activities are reentrant, only a single stub can be used for multiple
     * activity invocations.
     */
    private final GreetingActivities activities =
        Workflow.newLocalActivityStub(GreetingActivities.class);

    @Override
    public String getGreeting(String name) {
      int delaysRemaining = 2;

      while (delaysRemaining > 0) {
        System.out.println("Workflow.sleep-ing name:" + name);
        Workflow.sleep(500);
        delaysRemaining -= 1;
      }

      for (int i = 0; i < 10; i++) {
        activities.composeGreeting("Hello" + i, name);
      }

      // This is a blocking call that returns only after the activity has completed.
      return activities.composeGreeting("Hello", name);
    }
  }

  static class GreetingActivitiesImpl implements GreetingActivities {
    @Override
    public String composeGreeting(String greeting, String name) {
      try {
        System.out.println("Activity.sleep-ing" + " name: " + name + "greeting: " + greeting );
        Thread.sleep(100);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      return greeting + " " + name + "!";
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
    WorkerFactoryOptions workerFactoryOptions =
        WorkerFactoryOptions.newBuilder().setStickyCacheSize(10).build();
    WorkerFactory factory = WorkerFactory.newInstance(workflowClient, workerFactoryOptions);

    WorkerOptions workerOptions =
        WorkerOptions.newBuilder()
            .setMaxConcurrentWorkflowExecutionSize(5)
            .setMaxConcurrentActivityExecutionSize(5)
            .setMaxConcurrentLocalActivityExecutionSize(5)
            .build();

    Worker worker = factory.newWorker(TASK_LIST, workerOptions);

    // Workflows are stateful. So you need a type to create instances.
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);

    // Activities are stateless and thread safe. So a shared instance is used.
    worker.registerActivitiesImplementations(new GreetingActivitiesImpl());

    // Start listening to the workflow and activity task lists.
    factory.start();

    System.out.println("Starting at " + LocalDateTime.now());

    // Execute many asynchronously
    int workflowCount = 1200;
    for (int i = 0; i < workflowCount; i++) {
      GreetingWorkflow workflow = workflowClient.newWorkflowStub(GreetingWorkflow.class);
      WorkflowClient.start(workflow::getGreeting, "World" + workflowCount);
    }

    // Don't let process exit since worker is working async
    while (true) {
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    //    System.exit(0);
  }
}
