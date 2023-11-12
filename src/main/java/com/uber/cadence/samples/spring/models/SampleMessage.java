package com.uber.cadence.samples.spring.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SampleMessage {
  @JsonProperty("message")
  String message;

  @JsonCreator
  public SampleMessage(String message) {
    this.message = message;
  }

  public String GetMessage() {
    return this.message;
  }
}
