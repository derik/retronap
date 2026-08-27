package com.deriklima.retronap.user;

import com.deriklima.retronap.message.*;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.UserLoggedInChecker;
import com.deriklima.retronap.message.ValidMessageChecker;
import com.deriklima.retronap.model.SharedFile;
import com.deriklima.retronap.session.SessionManager;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Handles browse user request messages from client. */
@Component
@Slf4j
public class ClientBrowseUserHandler extends MessageHandler {

  private final SessionManager sessionManager;

  public ClientBrowseUserHandler(SessionManager sessionManager) {
    this.sessionManager = sessionManager;
    addPreConditionChecker(new UserLoggedInChecker());
    addPreConditionChecker(new ValidMessageChecker());
  }

  protected void processMessage(Message m, MessageContext session) {
    OutboundMessageQueue queue = session.getOutboundMessageQueue();

    String nickname = m.getDataString(0);

    MessageContext targetSession = sessionManager.searchForSession(nickname);
    MessageContext userState = null;
    if (targetSession != null) {
      userState = targetSession;
    }

    try {
      if (userState != null) {
        SharedFile[] records = userState.getShares();
        if (records != null) {
          for (int x = 0; x < records.length; x++) {
            SharedFile aRecord = records[x];
            StringBuffer buf = new StringBuffer(128);
            buf.append(nickname);
            buf.append(" \"");
            buf.append(aRecord.getAbsolutePath());
            buf.append("\" ");
            buf.append(aRecord.getMd5Signature());
            buf.append(" ");
            buf.append(aRecord.getSize());
            buf.append(" ");
            buf.append(aRecord.getBitrate());
            buf.append(" ");
            buf.append(aRecord.getFrequency());
            buf.append(" ");
            buf.append(aRecord.getSeconds());
            queue.queueMessage(
                new Message(MessageTypes.SERVER_BROWSE_USER_RESPONSE, buf.toString()));
          }
        }
      }
      queue.queueMessage(new Message(MessageTypes.SERVER_BROWSE_USER_END_RESPONSE, nickname));
    } catch (InvalidatedQueueException iqe) {
    }
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.CLIENT_BROWSE_USER_FILES);
  }
}
