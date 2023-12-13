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

package com.uber.cadence.samples.clientsamples;

import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.samples.spring.common.Constant;
import com.uber.cadence.samples.spring.models.SampleMessage;
import com.uber.cadence.samples.spring.workflows.SignalWorkflow;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.Scanner;

// This class is a placeholder. Run the two binaries below to play with the
// signal workflow sample.
public class SignalSample {}

// This binary starts a signal workflow and store the workflow ID into a local file
// for future usage. It must be run first.
class SignalWorkflowStarter {
  public static void main(String[] args) throws IOException {
    WorkflowClient workflowClient = CadenceUtil.getWorkflowClient();
    WorkflowOptions workflowOptions =
        new WorkflowOptions.Builder()
            .setExecutionStartToCloseTimeout(Duration.ofSeconds(60))
            .setTaskList(Constant.TASK_LIST)
            .build();

    // start a signal workflow using cadence client
    SignalWorkflow signalWorkflow =
        workflowClient.newWorkflowStub(SignalWorkflow.class, workflowOptions);
    WorkflowExecution execution =
        WorkflowClient.start(signalWorkflow::getGreeting, new SampleMessage("Uber"));
    String workflowID = execution.getWorkflowId();
    System.out.printf("WorkflowID: %s, RunID: %s", workflowID, execution.getRunId());

    // store workflow ID for future use into a file. In prod, it may be persisted in a database
    File workflowIDFile = new File("workflow_id.txt");
    // always delete the file if it exists and create a new one.
    workflowIDFile.delete();
    workflowIDFile.createNewFile();
    FileWriter writer = new FileWriter(workflowIDFile);
    writer.write(workflowID);
    writer.close();
  }
}

// This binary retrieves the stored signal workflow ID from local file created by
// SignalWorkflowStarter and send a signal to this workflow.
class SignalSender {
  public static void main(String[] args) throws FileNotFoundException {
    // Get stored workflowID
    File workflowIDFile = new File("workflow_id.txt");
    Scanner scanner = new Scanner(workflowIDFile);
    String workflowID = scanner.nextLine();
    scanner.close();

    // create a new stub using the retrieved workflowID
    WorkflowClient workflowClient = CadenceUtil.getWorkflowClient();
    // To send a signal, only workflowID is needed.
    SignalWorkflow signalWorkflow =
        workflowClient.newWorkflowStub(SignalWorkflow.class, workflowID);
    // send signal to the workflow
    signalWorkflow.waitForGreeting("Hello");

    // cancel workflow directly via a signal
    // signalWorkflow.cancel();
  }
}
