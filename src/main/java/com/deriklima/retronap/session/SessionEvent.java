package com.deriklima.retronap.session;

import com.deriklima.retronap.message.MessageContext;
import org.springframework.context.ApplicationEvent;

/** Base event object for session events using Spring's event system. */
public class SessionEvent extends ApplicationEvent {
  public static final int REGISTER = 0;
  public static final int TERMINATE = 1;
  public static final int SIGNON = 2;

  private final int eventType;

  public SessionEvent(MessageContext session, int eventType) {
    super(session);
    this.eventType = eventType;
  }

  public MessageContext getSession() {
    return (MessageContext) getSource();
  }

  public int getType() {
    return eventType;
  }
}
