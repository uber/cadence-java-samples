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

import static com.uber.cadence.samples.calculation.Main.DEFAULT_TASK_LIST;

import com.uber.cadence.workflow.QueryMethod;
import com.uber.cadence.workflow.SignalMethod;
import com.uber.cadence.workflow.WorkflowMethod;

public interface WorkflowMethods {

  @WorkflowMethod(executionStartToCloseTimeoutSeconds = 3600, taskList = DEFAULT_TASK_LIST)
  long calculate(long a, long b, long c);

  /** Receives n for g(n) through an external signal. */
  @SignalMethod
  void factorForGn(long n);

  /** Returns factorForGn as a query value. */
  @QueryMethod
  long factorForGn();

  /** Returns a*b+a*c+b*c as a query value. */
  @QueryMethod
  long abPlusAcPlusBc();

  @QueryMethod
  long currentG();
}
