package com.uber.cadence.samples.replaytests;

import com.uber.cadence.samples.hello.HelloPeriodic;
import com.uber.cadence.testing.WorkflowReplayer;
import org.junit.Test;

public class HelloPeriodicReplayTest {

  // continue-as-new case for replayer tests: Passing
  @Test
  public void testReplay_continueAsNew() throws Exception {
    WorkflowReplayer.replayWorkflowExecutionFromResource(
        "replaytests/HelloPeriodic.json", HelloPeriodic.GreetingWorkflowImpl.class);
  }

  // Continue as new case: change in sleep timer compared to original workflow definition. It should
  // fail. BUT it is currently passing.
  @Test
  public void testReplay_continueAsNew_timerChange() throws Exception {
    WorkflowReplayer.replayWorkflowExecutionFromResource(
        "replaytests/HelloPeriodic.json",
        HelloPeriodic_sleepTimerChange.GreetingWorkflowImpl.class);
  }
}
