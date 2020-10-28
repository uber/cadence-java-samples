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

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.testing.TestWorkflowEnvironment;
import com.uber.cadence.worker.Worker;
import java.time.Duration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CalculationWorkflowTest {

  private TestWorkflowEnvironment testEnv;
  private Worker worker;
  private WorkflowClient workflowClient;

  @Before
  public void setUp() {
    testEnv = TestWorkflowEnvironment.newInstance();
    worker = testEnv.newWorker(WorkflowWorker.DEFAULT_TASK_LIST);
    worker.registerWorkflowImplementationTypes(WorkflowMethodsImpl.class);

    workflowClient = testEnv.newWorkflowClient();
  }

  @After
  public void tearDown() {
    testEnv.close();
  }

  /** Unit test workflow logic using mocked activities. */
  @Test
  public void testCalculationHappyCase() {
    Activities activities = mock(Activities.class);
    when(activities.multiple(4L, 5L)).thenReturn(20L);
    when(activities.multiple(4L, 6L)).thenReturn(24L);
    when(activities.multiple(5L, 6L)).thenReturn(30L);
    worker.registerActivitiesImplementations(activities);

    testEnv.start();

    WorkflowMethods calculation = workflowClient.newWorkflowStub(WorkflowMethods.class);

    testEnv.registerDelayedCallback(Duration.ofSeconds(3), () -> calculation.factorForGn(3));

    long result = calculation.calculate(4L, 5L, 6L);

    // 20+24+30 + 1 = 75
    assertEquals(75L, result);
    verify(activities).multiple(eq(4L), eq(5L));
    verify(activities).multiple(eq(4L), eq(6L));
    verify(activities).multiple(eq(5L), eq(6L));
  }
}
