package com.uber.cadence.samples.hello;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerFactory;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class HelloTest {
  private static final String TASK_LIST = "HelloTest";

  public interface TestWorkflow1 {

    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 10, taskList = TASK_LIST)
    String execute(String taskList);
  }

  public interface TestActivities {
    @ActivityMethod(name = "customActivity1")
    int activity1(int input);
  }

  private static class TestActivitiesImpl implements TestActivities {
    final List<String> invocations = Collections.synchronizedList(new ArrayList<>());

    @Override
    public int activity1(int a1) {
      invocations.add("activity1");
      return a1;
    }
  }

  public static class TestGetVersionAddedImpl implements TestWorkflow1 {

    @Override
    public String execute(String taskList) {

      //      int versionNew = Workflow.getVersion("cid2", Workflow.DEFAULT_VERSION, 1);
      int version = Workflow.getVersion("cid1", Workflow.DEFAULT_VERSION, 1);

      TestActivities testActivities =
          Workflow.newActivityStub(TestActivities.class, newActivityOptions1(taskList));
      return "hello" + testActivities.activity1(1);
    }

    private ActivityOptions newActivityOptions1(final String taskList) {
      return new ActivityOptions.Builder()
          .setTaskList(taskList)
          .setScheduleToCloseTimeout(Duration.ofSeconds(5))
          .setHeartbeatTimeout(Duration.ofSeconds(5))
          .setScheduleToStartTimeout(Duration.ofSeconds(5))
          .setStartToCloseTimeout(Duration.ofSeconds(10))
          .build();
    }
  }

  public static void main(String[] args) {
    final WorkflowServiceTChannel cadenceService =
        new WorkflowServiceTChannel(ClientOptions.defaultInstance());
    // Get a new client
    // NOTE: to set a different options, you can do like this:
    // ClientOptions.newBuilder().setRpcTimeout(5 * 1000).build();
    WorkflowClient workflowClient =
        WorkflowClient.newInstance(
            cadenceService, WorkflowClientOptions.newBuilder().setDomain(DOMAIN).build());
    // Get worker to poll the task list.
    WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
    Worker worker = factory.newWorker(TASK_LIST);
    // Workflows are stateful. So you need a type to create instances.
    worker.registerWorkflowImplementationTypes(TestGetVersionAddedImpl.class);
    // Activities are stateless and thread safe. So a shared instance is used.
    worker.registerActivitiesImplementations(new TestActivitiesImpl());
    // Start listening to the workflow and activity task lists.
    factory.start();

    String workflowID = UUID.randomUUID().toString();
    WorkflowOptions workflowOptions =
        new WorkflowOptions.Builder().setTaskList(TASK_LIST).setWorkflowId(workflowID).build();
    // Get a workflow stub using the same task list the worker uses.
    final TestWorkflow1 workflow =
        workflowClient.newWorkflowStub(TestWorkflow1.class, workflowOptions);
    final String res = workflow.execute(TASK_LIST);
    System.out.println(res);
  }
}
