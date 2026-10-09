package com.deriklima.retronap.session;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.deriklima.retronap.message.MessageHandler;
import com.deriklima.retronap.message.MessageHandlerFactory;
import com.deriklima.retronap.message.MessageTypes;
import com.deriklima.retronap.message.OutboundMessageQueue;
import java.util.ArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class SessionStatusSchedulerTests {
  @Test
  void cancelledTimersImmediatelyReleaseSessionCallbacks() {
    var scheduler = new SessionStatusScheduler();
    try {
      var tasks = new ArrayList<ScheduledFuture<?>>();
      for (int i = 0; i < 100; i++) {
        tasks.add(scheduler.schedule(() -> fail("must wait 60 seconds before the first update")));
      }
      var executor =
          (ScheduledThreadPoolExecutor) ReflectionTestUtils.getField(scheduler, "executor");
      assertEquals(1, executor.getPoolSize());
      assertEquals(100, executor.getQueue().size());
      assertTrue(tasks.getFirst().getDelay(TimeUnit.SECONDS) > 50);
      tasks.forEach(task -> task.cancel(false));
      assertTrue(executor.getQueue().isEmpty(), "cancelled callbacks must not retain sessions");
    } finally {
      scheduler.shutdown();
    }
  }

  @Test
  void stoppingOneSessionLeavesOtherSessionsScheduled() throws Exception {
    var scheduler = new SessionStatusScheduler();
    var factory = mock(MessageHandlerFactory.class);
    var handler = mock(MessageHandler.class);
    when(factory.getHandlerForType(MessageTypes.SERVER_STATS)).thenReturn(handler);
    var first = new Session(null, null, factory, null, null, null, scheduler);
    var second = new Session(null, null, factory, null, null, null, scheduler);
    try {
      first.startStatusUpdateThread();
      ScheduledFuture<?> original = first.getStatusUpdateTask();
      first.startStatusUpdateThread();
      assertSame(original, first.getStatusUpdateTask());
      second.startStatusUpdateThread();
      first.stopStatusUpdateThread();
      assertTrue(original.isCancelled());
      assertFalse(second.getStatusUpdateTask().isCancelled());
      assertNull(first.getStatusUpdateTask());
    } finally {
      first.stopStatusUpdateThread();
      second.stopStatusUpdateThread();
      scheduler.shutdown();
    }
  }

  @Test
  void callbackSendsStatsAndCancelsWhenOutboundQueueIsInvalid() throws Exception {
    var scheduler = mock(SessionStatusScheduler.class);
    var future = mock(ScheduledFuture.class);
    doReturn(future).when(scheduler).schedule(any());
    var factory = mock(MessageHandlerFactory.class);
    var handler = mock(MessageHandler.class);
    when(factory.getHandlerForType(MessageTypes.SERVER_STATS)).thenReturn(handler);
    var session = new Session(null, null, factory, null, null, null, scheduler);
    var queue = mock(OutboundMessageQueue.class);
    ReflectionTestUtils.setField(session, "outboundMessageQueue", queue);
    session.startStatusUpdateThread();
    var callback = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler).schedule(callback.capture());
    callback.getValue().run();
    verify(handler).handleMessage(null, session);
    when(queue.isInvalid()).thenReturn(true);
    callback.getValue().run();
    verify(future).cancel(false);
    verifyNoMoreInteractions(handler);
    assertNull(session.getStatusUpdateTask());
  }
}
