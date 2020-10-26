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

package com.uber.cadence.samples.calculation;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

import com.uber.cadence.WorkflowIdReusePolicy;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.worker.Worker;
import java.time.Duration;

public class Main {

  static final String DEFAULT_TASK_LIST = "calculation-default-tasklist";

  @SuppressWarnings("CatchAndPrintStackTrace")
  public static void main(String[] args) {
    // Get worker to poll the common task list.
    Worker.Factory factory = new Worker.Factory(DOMAIN);
    final Worker workerForCommonTaskList = factory.newWorker(DEFAULT_TASK_LIST);
    workerForCommonTaskList.registerWorkflowImplementationTypes(WorkflowMethodsImpl.class);
    Activities activities = new ActivitiesImpl();
    workerForCommonTaskList.registerActivitiesImplementations(activities);

    // Start all workers created by this factory.
    factory.start();
    System.out.println("Worker started for task list: " + DEFAULT_TASK_LIST);

    final WorkflowClient workflowClient = WorkflowClient.newInstance(DOMAIN);

    WorkflowOptions workflowOptions =
        new WorkflowOptions.Builder()
            .setTaskList(DEFAULT_TASK_LIST)
            .setExecutionStartToCloseTimeout(Duration.ofSeconds(30))
            .setWorkflowId("test-workflow-id")
            .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.AllowDuplicate)
            .build();
    WorkflowMethods calculation = workflowClient.newWorkflowStub(WorkflowMethods.class, workflowOptions);
    WorkflowClient.start(calculation::calculate, 4L, 5L, 6L);
  }
}
