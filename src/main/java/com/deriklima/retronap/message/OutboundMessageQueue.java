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
public class OutboundMessageQueue {

  private final BlockingQueue<Message> queue = new LinkedBlockingQueue<>();
  private volatile boolean running = false;
  private volatile boolean invalidated = false;
  private Thread writer;
  private final OutputStream os;
  private final long timeoutMs;

  public OutboundMessageQueue(OutputStream os, int timeoutSeconds) {
    this.timeoutMs = timeoutSeconds > 0 ? timeoutSeconds * 1000L : 0;
    this.os = new BufferedOutputStream(os);
  }

  public synchronized void queueMessage(Message m) throws InvalidatedQueueException {
    if (isInvalid()) {
      throw new InvalidatedQueueException("Queue has been invalidated");
    }
    if (!queue.offer(m)) {
      log.warn("Could not add message to queue, it might be full.");
    }
  }

  private void run() {
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
    } finally {
      invalidate();
    }
  }

  public synchronized void startQueue() {
    if (writer == null && !invalidated) {
      running = true;
      writer = Thread.ofVirtual().name("retronap-outbound").unstarted(this::run);
      writer.start();
    }
  }

  public synchronized void stopQueue() {
    running = false;
    invalidated = true;
    queue.clear();
    if (writer != null) {
      writer.interrupt();
    }
  }

  public void invalidate() {
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
