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

package com.uber.cadence.samples.shadowing;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

import com.google.common.collect.Lists;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.samples.hello.HelloActivity;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.shadower.ExitCondition;
import com.uber.cadence.shadower.Mode;
import com.uber.cadence.worker.ShadowingOptions;
import com.uber.cadence.worker.ShadowingWorker;
import com.uber.cadence.worker.WorkerOptions;
import com.uber.cadence.worker.WorkflowStatus;
import java.util.concurrent.CountDownLatch;

public class ShadowTraffic {
  public static void main(String[] args) throws InterruptedException {
    // Get a new client
    // NOTE: to set a different options, you can do like this:
    // ClientOptions.newBuilder().setRpcTimeout(5 * 1000).build();
    WorkflowClient workflowClient =
        WorkflowClient.newInstance(
            new WorkflowServiceTChannel(ClientOptions.defaultInstance()),
            WorkflowClientOptions.newBuilder().setDomain(DOMAIN).build());
    ShadowingOptions options =
        ShadowingOptions.newBuilder()
            .setDomain(DOMAIN)
            .setShadowMode(Mode.Normal)
            .setWorkflowTypes(Lists.newArrayList("GreetingWorkflow::getGreeting"))
            .setWorkflowStatuses(Lists.newArrayList(WorkflowStatus.OPEN, WorkflowStatus.CLOSED))
            .setExitCondition(new ExitCondition().setExpirationIntervalInSeconds(60))
            .build();

    ShadowingWorker shadowingWorker =
        new ShadowingWorker(
            workflowClient, "HelloActivity", WorkerOptions.defaultInstance(), options);
    shadowingWorker.registerWorkflowImplementationTypes(HelloActivity.GreetingWorkflowImpl.class);

    CountDownLatch latch = new CountDownLatch(1);
    // Execute a workflow waiting for it to complete.
    Runnable runnable =
        () -> {
          try {
            shadowingWorker.start();
          } catch (Exception e) {
            System.out.println("Failed to start shadowing workflow");
            System.out.println(e);
            latch.countDown();
          }
        };
    runnable.run();
    latch.await();
  }
}
