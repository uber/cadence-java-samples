package com.uber.cadence.samples.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CadenceSamplesApplication {
  public static void main(String[] args) {
    SpringApplication.run(CadenceSamplesApplication.class, args);
  }

  // Example to start a workflow
  //  @Bean
  //  public CommandLineRunner startHelloWorkflow(ApplicationContext ctx) {
  //    return args -> {
  //      System.out.println("Start one synchronous HelloWorld workflow");
  //
  //      WorkflowClient workflowClient = ctx.getBean(WorkflowClient.class);
  //      HelloWorldWorkflow stub = workflowClient.newWorkflowStub(HelloWorldWorkflow.class);
  //      stub.sayHello(new SampleMessage("hello"));
  //
  //      System.out.println("Synchronous HelloWorld workflow finished");
  //      System.exit(SpringApplication.exit(ctx, () -> 0));
  //    };
  //  }
}
