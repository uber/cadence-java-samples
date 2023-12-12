package com.uber.cadence.samples.spring.workflows.impl;

import com.uber.cadence.samples.spring.workflows.ChildWorkflow;

public class ChildWorkflowImpl implements ChildWorkflow {
  @Override
  public String greetInChild(String msg) {
    return "Hello, " + msg + "!";
  }
}
