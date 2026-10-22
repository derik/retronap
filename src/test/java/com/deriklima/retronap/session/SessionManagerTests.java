package com.deriklima.retronap.session;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

class SessionManagerTests {
  @Test
  void nullNicknameDoesNotMatchAnUnauthenticatedSessionOrBreakLookup() {
    var manager = new SessionManager();
    var unauthenticated = mock(Session.class);
    var authenticated = mock(Session.class);
    when(authenticated.getNickname()).thenReturn("alice");
    manager.addActiveSession(unauthenticated);
    manager.addActiveSession(authenticated);

    assertNull(manager.searchForSession(null));
    assertNull(manager.searchForUserState(null));
    assertNull(manager.searchForSession("offline"));
    assertSame(authenticated, manager.searchForSession("alice"));
  }
}
