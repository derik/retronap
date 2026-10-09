package com.deriklima.retronap.message;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class OutboundMessageQueueTests {
  @Test
  void virtualWriterPreservesPacketOrderAndClosesOnShutdown() throws Exception {
    var bytes = new ByteArrayOutputStream();
    var flushed = new CountDownLatch(3);
    var closed = new CountDownLatch(1);
    var virtual = new AtomicBoolean();
    var output =
        new OutputStream() {
          @Override
          public void write(int value) {
            virtual.set(Thread.currentThread().isVirtual());
            bytes.write(value);
          }

          @Override
          public void flush() {
            flushed.countDown();
          }

          @Override
          public void close() {
            closed.countDown();
          }
        };
    var queue = new OutboundMessageQueue(output, 0);
    var expected = new ByteArrayOutputStream();
    try {
      for (int kind : new int[] {3, 621, 202}) {
        var message = new Message(kind, "payload");
        expected.write(message.toByteArray());
        queue.queueMessage(message);
      }
      queue.startQueue();
      queue.startQueue(); // Concurrent login/MOTD paths must not start another writer.
      assertTrue(flushed.await(5, TimeUnit.SECONDS));
      assertTrue(virtual.get());
      assertArrayEquals(expected.toByteArray(), bytes.toByteArray());
    } finally {
      queue.invalidate();
    }
    assertTrue(closed.await(5, TimeUnit.SECONDS));
    assertTrue(queue.isInvalid());
    assertThrows(InvalidatedQueueException.class, () -> queue.queueMessage(new Message(202, "")));
  }

  @Test
  void shutdownInterruptsBlockedWriterAndReleasesPendingMessages() throws Exception {
    var writing = new CountDownLatch(1);
    var closed = new CountDownLatch(1);
    var output =
        new OutputStream() {
          @Override
          public void write(int value) throws IOException {
            writing.countDown();
            try {
              new CountDownLatch(1).await();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IOException("interrupted", e);
            }
          }

          @Override
          public void close() {
            closed.countDown();
          }
        };
    var queue = new OutboundMessageQueue(output, 0);
    try {
      queue.startQueue();
      queue.queueMessage(new Message(201, "first"));
      assertTrue(writing.await(5, TimeUnit.SECONDS));
      queue.queueMessage(new Message(202, ""));
      assertTrue(queue.hasMessages());
      queue.invalidate();
      assertFalse(queue.hasMessages());
      assertTrue(closed.await(5, TimeUnit.SECONDS));
    } finally {
      queue.invalidate();
    }
  }

  @Test
  void invalidationBeforeStartCannotResurrectWriter() throws Exception {
    var bytes = new ByteArrayOutputStream();
    var queue = new OutboundMessageQueue(bytes, 0);
    queue.queueMessage(new Message(202, ""));
    queue.invalidate();
    queue.startQueue();
    assertTrue(queue.isInvalid());
    assertFalse(queue.hasMessages());
    assertEquals(0, bytes.size());
    assertThrows(InvalidatedQueueException.class, () -> queue.queueMessage(new Message(202, "")));
  }

  @Test
  void idleTimeoutInvalidatesQueue() throws Exception {
    var closed = new CountDownLatch(1);
    var output =
        new ByteArrayOutputStream() {
          @Override
          public void close() {
            closed.countDown();
          }
        };
    var queue = new OutboundMessageQueue(output, 1);
    try {
      queue.startQueue();
      assertTrue(closed.await(5, TimeUnit.SECONDS));
      assertTrue(queue.isInvalid());
    } finally {
      queue.invalidate();
    }
  }
}
