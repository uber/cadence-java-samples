package com.uber.cadence.samples.spring;

import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.samples.spring.models.SampleMessage;
import com.uber.cadence.samples.spring.workflows.HelloWorldWorkflow;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class CadenceSamplesApplication {
  public static void main(String[] args) {
    SpringApplication.run(CadenceSamplesApplication.class, args);
  }

  // Example to start a workflow
  @Bean
  public CommandLineRunner startHelloWorkflow(ApplicationContext ctx) {
    return args -> {
      System.out.println("Start one synchronous HelloWorld workflow");

      WorkflowClient workflowClient = ctx.getBean(WorkflowClient.class);
      HelloWorldWorkflow stub = workflowClient.newWorkflowStub(HelloWorldWorkflow.class);
      stub.sayHello(new SampleMessage("hello"));

      System.out.println("Synchronous HelloWorld workflow finished");
      System.exit(SpringApplication.exit(ctx, () -> 0));
    };
  }
}
