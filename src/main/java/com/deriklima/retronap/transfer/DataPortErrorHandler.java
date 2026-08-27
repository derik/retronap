package com.deriklima.retronap.transfer;

import com.deriklima.retronap.message.*;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.session.*;
import java.util.List;
import org.springframework.stereotype.Component;

/** Handles dataport error messages */
@Component
public class DataPortErrorHandler extends MessageHandler {
  private final SessionManager sessionManager;

  public DataPortErrorHandler(SessionManager sessionManager) {
    this.sessionManager = sessionManager;
    addPreConditionChecker(new UserLoggedInChecker());
    addPreConditionChecker(new ValidMessageChecker());
  }

  public void processMessage(Message m, MessageContext session) {
    String uploader = m.getDataString(0);
    String downloader = session.getUser().getNickname();

    // just pass it along.

    MessageContext targetSession = sessionManager.searchForSession(uploader);
    if (targetSession != null) {
      OutboundMessageQueue queue = targetSession.getOutboundMessageQueue();
      Message outMessage = new Message(MessageTypes.CLIENT_DATA_PORT_ERROR, downloader);
      try {
        queue.queueMessage(outMessage);
      } catch (InvalidatedQueueException iqe) {
      }
    }
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.CLIENT_DATA_PORT_ERROR);
  }
}
