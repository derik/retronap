package com.deriklima.retronap.session;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/** One timer for all sessions; callbacks only enqueue statistics, never write to sockets. */
@Component
public class SessionStatusScheduler {
  private final ScheduledThreadPoolExecutor executor =
      new ScheduledThreadPoolExecutor(
          1, Thread.ofPlatform().daemon().name("retronap-status").factory());

  public SessionStatusScheduler() {
    // Cancelled tasks otherwise retain their sessions until the next 60-second deadline.
    executor.setRemoveOnCancelPolicy(true);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
  }

  public ScheduledFuture<?> schedule(Runnable update) {
    return executor.scheduleAtFixedRate(update, 60, 60, TimeUnit.SECONDS);
  }

  @PreDestroy
  public void shutdown() {
    executor.shutdownNow();
  }
}
