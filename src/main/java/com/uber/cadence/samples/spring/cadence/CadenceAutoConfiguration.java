package com.uber.cadence.samples.spring.cadence;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;
import static com.uber.cadence.samples.spring.common.Constant.TASK_LIST;

import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.samples.spring.workflows.HelloWorldWorkflowImpl;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

@Configuration
public class CadenceAutoConfiguration {
  @Bean
  public WorkflowClient workflowClient() {
    return WorkflowClient.newInstance(
        new WorkflowServiceTChannel(ClientOptions.defaultInstance()),
        WorkflowClientOptions.newBuilder().setDomain(DOMAIN).build());
  }

  @EventListener(ApplicationStartedEvent.class)
  public void startWorker(ApplicationStartedEvent event) {
    System.out.println("Starting workers");

    ApplicationContext context = event.getApplicationContext();
    WorkflowClient workflowClient = context.getBean(WorkflowClient.class);
    WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
    Worker worker = factory.newWorker(TASK_LIST);
    worker.registerWorkflowImplementationTypes(HelloWorldWorkflowImpl.class);
    factory.start();
  }
}
