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

import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.WorkflowIdReusePolicy;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;

public class WorkflowStarter {

  @SuppressWarnings("CatchAndPrintStackTrace")
  public static void main(String[] args) {
    final WorkflowClient workflowClient =
        WorkflowClient.newInstance(
            new WorkflowServiceTChannel(ClientOptions.defaultInstance()),
            WorkflowClientOptions.newBuilder().setDomain(DOMAIN).build());

    WorkflowOptions workflowOptions =
        new WorkflowOptions.Builder()
            .setTaskList(WorkflowWorker.DEFAULT_TASK_LIST)
            .setWorkflowId("test-workflow-id")
            .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.AllowDuplicate)
            .build();
    WorkflowMethods calculation =
        workflowClient.newWorkflowStub(WorkflowMethods.class, workflowOptions);

    WorkflowExecution execution = WorkflowClient.start(calculation::calculate, 4L, 5L, 6L);
    System.out.println("started workflow execution" + execution);
    System.exit(0);
  }
}
