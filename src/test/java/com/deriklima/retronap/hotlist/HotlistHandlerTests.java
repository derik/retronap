package com.deriklima.retronap.hotlist;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.deriklima.retronap.message.Message;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.MessageTypes;
import com.deriklima.retronap.session.SessionEvent;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class HotlistHandlerTests {
  @Test
  void disconnectReleasesOnlyThatUsersLoopState() {
    var hotlist = mock(Hotlist.class);
    var handler = new HotlistHandler(hotlist, null);
    var first = mock(MessageContext.class);
    var second = mock(MessageContext.class);
    when(first.getUserId()).thenReturn(UUID.randomUUID());
    when(second.getUserId()).thenReturn(UUID.randomUUID());
    var create = new Message(MessageTypes.CLIENT_HOTLIST_CREATE, "target");
    handler.processMessage(create, first);
    handler.processMessage(create, second);
    var states = (Map<?, ?>) ReflectionTestUtils.getField(handler, "hotlistCreateLoopState");
    assertEquals(2, states.size());
    handler.handleSessionEvent(new SessionEvent(first, SessionEvent.SIGNON));
    assertEquals(2, states.size());
    handler.handleSessionEvent(new SessionEvent(first, SessionEvent.TERMINATE));
    assertFalse(states.containsKey(first.getUserId()));
    assertTrue(states.containsKey(second.getUserId()));
    handler.processMessage(create, first);
    verify(hotlist, times(2)).addStalkerForUser(first, "target");
    handler.handleSessionEvent(new SessionEvent(first, SessionEvent.TERMINATE));
    handler.handleSessionEvent(new SessionEvent(second, SessionEvent.TERMINATE));
    handler.handleSessionEvent(
        new SessionEvent(mock(MessageContext.class), SessionEvent.TERMINATE));
    assertTrue(states.isEmpty());
  }
}
