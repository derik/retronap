package com.deriklima.retronap.statistics;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NapsterStatistics implements StatisticsMaintainer {
  private static final int MEGS_PER_GIG = 1000;
  private final AtomicInteger fileCount = new AtomicInteger(0);
  private final AtomicInteger userCount = new AtomicInteger(0);
  private final AtomicInteger sizeInMegs = new AtomicInteger(0);

  @EventListener
  public void incrementFileCount(IncreaseFileCountEvent event) {
    fileCount.addAndGet(event.increment());
  }

  @EventListener
  public void decrementFileCount(DecreaseFileCountEvent event) {
    fileCount.addAndGet(-event.decrement());
  }

  @EventListener
  public void incrementUserCount(IncreaseUserCountEvent event) {
    userCount.addAndGet(event.increment());
  }

  @EventListener
  public void decrementUserCount(DecreaseUserCountEvent event) {
    userCount.addAndGet(-event.decrement());
  }

  @EventListener
  public void incrementTotalLibSize(IncreaseTotalLibSizeEvent event) {
    sizeInMegs.addAndGet(event.mbs());
  }

  @EventListener
  public void decrementTotalLibSize(DecreaseTotalLibSizeEvent event) {
    sizeInMegs.addAndGet(-event.mbs());
  }

  @Override
  public int getUserCount() {
    return userCount.get();
  }

  @Override
  public int getTotalLibrarySizeInGigs() {
    return sizeInMegs.get() / MEGS_PER_GIG;
  }

  @Override
  public int getFileCount() {
    return fileCount.get();
  }
}
