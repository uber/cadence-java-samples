package com.uber.cadence.samples.hello;

import com.uber.cadence.workflow.Workflow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

public class ExpirationTimer {
  private Instant expirationTime;
  private final Supplier<Instant> currentInstant;
  private final Duration window;

  public ExpirationTimer(final Duration window) {
    this.window = window;
    this.currentInstant = () -> Instant.ofEpochMilli(Workflow.currentTimeMillis());
    extend(window);
  }

  public ExpirationTimer(final Duration window, final Clock clock) {
    this.window = window;
    this.currentInstant = clock::instant;
    extend(window);
  }

  public Duration getDuration() {
    final Duration span = Duration.between(currentInstant.get(), expirationTime);
    if (span.isNegative()) {
      return Duration.ZERO;
    }
    return span;
  }

  public void waitForExpiration() {
    while (!isExpired()) {
      Workflow.sleep(getDuration());
    }
  }

  public void reset() {
    extend(window);
  }

  public boolean isExpired() {
    return currentInstant.get().isAfter(expirationTime);
  }

  private void extend(final Duration window) {
    expirationTime = currentInstant.get().plus(window);
  }
}
