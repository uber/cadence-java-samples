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

import com.uber.cadence.activity.ActivityMethod;
import com.uber.cadence.client.WorkflowClient;
import com.uber.cadence.client.WorkflowClientOptions;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.IWorkflowService;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerFactory;
import com.uber.cadence.worker.WorkerOptions;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import com.uber.m3.tally.Buckets;
import com.uber.m3.tally.Capabilities;
import com.uber.m3.tally.Counter;
import com.uber.m3.tally.Gauge;
import com.uber.m3.tally.Histogram;
import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.Scope;
import com.uber.m3.tally.ScopeCloseException;
import com.uber.m3.tally.Timer;
import com.uber.m3.tally.prometheus.PrometheusReporter;
import com.uber.m3.util.Duration;
import com.uber.m3.util.ImmutableMap;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Hello World Cadence workflow that executes a single activity with emitting metrics to Prometheus.
 * Check http://localhost:9098/ to see the reported metrics for scaping. Requires a local instance
 * the Cadence service to be running.
 */
public class HelloMetric {

  static final String TASK_LIST = "HelloActivity";

  /** Workflow interface has to have at least one method annotated with @WorkflowMethod. */
  public interface GreetingWorkflow {
    /** @return greeting string */
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 10, taskList = TASK_LIST)
    String getGreeting(String name);
  }

  /** Activity interface is just a POJI. */
  public interface GreetingActivities {
    @ActivityMethod(scheduleToCloseTimeoutSeconds = 2)
    String composeGreeting(String greeting, String name);
  }

  /** GreetingWorkflow implementation that calls GreetingsActivities#composeGreeting. */
  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    /**
     * Activity stub implements activity interface and proxies calls to it to Cadence activity
     * invocations. Because activities are reentrant, only a single stub can be used for multiple
     * activity invocations.
     */
    private final GreetingActivities activities =
        Workflow.newActivityStub(GreetingActivities.class);

    @Override
    public String getGreeting(String name) {
      // This is a blocking call that returns only after the activity has completed.
      return activities.composeGreeting("Hello", name);
    }
  }

  static class GreetingActivitiesImpl implements GreetingActivities {
    @Override
    public String composeGreeting(String greeting, String name) {
      return greeting + " " + name + "!";
    }
  }

  public static void main(String[] args) throws IOException {
    final ClientOptions clientOptions =
        ClientOptions.newBuilder().setMetricsScope(createMetricScope()).build();
    //    final ClientOptions clientOptions = ClientOptions.newBuilder().build();
    IWorkflowService service = new WorkflowServiceTChannel(clientOptions);
    final WorkflowClient workflowClient =
        WorkflowClient.newInstance(
            service, WorkflowClientOptions.newBuilder().setDomain(DOMAIN).build());
    // Start a worker that hosts both workflow and activity implementations.
    WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
    Worker worker = factory.newWorker(TASK_LIST, WorkerOptions.defaultInstance());
    // Workflows are stateful. So you need a type to create instances.
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
    // Activities are stateless and thread safe. So a shared instance is used.
    worker.registerActivitiesImplementations(new GreetingActivitiesImpl());
    // Start listening to the workflow and activity task lists.
    factory.start();

    // Get a workflow stub using the same task list the worker uses.
    GreetingWorkflow workflow = workflowClient.newWorkflowStub(GreetingWorkflow.class);
    // Execute a workflow waiting for it to complete.
    String greeting = workflow.getGreeting("World");
    System.out.println(greeting);
  }

  private static Scope createMetricScope() throws IOException {
    CollectorRegistry registry = CollectorRegistry.defaultRegistry;
    HTTPServer httpServer = new HTTPServer(new InetSocketAddress(9098), registry);
    PrometheusReporter reporter = PrometheusReporter.builder().registry(registry).build();
    // Make sure to set separator to "_" for Prometheus. Default is "." and doesn't work.
    Scope scope =
        new RootScopeBuilder().separator("_").reporter(reporter).reportEvery(Duration.ofSeconds(1));
    return new PrometheusScope(scope);
  }
}

/**
 * PrometheusScope will replace all "-"(dash) into "_"(underscore) so that it meets the requirement
 * in https://prometheus.io/docs/concepts/data_model/
 */
class PrometheusScope implements Scope {

  private Scope scope;

  PrometheusScope(Scope scope) {
    this.scope = scope;
  }

  private String fixName(String name) {
    String newName = name.replace('-', '_');
    return newName;
  }

  private Map<String, String> fixTags(Map<String, String> tags) {
    ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
    tags.forEach((key, value) -> builder.put(fixName(key), fixName(value)));
    return builder.build();
  }

  @Override
  public Counter counter(final String name) {
    String newName = fixName(name);
    return scope.counter(newName);
  }

  @Override
  public Gauge gauge(final String name) {
    String newName = fixName(name);
    return scope.gauge(newName);
  }

  @Override
  public Timer timer(final String name) {
    String newName = fixName(name);
    return scope.timer(newName);
  }

  @Override
  public Histogram histogram(final String name, final Buckets buckets) {
    String newName = fixName(name);
    return scope.histogram(newName, buckets);
  }

  @Override
  public Scope tagged(final Map<String, String> tags) {
    return new PrometheusScope(scope.tagged(fixTags(tags)));
  }

  @Override
  public Scope subScope(final String name) {
    String newName = fixName(name);
    return new PrometheusScope(scope.subScope(newName));
  }

  @Override
  public Capabilities capabilities() {
    return scope.capabilities();
  }

  @Override
  public void close() throws ScopeCloseException {
    scope.close();
  }
}
