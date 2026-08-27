package com.deriklima.retronap.session;

import com.deriklima.retronap.message.*;
import com.deriklima.retronap.message.MessageContext;
import java.util.List;
import org.springframework.stereotype.Component;

/** Handles ping and pong messages */
@Component
public class PingHandler extends MessageHandler {

  private final SessionManager sessionManager;

  public PingHandler(SessionManager sessionManager) {
    this.sessionManager = sessionManager;
    addPreConditionChecker(new ValidMessageChecker());
    addPreConditionChecker(new UserLoggedInChecker());
  }

  public void processMessage(Message m, MessageContext session) {
    int msgType = m.getType();
    String nickParam = m.getDataString(0);
    MessageContext targetSession = sessionManager.searchForSession(nickParam);
    if (targetSession != null) {
      OutboundMessageQueue out = targetSession.getOutboundMessageQueue();
      try {
        out.queueMessage(new Message(msgType, session.getUser().getNickname()));
      } catch (InvalidatedQueueException iqe) {
      }
    } else {
      OutboundMessageQueue out = session.getOutboundMessageQueue();
      try {
        out.queueMessage(
            new Message(
                MessageTypes.SERVER_GENERAL_ERROR, "ping failed, " + nickParam + " not online."));
      } catch (InvalidatedQueueException iqe) {
      }
    }
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.PING, MessageTypes.PONG);
  }
}
