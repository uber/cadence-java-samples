package com.uber.cadence.samples.spring.controller;

import com.uber.cadence.samples.spring.clientsamples.HelloWorldSample;
import com.uber.cadence.samples.spring.models.SampleMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/samples/hello-world")
public class HelloWorldSampleController {
  @Autowired private HelloWorldSample helloWorldSample;

  @GetMapping("/healthcheck")
  public String healthCheck() {
    return "ok";
  }

  @PostMapping("/workflow-start")
  public void startHelloWorldWorkflow(@RequestBody SampleMessage inputMessage) {
    helloWorldSample.startHelloWorldWorkflow(inputMessage);
  }
}
