package com.deriklima.retronap.message;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/** A queue for outbound messages, so threads don't contend over the OutputStream */
@Slf4j
public class OutboundMessageQueue extends Thread {

  private final BlockingQueue<Message> queue = new LinkedBlockingQueue<>();
  private volatile boolean running = false;
  private volatile boolean invalidated = false;
  private final OutputStream os;
  private final int timeoutMs;

  public OutboundMessageQueue(OutputStream os, int timeoutSeconds) {
    this.timeoutMs = timeoutSeconds > 0 ? timeoutSeconds * 1000 : 0;
    this.os = new BufferedOutputStream(os);
  }

  public void queueMessage(Message m) throws InvalidatedQueueException {
    if (isInvalid()) {
      throw new InvalidatedQueueException("Queue has been invalidated");
    }
    if (!queue.offer(m)) {
      log.warn("Could not add message to queue, it might be full.");
    }
  }

  @Override
  public void run() {
    running = true;
    try (os) {
      while (running) {
        try {
          Message sm =
              queue.poll(timeoutMs > 0 ? timeoutMs : Long.MAX_VALUE, TimeUnit.MILLISECONDS);
          if (sm == null) {
            if (timeoutMs > 0) {
              log.info("Queue idle timeout reached, stopping.");
              stopQueue();
            }
            continue;
          }

          byte[] byteBuf = sm.toByteArray();
          os.write(byteBuf);
          os.flush();
          log.debug("[SENT]: {}", sm);
        } catch (InterruptedException e) {
          if (running) {
            log.warn("Queue thread interrupted unexpectedly.");
          } else {
            log.info("Queue thread interrupted for shutdown.");
          }
          Thread.currentThread().interrupt();
          break; // Exit loop
        } catch (IOException e) {
          log.warn("Stopping thread: IOException in OutboundMessageQueue: {}", e.getMessage());
          invalidate();
        }
      }
    } catch (IOException e) {
      log.error("Error closing output stream", e);
    }
  }

  public void startQueue() {
    if (!running) {
      start();
    }
  }

  public void stopQueue() {
    if (running) {
      running = false;
      this.interrupt();
    }
  }

  public void invalidate() {
    invalidated = true;
    stopQueue();
  }

  public boolean isInvalid() {
    return invalidated;
  }

  public boolean hasMessages() {
    return !queue.isEmpty();
  }

  public void flushQueue() {
    // Not strictly needed with a blocking queue, but can be a no-op
  }
}
