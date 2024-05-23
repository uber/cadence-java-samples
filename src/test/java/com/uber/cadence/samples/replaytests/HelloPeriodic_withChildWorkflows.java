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

package com.uber.cadence.samples.replaytests;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

import com.google.common.base.Throwables;
import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.WorkflowIdReusePolicy;
import com.uber.cadence.activity.Activity;
import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.client.DuplicateWorkflowException;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.client.WorkflowException;
import com.uber.cadence.client.WorkflowStub;
import com.uber.cadence.internal.compatibility.Thrift2ProtoAdapter;
import com.uber.cadence.internal.compatibility.proto.serviceclient.IGrpcServiceStubs;
import com.uber.cadence.samples.hello.HelloChild;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerFactory;
import com.uber.cadence.workflow.Async;
import com.uber.cadence.workflow.Promise;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import java.time.Duration;
import java.util.Optional;

public class HelloPeriodic_withChildWorkflows {

  static final String TASK_LIST = "HelloPeriodic_withChildWorkflow";
  static final String PERIODIC_WORKFLOW_ID = "HelloPeriodic_withChildWorkflow";

  public interface GreetingWorkflow {
    @WorkflowMethod(
      // At most one instance.
      workflowId = PERIODIC_WORKFLOW_ID,
      // To allow starting workflow with the same ID after the previous one has terminated.
      workflowIdReusePolicy = WorkflowIdReusePolicy.AllowDuplicate,
      // Adjust this value to the maximum time workflow is expected to run.
      // It usually depends on the number of repetitions and interval between them.
      executionStartToCloseTimeoutSeconds = 300,
      taskList = TASK_LIST
    )
    void greetPeriodically(String name, Duration delay);
  }

  public interface GreetingActivities {
    void greet(String greeting);
  }

  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    private final int CONTINUE_AS_NEW_FREQUENCEY = 1000;

    private final GreetingActivities activities =
        Workflow.newActivityStub(
            GreetingActivities.class,
            new ActivityOptions.Builder()
                .setScheduleToCloseTimeout(Duration.ofSeconds(10))
                .build());

    /**
     * Stub used to terminate this workflow run and create the next one with the same ID atomically.
     */
    private final GreetingWorkflow continueAsNew =
        Workflow.newContinueAsNewStub(GreetingWorkflow.class);

    @Override
    public void greetPeriodically(String name, Duration delay) {

      HelloChild.GreetingChild child =
          Workflow.newChildWorkflowStub(HelloChild.GreetingChild.class);

      Promise<String> greeting = Async.function(child::composeGreeting, "Hello", name);

      System.out.println(greeting.get());

      // Loop the predefined number of times then continue this workflow as new.
      // This is needed to periodically truncate the history size.
      for (int i = 0; i < CONTINUE_AS_NEW_FREQUENCEY; i++) {
        activities.greet("Hello " + name + "!");
        Workflow.sleep(delay);
      }
      // Current workflow run stops executing after this call.
      continueAsNew.greetPeriodically(name, delay);
    }
  }

  static class GreetingActivitiesImpl implements GreetingActivities {

    @Override
    public void greet(String greeting) {
      System.out.println("From " + Activity.getWorkflowExecution() + ": " + greeting);
    }
  }

  public static void main(String[] args) throws InterruptedException {
    // Get a new client
    // NOTE: to set a different options, you can do like this:
    // ClientOptions.newBuilder().setRpcTimeout(5 * 1000).build();
    WorkflowClient workflowClient =
        WorkflowClient.newInstance(
            new Thrift2ProtoAdapter(IGrpcServiceStubs.newInstance()),
            WorkflowClientOptions.newBuilder().setDomain(DOMAIN).build());
    // Get worker to poll the task list.
    WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
    Worker worker = factory.newWorker(TASK_LIST);
    // Workflows are stateful. So you need a type to create instances.
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
    // Activities are stateless and thread safe. So a shared instance is used.
    worker.registerActivitiesImplementations(new GreetingActivitiesImpl());
    // Start listening to the workflow and activity task lists.
    factory.start();

    // Start a workflow execution. Usually this is done from another program.
    // To ensure that this daemon type workflow is always running try to start it periodically
    // ignoring the duplicated exception.
    // It is only to protect from application level failures.
    // Failures of a workflow worker don't lead to workflow failures.
    WorkflowExecution execution = null;
    while (true) {
      // Print reason of failure of the previous run, before restarting.
      if (execution != null) {
        WorkflowStub workflow = workflowClient.newUntypedWorkflowStub(execution, Optional.empty());
        try {
          workflow.getResult(Void.class); //
        } catch (WorkflowException e) {
          System.out.println("Previous instance failed:\n" + Throwables.getStackTraceAsString(e));
        }
      }
      // New stub instance should be created for each new workflow start.
      GreetingWorkflow workflow = workflowClient.newWorkflowStub(GreetingWorkflow.class);
      try {
        execution =
            WorkflowClient.start(workflow::greetPeriodically, "World", Duration.ofSeconds(3));
        System.out.println("Started " + execution);
      } catch (DuplicateWorkflowException e) {
        System.out.println("Still running as " + e.getExecution());
      } catch (Throwable e) {
        e.printStackTrace();
        System.exit(1);
      }
      // This value is so low just for the sample purpose. In production workflow
      // it is usually much higher.
      Thread.sleep(10000);
    }
  }
}
