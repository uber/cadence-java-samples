package com.uber.cadence.samples.shadowing;

import com.google.common.collect.Lists;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.samples.hello.HelloActivity;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.shadower.ExitCondition;
import com.uber.cadence.shadower.Mode;
import com.uber.cadence.worker.ShadowingOptions;
import com.uber.cadence.worker.ShadowingWorker;
import com.uber.cadence.worker.WorkerOptions;
import com.uber.cadence.worker.WorkflowStatus;

import java.util.concurrent.CountDownLatch;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;

public class ShadowTraffic {
    public static void main(String[] args) throws InterruptedException {
        // Get a new client
        // NOTE: to set a different options, you can do like this:
        // ClientOptions.newBuilder().setRpcTimeout(5 * 1000).build();
        WorkflowClient workflowClient =
                WorkflowClient.newInstance(
                        new WorkflowServiceTChannel(ClientOptions.defaultInstance()),
                        WorkflowClientOptions.newBuilder().setDomain(DOMAIN).build());
        ShadowingOptions options = ShadowingOptions
                .newBuilder()
                .setDomain(DOMAIN)
                .setShadowMode(Mode.Normal)
                .setWorkflowTypes(Lists.newArrayList("GreetingWorkflow::getGreeting"))
                .setWorkflowStatuses(Lists.newArrayList(WorkflowStatus.OPEN, WorkflowStatus.CLOSED))
                .setExitCondition(new ExitCondition().setExpirationIntervalInSeconds(60))
                .build();

        ShadowingWorker shadowingWorker = new ShadowingWorker(
                workflowClient,
                "HelloActivity",
                WorkerOptions.defaultInstance(),
                options);
        shadowingWorker.registerWorkflowImplementationTypes(HelloActivity.GreetingWorkflowImpl.class);

        CountDownLatch latch = new CountDownLatch(1);
        // Execute a workflow waiting for it to complete.
        Runnable runnable = () -> {
            try {
                shadowingWorker.start();
            } catch (Exception e) {
                System.out.println("Failed to start shadowing workflow");
                System.out.println(e);
                latch.countDown();
            }
        };
        runnable.run();
        latch.await();
    }
}
