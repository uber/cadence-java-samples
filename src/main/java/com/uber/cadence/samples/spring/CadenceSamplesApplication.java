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
