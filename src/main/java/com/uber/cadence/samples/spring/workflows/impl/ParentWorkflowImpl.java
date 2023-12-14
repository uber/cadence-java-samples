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

package com.uber.cadence.samples.spring.workflows.impl;

import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.samples.spring.models.SampleMessage;
import com.uber.cadence.samples.spring.workflows.ChildWorkflow;
import com.uber.cadence.samples.spring.workflows.ParentWorkflow;
import com.uber.cadence.workflow.Async;
import com.uber.cadence.workflow.Promise;
import com.uber.cadence.workflow.Workflow;

public class ParentWorkflowImpl implements ParentWorkflow {
  @Override
  public String getGreetingInParent(SampleMessage sampleMessage) {
    // Workflows are stateful. So a new stub must be created for each new child.
    ChildWorkflow childWorkflow = Workflow.newChildWorkflowStub(ChildWorkflow.class);
    return childWorkflow.greetInChild(sampleMessage.GetMessage());
  }

  // This example allows parent workflow returns first and leave child workflow keep running.
  private String runChildWorkflowAsync(SampleMessage sampleMessage) {
    ChildWorkflow childWorkflow = Workflow.newChildWorkflowStub(ChildWorkflow.class);
    // non-blocking call that starts the child workflow
    Async.function(childWorkflow::greetInChild, sampleMessage.GetMessage());

    // Directly invoking child workflow method is a blocking call. It will block the thread
    // until child workflow completes. In this example, parent workflow will immediately
    // and keep child workflow running.
    Promise<WorkflowExecution> childWorkflowPromise = Workflow.getWorkflowExecution(childWorkflow);
    // block until child workflow started, otherwise parent workflow may completes first
    // before child workflow even starts.
    childWorkflowPromise.get();
    return "child can keep running, parent return first";
  }
}
