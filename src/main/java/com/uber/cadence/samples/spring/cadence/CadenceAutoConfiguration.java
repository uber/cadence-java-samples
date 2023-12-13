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

package com.uber.cadence.samples.spring.cadence;

import static com.uber.cadence.samples.common.SampleConstants.DOMAIN;
import static com.uber.cadence.samples.spring.common.Constant.TASK_LIST;

import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.samples.spring.workflows.impl.ChildWorkflowImpl;
import com.uber.cadence.samples.spring.workflows.impl.HelloWorldWorkflowImpl;
import com.uber.cadence.samples.spring.workflows.impl.ParentWorkflowImpl;
import com.uber.cadence.samples.spring.workflows.impl.SignalWorkflowImpl;
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

    worker.registerWorkflowImplementationTypes(
        HelloWorldWorkflowImpl.class,
        SignalWorkflowImpl.class,
        ParentWorkflowImpl.class,
        ChildWorkflowImpl.class);
    factory.start();
  }
}
