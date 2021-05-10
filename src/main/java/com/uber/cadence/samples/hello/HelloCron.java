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

import com.uber.cadence.TerminateWorkflowExecutionRequest;
import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.WorkflowIdReusePolicy;
import com.uber.cadence.activity.Activity;
import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.common.CronSchedule;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerFactory;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import java.time.Duration;

/**
 * Demonstrates a cron workflow that executes activity periodically. Requires a local instance of
 * Cadence server to be running.
 */
public class HelloCron {

  static final String TASK_LIST = "HelloCron";
  static final String CRON_WORKFLOW_ID = "HelloCron";

  public interface CronWorkflow {
    @WorkflowMethod(
      // At most one instance.
      workflowId = CRON_WORKFLOW_ID,
      taskList = TASK_LIST,
      // timeout for every run
      executionStartToCloseTimeoutSeconds = 30,
      // To allow starting workflow with the same ID after the previous one has terminated.
      workflowIdReusePolicy = WorkflowIdReusePolicy.AllowDuplicate
    )
    @CronSchedule("*/1 * * * *") // new workflow run every minute
    void greetPeriodically(String name);
  }

  public interface GreetingActivities {
    void greet(String greeting);
  }

  public static class CronWorkflowImpl implements CronWorkflow {

    private final GreetingActivities activities =
        Workflow.newActivityStub(
            GreetingActivities.class,
            new ActivityOptions.Builder()
                .setScheduleToCloseTimeout(Duration.ofSeconds(10))
                .build());

    @Override
    public void greetPeriodically(String name) {
      activities.greet("Hello " + name + "!");
    }
  }

  static class GreetingActivitiesImpl implements GreetingActivities {
    @Override
    public void greet(String greeting) {
      System.out.println("From " + Activity.getWorkflowExecution() + ": " + greeting);
    }
  }

  public static void main(String[] args) throws InterruptedException {
    final WorkflowServiceTChannel cadenceService =
        new WorkflowServiceTChannel(ClientOptions.defaultInstance());
    // Get a new client
    // NOTE: to set a different options, you can do like this:
    // ClientOptions.newBuilder().setRpcTimeout(5 * 1000).build();
    WorkflowClient workflowClient =
        WorkflowClient.newInstance(
            cadenceService, WorkflowClientOptions.newBuilder().setDomain(DOMAIN).build());
    // Get worker to poll the task list.
    WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
    Worker worker = factory.newWorker(TASK_LIST);
    // Workflows are stateful. So you need a type to create instances.
    worker.registerWorkflowImplementationTypes(CronWorkflowImpl.class);
    // Activities are stateless and thread safe. So a shared instance is used.
    worker.registerActivitiesImplementations(new GreetingActivitiesImpl());
    // Start listening to the workflow and activity task lists.
    factory.start();

    // Start a workflow execution async. Usually this is done from another program.
    CronWorkflow workflow = workflowClient.newWorkflowStub(CronWorkflow.class);
    WorkflowClient.start(workflow::greetPeriodically, "World");
    System.out.println("Cron workflow is running");

    // Cron workflow will not stop until it is terminated or cancelled.
    // So we wait some time to see cron run twice then terminate the cron workflow.
    Thread.sleep(90000);

    // execution without RunID set will be used to terminate current run
    WorkflowExecution execution = new WorkflowExecution();
    execution.setWorkflowId(CRON_WORKFLOW_ID);
    TerminateWorkflowExecutionRequest request = new TerminateWorkflowExecutionRequest();
    request.setDomain(DOMAIN);
    request.setWorkflowExecution(execution);
    try {
      cadenceService.TerminateWorkflowExecution(request);
      System.out.println("Cron workflow is terminated");
    } catch (Exception e) {
      System.out.println(e);
    }
    System.exit(0);
  }
}
