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

import com.uber.cadence.samples.hello.HelloPeriodic;
import com.uber.cadence.testing.WorkflowReplayer;
import org.junit.Test;

public class HelloPeriodicReplayTest {

  // continue-as-new case for replayer tests: Passing
  @Test
  public void testReplay_continueAsNew() throws Exception {
    WorkflowReplayer.replayWorkflowExecutionFromResource(
        "replaytests/HelloPeriodic.json", HelloPeriodic.GreetingWorkflowImpl.class);
  }

  // Continue as new case: change in sleep timer compared to original workflow definition. It should
  // fail. BUT it is currently passing.
  @Test
  public void testReplay_continueAsNew_timerChange() throws Exception {
    WorkflowReplayer.replayWorkflowExecutionFromResource(
        "replaytests/HelloPeriodic.json",
        HelloPeriodic_sleepTimerChange.GreetingWorkflowImpl.class);
  }
}
