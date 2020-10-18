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
import com.uber.cadence.converter.DataConverter;
import com.uber.cadence.converter.DataConverterException;
import com.uber.cadence.converter.JsonDataConverter;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerOptions;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import java.lang.reflect.Type;

/**
 * Hello World Cadence workflow that executes a single activity. Requires a local instance the
 * Cadence service to be running.
 */
public class HelloDataConverter {

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
    MyStruct composeGreeting(String greeting, String name);
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
      MyStruct st = activities.composeGreeting("Hello", name);
      return st.str + "....." + st.num;
    }
  }

  public static class MyStruct {
    private int num;
    private String str;

    public MyStruct(int num, String str) {
      this.num = num;
      this.str = str;
    }

    public String toString() {
      return str + " and " + num;
    }
  }

  public static class MyStructConverter implements DataConverter {
    private static final DataConverter jsonConverter = JsonDataConverter.getInstance();

    @Override
    public byte[] toData(final Object... value) throws DataConverterException {
      if (value.length == 1 && value[0] instanceof MyStruct) {
        MyStruct st = (MyStruct) value[0];
        return (st.num + "#" + st.str).getBytes();
      }
      return jsonConverter.toData(value);
    }

    @Override
    public <T> T fromData(final byte[] content, final Class<T> valueClass, final Type valueType)
        throws DataConverterException {
      if (valueType.getTypeName().equals(MyStruct.class.getTypeName())) {
        String s = new String(content);
        String[] ss = s.split("#");
        int num = Integer.parseInt(ss[0]);
        MyStruct st = new MyStruct(num, ss[1]);
        return (T) st;
      } else {
        return jsonConverter.fromData(content, valueClass, valueType);
      }
    }

    @Override
    public Object[] fromDataArray(final byte[] content, final Type... valueType)
        throws DataConverterException {
      return jsonConverter.fromDataArray(content, valueType);
    }
  }

  static class GreetingActivitiesImpl implements GreetingActivities {
    @Override
    public MyStruct composeGreeting(String greeting, String name) {
      return new MyStruct(123, greeting + "," + name);
    }
  }

  public static void main(String[] args) {
    // Start a worker that hosts both workflow and activity implementations.
    Worker.Factory factory = new Worker.Factory(DOMAIN);
    Worker worker =
        factory.newWorker(
            TASK_LIST,
            new WorkerOptions.Builder().setDataConverter(new MyStructConverter()).build());
    // Workflows are stateful. So you need a type to create instances.
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
    // Activities are stateless and thread safe. So a shared instance is used.
    worker.registerActivitiesImplementations(new GreetingActivitiesImpl());
    // Start listening to the workflow and activity task lists.
    factory.start();

    // Start a workflow execution. Usually this is done from another program.
    WorkflowClient workflowClient = WorkflowClient.newInstance(DOMAIN);
    // Get a workflow stub using the same task list the worker uses.
    GreetingWorkflow workflow = workflowClient.newWorkflowStub(GreetingWorkflow.class);
    // Execute a workflow waiting for it to complete.
    String greeting = workflow.getGreeting("World");
    System.out.println(greeting);
    System.exit(0);
  }
}
