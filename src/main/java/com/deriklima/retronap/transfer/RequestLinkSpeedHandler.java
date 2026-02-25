package com.deriklima.retronap.transfer;

import com.deriklima.retronap.message.*;
import com.deriklima.retronap.model.User;
import com.deriklima.retronap.session.SessionManager;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Give a clients link speed */
@Component
@Slf4j
public class RequestLinkSpeedHandler extends MessageHandler {

  private final SessionManager sessionManager;

  public RequestLinkSpeedHandler(SessionManager sessionManager) {
    this.sessionManager = sessionManager;
    addPreConditionChecker(new UserLoggedInChecker());
    addPreConditionChecker(new ValidMessageChecker());
  }

  protected void processMessage(Message m, MessageContext session) {
    String nick = m.getDataString(0);
    Message outMessage = null;
    MessageContext targetSession = sessionManager.searchForSession(nick);
    OutboundMessageQueue queue = session.getOutboundMessageQueue();

    if (targetSession != null) {
      User us = targetSession.getUser();
      int linespeed = us.getLinkSpeedValue();

      StringBuffer msg = new StringBuffer(128);
      msg.append(nick);
      msg.append(" ");
      msg.append(linespeed);
      outMessage = new Message(MessageTypes.SERVER_LINKSPEED_RESPONSE, msg.toString());
    }
    try {
      queue.queueMessage(outMessage);
    } catch (InvalidatedQueueException iqe) {
    }
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.CLIENT_LINKSPEED_REQUEST);
  }
}
