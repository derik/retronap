package com.deriklima.retronap.user;

import com.deriklima.retronap.message.*;
import com.deriklima.retronap.message.LevelAtLeastAdminChecker;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.UserLoggedInChecker;
import com.deriklima.retronap.message.ValidMessageChecker;
import com.deriklima.retronap.model.User;
import com.deriklima.retronap.session.SessionManager;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Handles various change requests that affect other users' state. Client should be an admin if
 * sending these msgs.
 */
@Component
public class AdminUserOptionHandler extends MessageHandler {
  private final SessionManager sessionManager;

  public AdminUserOptionHandler(SessionManager sessionManager) {
    this.sessionManager = sessionManager;
    addPreConditionChecker(new UserLoggedInChecker());
    addPreConditionChecker(new ValidMessageChecker());
    addPreConditionChecker(new LevelAtLeastAdminChecker());
  }

  protected void processMessage(Message m, MessageContext session) {
    switch (m.getType()) {
      case MessageTypes.CLIENT_CHANGE_USER_LINKSPEED:
        {
          String user = m.getDataString(0);
          String newLinkType = m.getDataString(1);
          MessageContext targetSession = sessionManager.searchForSession(user);
          if (targetSession != null) {
            User us = targetSession.getUser();
            us.setLinkSpeedValue(Integer.parseInt(newLinkType));
          }
          break;
        }
      case MessageTypes.CLIENT_CHANGE_USER_DATAPORT:
        {
          String user = m.getDataString(0);
          String newDataPort = m.getDataString(1);
          MessageContext targetSession = sessionManager.searchForSession(user);
          if (targetSession != null) {
            User us = targetSession.getUser();
            us.setLinkSpeedValue(Integer.parseInt(newDataPort));
            OutboundMessageQueue queue = session.getOutboundMessageQueue();
            try {
              // send change port notification to target client
              queue.queueMessage(
                  new Message(MessageTypes.CLIENT_CHANGE_USER_DATAPORT, newDataPort));
            } catch (InvalidatedQueueException iqe) {
            }
          }
          break;
        }
    }
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(
        MessageTypes.CLIENT_CHANGE_USER_LINKSPEED, MessageTypes.CLIENT_CHANGE_USER_DATAPORT);
  }
}
