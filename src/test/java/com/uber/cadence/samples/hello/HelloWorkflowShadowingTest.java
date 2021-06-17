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
import static com.uber.cadence.samples.hello.HelloActivity.TASK_LIST;

import com.google.common.collect.Lists;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.IWorkflowService;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.shadower.ExitCondition;
import com.uber.cadence.shadower.Mode;
import com.uber.cadence.testing.WorkflowShadower;
import com.uber.cadence.worker.ShadowingOptions;
import com.uber.cadence.worker.WorkflowStatus;
import org.junit.Ignore;
import org.junit.Test;

public class HelloWorkflowShadowingTest {
  @Ignore
  @Test
  public void testShadowing() throws Throwable {
    IWorkflowService service = new WorkflowServiceTChannel(ClientOptions.defaultInstance());

    ShadowingOptions options =
        ShadowingOptions.newBuilder()
            .setDomain(DOMAIN)
            .setShadowMode(Mode.Normal)
            .setWorkflowTypes(Lists.newArrayList("GreetingWorkflow::getGreeting"))
            .setWorkflowStatuses(Lists.newArrayList(WorkflowStatus.OPEN, WorkflowStatus.CLOSED))
            .setExitCondition(new ExitCondition().setExpirationIntervalInSeconds(60))
            .build();
    WorkflowShadower shadower = new WorkflowShadower(service, options, TASK_LIST);
    shadower.registerWorkflowImplementationTypes(HelloActivity.GreetingWorkflowImpl.class);

    shadower.run();
  }
}
