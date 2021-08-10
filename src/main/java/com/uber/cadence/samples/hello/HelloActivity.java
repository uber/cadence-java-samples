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
import com.uber.cadence.serviceclient.WorkflowServiceTChannel;
import com.uber.cadence.serviceclient.auth.AdminJwtAuthorizationProvider;
import com.uber.cadence.worker.Worker;
import com.uber.cadence.worker.WorkerFactory;
import com.uber.cadence.workflow.Workflow;
import com.uber.cadence.workflow.WorkflowMethod;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import org.apache.commons.codec.binary.Base64;

/**
 * Hello World Cadence workflow that executes a single activity. Requires a local instance the
 * Cadence service to be running.
 */
public class HelloActivity {

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

  public static void main(String[] args) throws InvalidKeySpecException, NoSuchAlgorithmException {
    // Get a new client
    // NOTE: to set a different options, you can do like this:
    Base64 b64 = new Base64();
    byte[] decodedPub = b64.decode(testPublicKey.getBytes(StandardCharsets.UTF_8));
    byte[] decodedPri = b64.decode(testPrivateKey.getBytes(StandardCharsets.UTF_8));

    KeyFactory rsaKeyFactory = KeyFactory.getInstance("RSA");

    final RSAPublicKey rsaPublicKey =
        (RSAPublicKey) rsaKeyFactory.generatePublic(new X509EncodedKeySpec(decodedPub));

    final RSAPrivateKey rsaPrivateKey =
        (RSAPrivateKey) rsaKeyFactory.generatePrivate(new PKCS8EncodedKeySpec(decodedPri));

    final AdminJwtAuthorizationProvider authProvider =
        new AdminJwtAuthorizationProvider(rsaPublicKey, rsaPrivateKey);
    final ClientOptions clientOpts =
        ClientOptions.newBuilder().setAuthorizationProvider(authProvider).build();
    WorkflowClient workflowClient =
        WorkflowClient.newInstance(
            new WorkflowServiceTChannel(clientOpts),
            WorkflowClientOptions.newBuilder().setDomain(DOMAIN).build());
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
    String greeting = workflow.getGreeting("World");
    System.out.println(greeting);
    System.exit(0);
  }

  private static String testPublicKey =
      "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAscukltHilaq+o5gIVE4P\n"
          + "GwWl+esvJ2EaEpWw6ogr98Un11YJ4oKkwIkLw4iIo0tveCINA3cZmxaW1RejRWKE\n"
          + "qYFtQ1rYd6BsnFAHXWh2R3A1FtpG6ANUEGkE7OAJe2/L42E/ImJ+GQxRvartInDM\n"
          + "yfiRfB7+L2n3wG+Ni+hBNMtAaX4Wwbj2hup21Jjuo96TuhcGImBFBATGWaYR2wqe\n"
          + "/6by9wJexPHlY/1uDp3SnzF1dCLjp76SGCfyYqOGC/PxhQi7mDxeH9/tIC+lt/Sz\n"
          + "wc1n8gZLtlRlZHinvYa8lhWXqVYw6WD8h4LTgALq9iY+beD1PFQSY1GkQtt0RhRw\n"
          + "eQIDAQAB";

  private static String testPrivateKey =
      "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQCxy6SW0eKVqr6j\n"
          + "mAhUTg8bBaX56y8nYRoSlbDqiCv3xSfXVgnigqTAiQvDiIijS294Ig0DdxmbFpbV\n"
          + "F6NFYoSpgW1DWth3oGycUAddaHZHcDUW2kboA1QQaQTs4Al7b8vjYT8iYn4ZDFG9\n"
          + "qu0icMzJ+JF8Hv4vaffAb42L6EE0y0BpfhbBuPaG6nbUmO6j3pO6FwYiYEUEBMZZ\n"
          + "phHbCp7/pvL3Al7E8eVj/W4OndKfMXV0IuOnvpIYJ/Jio4YL8/GFCLuYPF4f3+0g\n"
          + "L6W39LPBzWfyBku2VGVkeKe9hryWFZepVjDpYPyHgtOAAur2Jj5t4PU8VBJjUaRC\n"
          + "23RGFHB5AgMBAAECggEABj1T9Orf0W9nskDQ2QQ7cuVdZEJjpMrbTK1Aw1L8/Qc9\n"
          + "TSkINDEayaV9mn1RXe61APcBSdP4ER7nXfTZiQ21LhLcWWg9T3cbh1b70oRqyI9z\n"
          + "Pi6HSBeWz4kfUBX9izMQFBZKzjYn6qaJp1b8bGXKRWkcvPRZqLhmsRPmeH3xrOHe\n"
          + "qsIDhYXMjRoOgEUxLbk8iPLP6nx0icPJl/tHK2l76R+1Ko6TBE69Md2krUIuh0u4\n"
          + "nm9n+Az+0GuvkFsLw5KMGhSBeqB+ez5qtFa8T8CUCn98IjiUDOwgZdFrNldFLcZf\n"
          + "putw7O2qCA9LT+mFBQ6CVsVu/9tKeXQ9sJ7p3lxhwQKBgQDjt7HNIabLncdXPMu0\n"
          + "ByRyNVme0+Y1vbj9Q7iodk77hvlzWpD1p5Oyvq7cN+Cb4c1iO/ZQXMyUw+9hLgmf\n"
          + "LNquH2d4hK1Jerzc/ciwu6dUBsCW8+0VJd4M2UNN15rJMPvbZGmqMq9Np1iCTCjE\n"
          + "dvHo7xjPcJhsbhMbHq+PaUU7OQKBgQDH4KuaHBFTGUPkRaQGAZNRB8dDvSExV6ID\n"
          + "Pblzr80g9kKHUnQCQfIDLjHVgDbTaSCdRw7+EXRyRmLy5mfPWEbUFfIemEpEcEcb\n"
          + "3geWeVDx4Z/FwprWFuVifRopRSQ/FAbMXLIui7OHXWLEtzBvLkR/uS2VIVPm10PV\n"
          + "pbh2EXifQQKBgQDbcOLbjelBYLt/euvGgfeCQ50orIS1Fy5UidVCKjh0tR5gJk95\n"
          + "G1L+tjilqQc+0LtuReBYkwTm+2YMXSQSi1P05fh9MEYZgDjOMZYbkcpu887V6Rx3\n"
          + "+7Te5uOv+OyFozmhs0MMK6m5iGGHtsK2iPUYBoj/Jj8MhorM4KZH6ic4KQKBgQCl\n"
          + "3zIpg09xSc9Iue5juZz6qtzXvzWzkAj4bZnggq1VxGfzix6Q3Q8tSoG6r1tQWLbj\n"
          + "Lpwnhm6/guAMud6+eIDW8ptqfnFrmE26t6hOXMEq6lXANT5vmrKj6DP0uddZrZHy\n"
          + "uJ55+B91n68elvPP4HKiGBfW4cCSGmTGAXAyM0+JwQKBgQCz2cNiFrr+oEnlHDLg\n"
          + "EqsiEufppT4FSZPy9/MtuWuMgEOBu34cckYaai+nahQLQvH62KskTK0EUjE1ywub\n"
          + "NPORuXcugxIBMHWyseOS7lrtrlSBxU9gntS7jHdM3IMrrUy9YZBvPvFGP0wLdpKM\n"
          + "nvt3vT46hs3n28XZpb18uRkSDw==";
}
