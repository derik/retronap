package com.deriklima.retronap.session;

import com.deriklima.retronap.message.*;
import com.deriklima.retronap.message.MessageContext;
import java.util.List;
import org.springframework.stereotype.Component;

/** Handles private messages */
@Component
public class PrivateMessageHandler extends MessageHandler {
  private final SessionManager sessionManager;

  public PrivateMessageHandler(SessionManager sessionManager) {
    this.sessionManager = sessionManager;
    addPreConditionChecker(new ValidMessageChecker());
    addPreConditionChecker(new UserLoggedInChecker());
  }

  public void processMessage(Message m, MessageContext session) {
    String nickname = m.getDataString(0);
    String textToSend = m.getDataString();
    textToSend = textToSend.substring(textToSend.indexOf(' ') + 1, textToSend.length());
    StringBuffer msg = new StringBuffer(128);

    // insert this session's username as originator
    msg.append(session.getUser().getNickname());
    msg.append(" ");
    msg.append(textToSend);

    MessageContext targetSession = sessionManager.searchForSession(nickname);
    if (targetSession != null) {
      OutboundMessageQueue queue = targetSession.getOutboundMessageQueue();
      try {
        queue.queueMessage(new Message(MessageTypes.PRIVATE_MESSAGE, msg.toString()));
      } catch (InvalidatedQueueException iqe) {
      }
    } else {
      OutboundMessageQueue queue = session.getOutboundMessageQueue();
      try {
        queue.queueMessage(
            new Message(
                MessageTypes.SERVER_GENERAL_ERROR, "User " + nickname + " not currently online"));
      } catch (InvalidatedQueueException iqe) {
      }
    }
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.PRIVATE_MESSAGE);
  }
}
