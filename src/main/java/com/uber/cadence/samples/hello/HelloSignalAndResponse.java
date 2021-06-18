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

import com.uber.cadence.DescribeWorkflowExecutionRequest;
import com.uber.cadence.DescribeWorkflowExecutionResponse;
import com.uber.cadence.EventType;
import com.uber.cadence.GetWorkflowExecutionHistoryRequest;
import com.uber.cadence.GetWorkflowExecutionHistoryResponse;
import com.uber.cadence.HistoryEvent;
import com.uber.cadence.SearchAttributes;
import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.converter.DataConverter;
import com.uber.cadence.converter.JsonDataConverter;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerFactory;
import com.uber.cadence.workflow.SignalMethod;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import com.uber.cadence.workflow.WorkflowUtils;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.RandomStringUtils;

/**
 * Demonstrates signalling a workflow, and wait until it's applied and get a response. This should
 * be much performant(lower latency) than using signal+query approach. Requires a Cadence server to
 * be running.
 */
@SuppressWarnings("ALL")
public class HelloSignalAndResponse {

  static final String TASK_LIST = "HelloSignal";

  /** Workflow interface must have a method annotated with @WorkflowMethod. */
  public interface GreetingWorkflow {
    /**
     * @return list of greeting strings that were received through the receiveName Method. This
     *     method will block until the number of greetings specified are received.
     */
    @WorkflowMethod
    List<String> getGreetings();

    /** Receives name through an external signal. */
    @SignalMethod
    void receiveName(String name);

    @SignalMethod
    void exit();
  }

  /** GreetingWorkflow implementation that returns a greeting. */
  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    List<String> messageQueue = new ArrayList<>(10);
    boolean exit = false;

    @Override
    public List<String> getGreetings() {
      List<String> receivedMessages = new ArrayList<>(10);

      while (true) {
        Workflow.await(() -> !messageQueue.isEmpty() || exit);
        if (messageQueue.isEmpty() && exit) {
          return receivedMessages;
        }
        String message = messageQueue.remove(0);
        receivedMessages.add(message);
      }
    }

    @Override
    public void receiveName(String name) {
      Map<String, Object> upsertedMap = new HashMap<>();
      // Because we are going to get the response after signal, make sure first thing to do in the
      // signal method is to upsert search attribute with the response.
      // Use CustomKeywordField for response, in real code you may use other fields
      // If there are multiple signals processed in paralell, consider returning a map of message
      // to each status/result so that they won't overwrite each other
      upsertedMap.put("CustomKeywordField", name + ":" + "No_Error");
      Workflow.upsertSearchAttributes(upsertedMap);

      messageQueue.add(name);
    }

    @Override
    public void exit() {
      exit = true;
    }
  }

  public static void main(String[] args) throws Exception {
    // Get a new client
    // NOTE: to set a different options, you can do like this:
    // ClientOptions.newBuilder().setRpcTimeout(5 * 1000).build();
    WorkflowClient workflowClient =
        WorkflowClient.newInstance(
            new WorkflowServiceTChannel(ClientOptions.defaultInstance()),
            WorkflowClientOptions.newBuilder().setDomain(DOMAIN).build());
    // Get worker to poll the task list.
    WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
    Worker worker = factory.newWorker(TASK_LIST);
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
    factory.start();

    // In a real application use a business ID like customer ID or order ID
    String workflowId = RandomStringUtils.randomAlphabetic(10);

    // Start a workflow execution. Usually this is done from another program.
    // Get a workflow stub using the same task list the worker uses.
    // The newly started workflow is going to have the workflowId generated above.
    WorkflowOptions workflowOptions =
        new WorkflowOptions.Builder()
            .setTaskList(TASK_LIST)
            .setExecutionStartToCloseTimeout(Duration.ofSeconds(30))
            .setWorkflowId(workflowId)
            .build();
    GreetingWorkflow workflow =
        workflowClient.newWorkflowStub(GreetingWorkflow.class, workflowOptions);
    // Start workflow asynchronously to not use another thread to signal.
    WorkflowClient.start(workflow::getGreetings);
    // After start for getGreeting returns, the workflow is guaranteed to be started.
    // So we can send a signal to it using the workflow stub.
    // This workflow keeps receiving signals until exit is called
    String signal = "World";

    final signalWaitResult result =
        signalAndWait(
            workflowClient,
            workflowId,
            "",
            () -> {
              workflow.receiveName(signal); // sends receiveName signal
            },
            JsonDataConverter.getInstance(),
            "GreetingWorkflow::receiveName",
            signal);

    System.out.printf(
        "result: isReceived: %b, isProccessed: %b, isRunning: %b, runID: %s \n",
        result.isSignalReceived, result.isSignalProcessed, result.isWorkflowRunning, result.runId);
    if (result.isSignalProcessed) {
      // Get results from search attribute `CustomKeywordField`
      WorkflowExecution execution = new WorkflowExecution();
      execution.setWorkflowId(workflowId);
      execution.setRunId(
          result.runId); // make sure to sure the same runID in case the current run changes
      DescribeWorkflowExecutionRequest request = new DescribeWorkflowExecutionRequest();
      request.setDomain(DOMAIN);
      request.setExecution(execution);
      DescribeWorkflowExecutionResponse resp =
          workflowClient.getService().DescribeWorkflowExecution(request);
      SearchAttributes searchAttributes = resp.workflowExecutionInfo.getSearchAttributes();
      String keyword =
          WorkflowUtils.getValueFromSearchAttributes(
              searchAttributes, "CustomKeywordField", String.class);
      System.out.printf("Signal result is: %s\n", keyword);
    } else {
      System.out.printf("No result because signal was not processed");
    }
  }

  /**
   * This signal helper not only sends signal to workflow, but also wait & return after the signal
   * has been applied. It will wait until the first decision task completed after the signal shows
   * by in the history(meaning recieved). It compare signalName and the signal method argument to
   * determine if that's the signal you sent. TODO: if this feature is proved to be useful, we
   * should move to client or server implementation
   *
   * <p>NOTE that the signalOperation should not use requestedID for deduping. (However, this
   * requestedID is not exposed in Java client yet anyway). Because deduping means a noop and return
   * success, then this helper will wait for signal forever.
   *
   * @param workflowClient
   * @param workflowId
   * @param runId
   * @param signalOperation the operation that will send signal
   * @param dataConverter for converting signalArgs to compare and determine if the signal has
   *     received
   * @param signalName for comparing the signalName received in the history
   * @param signalArgs for comparing the signalData(converted by dataConverter) received in the
   *     history
   */
  private static signalWaitResult signalAndWait(
      WorkflowClient workflowClient,
      String workflowId,
      String runId,
      Runnable signalOperation,
      DataConverter dataConverter,
      String signalName,
      Object... signalArgs)
      throws Exception {
    final byte[] signalData = dataConverter.toData(signalArgs);
    signalWaitResult result = new signalWaitResult();

    // get the current eventID
    WorkflowExecution execution = new WorkflowExecution();
    execution.setWorkflowId(workflowId);
    execution.setRunId(runId);
    DescribeWorkflowExecutionRequest request = new DescribeWorkflowExecutionRequest();
    request.setDomain(DOMAIN);
    request.setExecution(execution);
    DescribeWorkflowExecutionResponse resp =
        workflowClient.getService().DescribeWorkflowExecution(request);
    long currentEventId = resp.workflowExecutionInfo.historyLength;
    result.runId = resp.workflowExecutionInfo.execution.runId;

    // send signal
    signalOperation.run();

    // Poll history starting from currentEventId,
    // then wait until the signal is received, and then wait until it's
    // processed(decisionTaskCompleted)
    result.isSignalReceived = false;
    result.isSignalProcessed = false;
    result.isWorkflowRunning = !resp.workflowExecutionInfo.isSetCloseStatus();

    while (result.isWorkflowRunning && !result.isSignalProcessed) {
      GetWorkflowExecutionHistoryRequest historyReq = new GetWorkflowExecutionHistoryRequest();
      historyReq.setDomain(DOMAIN);
      historyReq.setExecution(execution);
      historyReq.setWaitForNewEvent(true);
      String token =
          String.format(
              "{\"RunID\":\"%s\",\"FirstEventID\":0,\"NextEventID\":%d,\"IsWorkflowRunning\":true,\"PersistenceToken\":null,\"TransientDecision\":null,\"BranchToken\":null}",
              result.runId, currentEventId + 1);
      historyReq.setNextPageToken(token.getBytes(Charset.defaultCharset()));
      final GetWorkflowExecutionHistoryResponse historyResp =
          workflowClient.getService().GetWorkflowExecutionHistory(historyReq);
      token = new String(historyResp.getNextPageToken(), Charset.defaultCharset());
      result.isWorkflowRunning = token.contains("\"IsWorkflowRunning\":true");

      for (HistoryEvent event : historyResp.history.events) {
        if (!result.isSignalReceived) {
          // wait for signal received
          if (event.getEventType() == EventType.WorkflowExecutionSignaled) {
            final byte[] eventSignalData =
                event.getWorkflowExecutionSignaledEventAttributes().getInput();
            final String eventSignalName =
                event.getWorkflowExecutionSignaledEventAttributes().getSignalName();
            if (Arrays.equals(eventSignalData, signalData) && eventSignalName.equals(signalName)) {
              result.isSignalReceived = true;
            } else {
              if (Arrays.equals(eventSignalData, signalData)
                  || eventSignalName.equals(signalName)) {
                System.out.println(
                    "[WARN] either signal event data or signalName doesn't match, is the signalArgs and signalName correct?");
              }
            }
          }
        } else {
          // signal is received, now wait for first decisioin task complete
          if (event.getEventType() == EventType.DecisionTaskCompleted) {
            result.isSignalProcessed = true;
            break;
          }
        }
        currentEventId = event.getEventId();
      }
    }
    return result;
  }

  private static class signalWaitResult {
    public boolean isSignalProcessed;
    public boolean isSignalReceived;
    public boolean isWorkflowRunning;
    public String runId;
  }
}
