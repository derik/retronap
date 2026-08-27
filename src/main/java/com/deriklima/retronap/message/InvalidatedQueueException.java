package com.deriklima.retronap.message;

/** Thrown by invalidated queues (when sockets have been closed or IOException occurs) */
public class InvalidatedQueueException extends Exception {
  public InvalidatedQueueException(String s) {
    super(s);
  }
}
