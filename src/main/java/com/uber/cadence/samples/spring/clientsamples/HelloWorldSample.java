package com.uber.cadence.samples.spring.clientsamples;

import com.uber.cadence.WorkflowExecution;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowOptions;
import com.uber.cadence.samples.spring.common.Constant;
import com.uber.cadence.samples.spring.models.SampleMessage;
import com.uber.cadence.samples.spring.workflows.HelloWorldWorkflow;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HelloWorldSample {
    Logger logger = LoggerFactory.getLogger(HelloWorldSample.class);
    @Autowired
    private WorkflowClient workflowClient;

    /**
     * startHelloWorldWorkflow starts a new HelloWorld workflow using Cadence client. It does not
     * retrieve the result of the workflow.
     *
     * @param inputMessage input message to start the workflow
     */
    public void startHelloWorldWorkflow(SampleMessage inputMessage) {
        logger.info("Start HelloWorldWorkflow via Cadence client");
        WorkflowOptions workflowOptions = this.getWorkflowOptions();
        HelloWorldWorkflow helloWorldWorkflow = this.workflowClient.newWorkflowStub(HelloWorldWorkflow.class, workflowOptions);

        WorkflowExecution workflowExecution = WorkflowClient.start(helloWorldWorkflow::sayHello, inputMessage);
        logger.info(String.format("Workflow ID: %s, Run ID: %s", workflowExecution.getWorkflowId(), workflowExecution.getRunId()));
    }

    private WorkflowOptions getWorkflowOptions() {
        return new WorkflowOptions.Builder().setTaskList(Constant.TASK_LIST).setExecutionStartToCloseTimeout(Duration.ofSeconds(60)).build();
    }
}
