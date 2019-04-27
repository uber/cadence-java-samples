package com.uber.cadence.samples.hello;

import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.workflow.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

/**
 * Demonstrates an asynchronous workflow with signal updates for workflow completion. Requires a
 * local instance of Cadence server to be running. 1. Start Workflow (asynchronous) 2. Wait 3.
 * Signal-1 => Do Activity-1 4. Wait 5. Signal-2 => Do Activity-2 6. Query Workflow 7. End Workflow
 */
public class HelloAsyncWorkflowWithSignalCompletion {

  static final String TASK_LIST = "HelloAsyncWorkflowWithSignalCompletion";

  /** Workflow interface must have a method annotated with @WorkflowMethod. */
  public interface GreetingWorkflow {
    /**
     * @return list of greeting strings that were received through the waitForNameMethod. This
     *     method will block until the number of greetings specified are received.
     */
    @WorkflowMethod
    List<String> getGreetings();

    /** Signal1 - Receives signal1 through an external signal. */
    @SignalMethod
    void waitForSignal1(String signal1);

    /** Signal2 - Receives signal2 through an external signal. */
    @SignalMethod
    void waitForSignal2(String signal2);

    @QueryMethod
    String queryResults();
  }

  /** Activity interface is just a POJI. * */
  public interface GreetingActivity1 {
    @ActivityMethod(scheduleToCloseTimeoutSeconds = 10)
    String composeGreeting1(String greeting, String name);
  }

  /** Activity interface is just a POJI. * */
  public interface GreetingActivity2 {
    @ActivityMethod(scheduleToCloseTimeoutSeconds = 10)
    String composeGreeting2(String greeting, String name);
  }

  /** GreetingWorkflow implementation that returns a greetings as per the designed workflow */
  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    List<String> messageQueue = new ArrayList<>(2);
    //    boolean exit = false;
    CompletablePromise<String> signal1 = Workflow.newPromise();
    CompletablePromise<String> signal2 = Workflow.newPromise();

    /**
     * Activity stub implements activity interface and proxies calls to it to Cadence activity
     * invocations. Because activities are reentrant, only a single stub can be used for multiple
     * activity invocations.
     */
    private final GreetingActivity1 activity1 = Workflow.newActivityStub(GreetingActivity1.class);

    private final GreetingActivity2 activity2 = Workflow.newActivityStub(GreetingActivity2.class);

    @Override
    public List<String> getGreetings() {
      List<String> receivedMessages = new ArrayList<>(5);

      System.out.println("Awaiting Signal1");
      if (!signal1.get().isEmpty()) {
        String activity1Result = activity1.composeGreeting1("Hello", "Activity-1");
        receivedMessages.add(activity1Result);
      }

      System.out.println("Awaiting Signal2");
      if (!signal2.get().isEmpty()) {
        String activity2Result = activity2.composeGreeting2("Hello", "Activity-2");
        receivedMessages.add(activity2Result);
      }

      messageQueue.addAll(receivedMessages);
      return receivedMessages;
    }

    @Override
    public void waitForSignal1(String signal) {
      signal1.complete(signal);
    }

    @Override
    public void waitForSignal2(String signal) {
      signal2.complete(signal);
    }

    @Override
    public String queryResults() {
      return messageQueue.toString();
    }
  }

  static class GreetingActivity1Impl implements GreetingActivity1 {

    @Override
    public String composeGreeting1(String greeting, String name) {
      System.out.println("==Activity-1 executed==");
      return greeting + " " + name + "!";
    }
  }

  static class GreetingActivity2Impl implements GreetingActivity2 {

    @Override
    public String composeGreeting2(String greeting, String name) {
      System.out.println("==Activity-2 executed==");
      return greeting + " " + name + "!";
    }
  }

  public static void main(String[] args) throws Exception {

    // Start a worker that hosts both workflow and activity implementations.
    Worker.Factory factory = new Worker.Factory(DOMAIN);
    Worker worker = factory.newWorker(TASK_LIST);
    // Workflows are stateful. So you need a type to create instances.
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
    // Activities are stateless and thread safe. So a shared instance is used.
    worker.registerActivitiesImplementations(
        new GreetingActivity1Impl(), new GreetingActivity2Impl());
    // Start listening to the workflow and activity task lists.
    factory.start();

    // Start a workflow execution. Usually this is done from another program.
    WorkflowClient workflowClient = WorkflowClient.newInstance(DOMAIN);
    // Get a workflow stub using the same task list the worker uses.
    WorkflowOptions workflowOptions =
        new WorkflowOptions.Builder()
            .setTaskList(TASK_LIST)
            .setExecutionStartToCloseTimeout(Duration.ofSeconds(30))
            .build();
    GreetingWorkflow workflow =
        workflowClient.newWorkflowStub(GreetingWorkflow.class, workflowOptions);
    // Start workflow asynchronously to not use another thread to query.
    WorkflowClient.start(workflow::getGreetings);
    Thread.sleep(1000);
    // After start for getGreeting returns, the workflow is guaranteed to be started.
    // So we can send a signal to it using workflow stub.
    // This workflow will execute different Activities based on the Signal received
    workflow.waitForSignal1("START_ACTIVITY1");
    // Note that inside a workflow only WorkflowThread.sleep is allowed. Outside
    // WorkflowThread.sleep is not allowed.
    Thread.sleep(1500);
    workflow.waitForSignal2("START_ACTIVITY2");
    Thread.sleep(500);
    // System.out.println(workflow.queryResults());
    // Calling synchronous getGreeting after workflow has started reconnects to the existing
    // workflow and blocks until a result is available. Note that this behavior assumes that
    // WorkflowOptions are not configured with WorkflowIdReusePolicy.AllowDuplicate. In that case
    // the call would fail with WorkflowExecutionAlreadyStartedException.
    List<String> greetings = workflow.getGreetings();
    System.out.println(greetings);
    System.exit(0);
  }
}
