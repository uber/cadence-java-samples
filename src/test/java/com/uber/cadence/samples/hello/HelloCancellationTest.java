package com.uber.cadence.samples.hello;

import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.client.WorkflowStub;
import com.uber.cadence.testing.TestWorkflowEnvironment;
import com.uber.cadence.worker.Worker;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.time.Duration;
import java.util.concurrent.CancellationException;

import static org.junit.Assert.assertEquals;

public class HelloCancellationTest {
  /** Prints a history of the workflow under test in case of a test failure. */
  @Rule
  public TestWatcher watchman =
      new TestWatcher() {
        @Override
        protected void failed(Throwable e, Description description) {
          if (testEnv != null) {
            System.err.println(testEnv.getDiagnostics());
            testEnv.close();
          }
        }
      };

  private TestWorkflowEnvironment testEnv;
  private Worker worker;
  private WorkflowClient workflowClient;
  private HelloCancellation.GreetingActivitiesImpl activities;

  @Before
  public void setUp() {
    testEnv = TestWorkflowEnvironment.newInstance();

    worker = testEnv.newWorker(HelloCancellation.TASK_LIST);
    worker.registerWorkflowImplementationTypes(HelloCancellation.GreetingWorkflowImpl.class);

    // A shared instance is used to show activity invocations.
    activities = new HelloCancellation.GreetingActivitiesImpl();
    worker.registerActivitiesImplementations(activities);
    testEnv.start();

    workflowClient = testEnv.newWorkflowClient();
  }

  @After
  public void tearDown() {
    testEnv.close();
  }

  @Test
  public void testCancellation() {
    // Get a workflow stub using the same task list the worker uses.
    WorkflowOptions workflowOptions =
        new WorkflowOptions.Builder()
            .setTaskList(HelloCancellation.TASK_LIST)
            .setExecutionStartToCloseTimeout(Duration.ofDays(30))
            .build();
    WorkflowStub client =
        workflowClient.newUntypedWorkflowStub("GreetingWorkflow::getGreeting", workflowOptions);

    // Start workflow asynchronously to not use another thread to signal.
    client.start("World");

    // Issue cancellation request. This will trigger a CancellationException on the workflow which
    // can be ignored since it's expected
    client.cancel();

    try {
      client.getResult(String.class);
    } catch (CancellationException ignored) {
    }

    // The cancellation request will cause the workflow to trigger the "sayGoodbye" activity.
    assertEquals(1, activities.getInvocations().size());
    assertEquals("sayGoodbye", activities.getInvocations().get(0));
  }
}
