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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.uber.cadence.ListClosedWorkflowExecutionsRequest;
import com.uber.cadence.ListClosedWorkflowExecutionsResponse;
import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.WorkflowExecutionCloseStatus;
import com.uber.cadence.WorkflowExecutionFilter;
import com.uber.cadence.WorkflowExecutionInfo;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.testing.TestWorkflowEnvironment;
import com.uber.cadence.worker.Worker;
import java.time.Duration;
import org.apache.thrift.TException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Unit test for {@link HelloCron}. Doesn't use an external Cadence service. */
public class HelloCronTest {

  private TestWorkflowEnvironment testEnv;
  private Worker worker;
  private WorkflowClient workflowClient;

  @Before
  public void setUp() {
    testEnv = TestWorkflowEnvironment.newInstance();
    worker = testEnv.newWorker(HelloCron.TASK_LIST);
    worker.registerWorkflowImplementationTypes(HelloCron.CronWorkflowImpl.class);

    workflowClient = testEnv.newWorkflowClient();
  }

  @After
  public void tearDown() {
    testEnv.close();
  }

  @Test
  public void testCron() throws TException {
    worker.registerActivitiesImplementations(new HelloCron.GreetingActivitiesImpl());
    testEnv.start();

    // start cron workflow async
    HelloCron.CronWorkflow workflow = workflowClient.newWorkflowStub(HelloCron.CronWorkflow.class);
    WorkflowExecution execution = WorkflowClient.start(workflow::greetPeriodically, "World");
    assertEquals(HelloCron.CRON_WORKFLOW_ID, execution.getWorkflowId());

    // Validate that workflow was continued as new at least once.
    // Use TestWorkflowEnvironment.sleep to execute the unit test without really sleeping.
    testEnv.sleep(Duration.ofMinutes(1));
    ListClosedWorkflowExecutionsRequest request =
        new ListClosedWorkflowExecutionsRequest()
            .setDomain(testEnv.getDomain())
            .setExecutionFilter(
                new WorkflowExecutionFilter().setWorkflowId(HelloCron.CRON_WORKFLOW_ID));
    ListClosedWorkflowExecutionsResponse listResponse =
        testEnv.getWorkflowService().ListClosedWorkflowExecutions(request);
    assertTrue(listResponse.getExecutions().size() > 1);
    for (WorkflowExecutionInfo e : listResponse.getExecutions()) {
      assertEquals(WorkflowExecutionCloseStatus.CONTINUED_AS_NEW, e.getCloseStatus());
    }
  }
}
