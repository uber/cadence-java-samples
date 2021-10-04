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
import com.uber.cadence.converter.DataConverter;
import com.uber.cadence.converter.DataConverterException;
import com.uber.cadence.converter.JsonDataConverter;
import com.uber.cadence.serviceclient.ClientOptions;
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerFactory;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import java.lang.reflect.Type;
import java.nio.charset.Charset;

/**
 * HelloDataConverter is a sample to how to implement a dataConverter to convert some objects that
 * you want to use a different way to serialize/deserialize
 */
public class HelloDataConverter {

  /**
   * MyStruct is a sample class that you want to use a different to serialize/deserialize it. In
   * real-world you can put anything like Avro classes in it
   */
  public static class MyStruct {
    public int num;
    public String str;

    public MyStruct(int num, String str) {
      this.num = num;
      this.str = str;
    }

    public static MyStruct fromBytes(byte[] content) {
      String s = new String(content, Charset.defaultCharset());
      String[] ss = s.split("#");
      int num = Integer.parseInt(ss[0]);
      return new MyStruct(num, ss[1]);
    }

    public byte[] toBytes() {
      return (this.num + "#" + this.str).getBytes(Charset.defaultCharset());
    }

    @Override
    public String toString() {
      return str + " and " + num;
    }
  }

  static final String TASK_LIST = "HelloActivity";

  /** Workflow interface has to have at least one method annotated with @WorkflowMethod. */
  public interface GreetingWorkflow {
    /** @return greeting string */
    @WorkflowMethod(executionStartToCloseTimeoutSeconds = 10, taskList = TASK_LIST)
    String getGreeting(MyStruct st);
  }

  /** Activity interface is just a POJI. */
  public interface GreetingActivities {
    @ActivityMethod(scheduleToCloseTimeoutSeconds = 2)
    MyStruct composeGreeting(Integer num, String str);
  }

  /** GreetingWorkflow implementation that calls GreetingsActivities#composeGreeting. */
  public static class GreetingWorkflowImpl implements GreetingWorkflow {

    private final GreetingActivities activities =
        Workflow.newActivityStub(GreetingActivities.class);

    @Override
    public String getGreeting(MyStruct st) {
      // This is a blocking call that returns only after the activity has completed.
      st = activities.composeGreeting(st.num, st.str);
      return st.toString();
    }
  }

  public static class MyStructConverter implements DataConverter {

    private static final DataConverter cadenceDefaultDataConverter =
        JsonDataConverter.getInstance();

    /**
     * * toData is converting input/output parameter of workflow/activity, exception, internal
     * classes(local activity, heartbeat etc) into binary.
     *
     * @param values
     * @return
     * @throws DataConverterException
     */
    @Override
    public byte[] toData(final Object... values) throws DataConverterException {
      if (values == null || values.length == 0) {
        return null;
      }

      if (values.length == 1 && values[0] instanceof MyStruct) {
        // NOTE: toData can be used to converting multiple input parameter as well.
        // but here we assume that when passing MyStruct as input, we always use one parameter.
        // In your real-world case, you can change this to support multiple(values.length > 1) if
        // needed
        MyStruct st = (MyStruct) values[0];
        return st.toBytes();
      }

      // fallback to cadenceDefaultDataConverter to keep backward compatible
      return cadenceDefaultDataConverter.toData(values);
    }

    /**
     * * fromData is converting binary back to a single object. It's only being used for output of
     * workflow/activity, exception, internal classes(local activity, heartbeat etc)
     *
     * @param content
     * @param valueClass
     * @param valueType
     * @param <T>
     * @return
     * @throws DataConverterException
     */
    @Override
    public <T> T fromData(final byte[] content, final Class<T> valueClass, final Type valueType)
        throws DataConverterException {
      if (valueType.getTypeName().equals(MyStruct.class.getTypeName())) {
        return (T) MyStruct.fromBytes(content);
      } else {
        return cadenceDefaultDataConverter.fromData(content, valueClass, valueType);
      }
    }

    /*
     * Used to deserialize a byte[] into one-to-many different value types. The
     * primary use case for this is the deserialization of Workflow / Activity arguments for worker to execute workflow/activity
     */
    @Override
    public Object[] fromDataArray(final byte[] content, final Type... valueTypes)
        throws DataConverterException {
      if ((content == null) || (content.length == 0)) {
        Object[] result = new Object[valueTypes.length];
        return result;
      }
      if (valueTypes.length == 1) {
        final Object result;
        final Type valueType = valueTypes[0];
        if (valueType.getTypeName().equals(MyStruct.class.getTypeName())) {
          result = MyStruct.fromBytes(content);
          return new Object[] {result};
        }
      }

      return cadenceDefaultDataConverter.fromDataArray(content, valueTypes);
    }
  }

  static class GreetingActivitiesImpl implements GreetingActivities {
    @Override
    public MyStruct composeGreeting(Integer num, String str) {
      return new MyStruct(num * 2, str + "::" + str);
    }
  }

  public static void main(String[] args) {
    final MyStructConverter dc = new MyStructConverter();
    // Get a new client
    // NOTE: to set a different options, you can do like this:
    // ClientOptions.newBuilder().setRpcTimeout(5 * 1000).build();
    WorkflowClient workflowClient =
        WorkflowClient.newInstance(
            new WorkflowServiceTChannel(ClientOptions.defaultInstance()),
            WorkflowClientOptions.newBuilder().setDataConverter(dc).setDomain(DOMAIN).build());
    // Get worker to poll the task list.
    WorkerFactory factory = WorkerFactory.newInstance(workflowClient);
    Worker worker = factory.newWorker(TASK_LIST);
    // Workflows are stateful. So you need a type to create instances.
    worker.registerWorkflowImplementationTypes(GreetingWorkflowImpl.class);
    // Activities are stateless and thread safe. So a shared instance is used.
    worker.registerActivitiesImplementations(new GreetingActivitiesImpl());
    // Start listening to the workflow and activity task lists.
    factory.start();

    // Get a workflow stub using the same task list the worker uses.
    GreetingWorkflow workflow = workflowClient.newWorkflowStub(GreetingWorkflow.class);
    // Execute a workflow waiting for it to complete.
    String greeting = workflow.getGreeting(new MyStruct(100, "Hello"));
    System.out.println(greeting);
    System.exit(0);
  }
}
