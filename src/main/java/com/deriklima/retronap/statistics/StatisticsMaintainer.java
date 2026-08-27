package com.deriklima.retronap.statistics;

/**
 * StatisticsMaintainer should be implemented by a facility that maintains and provides stats about
 * the server. Implementations are responsible for being queried periodically.
 */
public interface StatisticsMaintainer {
  int getFileCount();

  int getUserCount();

  int getTotalLibrarySizeInGigs();
}
