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

package com.uber.cadence.samples.calculation;

import com.uber.cadence.activity.ActivityOptions;
import com.uber.cadence.workflow.Async;
import com.uber.cadence.workflow.Promise;
import com.uber.cadence.workflow.Workflow;
import java.time.Duration;
import org.slf4j.Logger;

public class WorkflowMethodsImpl implements WorkflowMethods {

  private static Logger LOGGER = Workflow.getLogger(WorkflowMethodsImpl.class);

  private final ActivityOptions options =
      new ActivityOptions.Builder().setScheduleToCloseTimeout(Duration.ofHours(1)).build();

  private final Activities activities = Workflow.newActivityStub(Activities.class, options);

  private long factorForGn = -1;
  private long abPlusAcPlusBc = -1;
  private long currentG = -1;

  @Override
  public long calculate(long a, long b, long c) {
    LOGGER.info("workflow start...");

    long result = 0;

    // Async.invoke takes method reference and activity parameters and returns Promise.
    Promise<Long> ab = Async.function(activities::multiple, a, b);
    Promise<Long> ac = Async.function(activities::multiple, a, c);
    Promise<Long> bc = Async.function(activities::multiple, b, c);

    // Promise#get blocks until result is ready.
    this.abPlusAcPlusBc = result = ab.get() + ac.get() + bc.get();

    // waiting 30s for a human input to decide the factor N for g(n), based on a*b+a*c+b*c
    // the waiting timer is durable, independent of workers' liveness
    final boolean received = Workflow.await(Duration.ofMinutes(2), () -> this.factorForGn > 1);
    if (!received) {
      this.factorForGn = 10;
    }

    long fi_1 = 0; // f(0)
    long fi_2 = 1; // f(1)
    this.currentG = 1; // current g = f(0)*f(0) + f(1)*f(1)
    long i = 2;

    for (; i < this.factorForGn; i++) {
      // get next fibonacci number
      long fi = fi_1 + fi_2;
      fi_2 = fi_1;
      fi_1 = fi;

      this.currentG += activities.multiple(fi, fi);
    }

    result += this.currentG;
    return result;
  }

  @Override
  public void factorForGn(final long n) {
    if (n < 2) {
      LOGGER.warn("receive invalid factor, " + n + ", it must be greater than 1");
    }
    if (this.factorForGn > 1) {
      LOGGER.warn(
          "factor N for g(n) is has been set to " + this.factorForGn + " and cannot be changed");
    }
    this.factorForGn = n;
    LOGGER.info("receive factor, " + n + " and set as factor N for g(n)");
  }

  @Override
  public long factorForGn() {
    return this.factorForGn;
  }

  @Override
  public long abPlusAcPlusBc() {
    return this.abPlusAcPlusBc;
  }

  @Override
  public long currentG() {
    return this.currentG;
  }
}
