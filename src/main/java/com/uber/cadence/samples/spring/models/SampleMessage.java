package com.uber.cadence.samples.spring.models;

public class SampleMessage {
  String message;

  public SampleMessage(String message) {
    this.message = message;
  }

  public String GetMessage() {
    return this.message;
  }
}
