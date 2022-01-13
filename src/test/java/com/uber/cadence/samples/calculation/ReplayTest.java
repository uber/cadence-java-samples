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

import com.uber.cadence.testing.WorkflowReplayer;
import org.junit.Test;

// This replay test is the recommended way to make sure changing workflow code is backward
// compatible without non-deterministic errors.
// "HelloActivity.json" can be downloaded from cadence CLI:
//      cadence --do samples-domain wf show -w helloworld_d002cd3a-aeee-4a11-aa30-1c62385b4d87
// --output_filename ~/tmp/HelloActivity.json
// Or from Cadence Web UI. (You may need to put history file in resources folder; and change
// workflowType in the first event of history).
public class ReplayTest {
  @Test
  public void testReplay() throws Exception {
    WorkflowReplayer.replayWorkflowExecutionFromResource(
        "calculation.workflow.history.json", WorkflowMethodsImpl.class);
  }
}
