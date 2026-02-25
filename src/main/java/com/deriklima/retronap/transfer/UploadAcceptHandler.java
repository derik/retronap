package com.deriklima.retronap.transfer;

import com.deriklima.retronap.message.*;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.model.SharedFile;
import com.deriklima.retronap.model.User;
import com.deriklima.retronap.session.*;
import com.deriklima.retronap.util.*;
import java.util.List;
import org.springframework.stereotype.Component;

/** Handles "upload accept" requests */
@Component
public class UploadAcceptHandler extends MessageHandler {

  private final SessionManager sessionManager;

  public UploadAcceptHandler(SessionManager sessionManager) {
    this.sessionManager = sessionManager;
    addPreConditionChecker(new ValidMessageChecker());
    addPreConditionChecker(new UserLoggedInChecker());
  }

  protected void processMessage(Message m, MessageContext session) {
    String nick = m.getDataString(0);
    String filename = m.getDataString(1);

    MessageContext us = session;
    User user = us.getUser();
    SharedFile sr = us.getShareByFilename(filename);
    MessageContext targetSession = sessionManager.searchForSession(nick);

    if (targetSession != null && sr != null) {
      OutboundMessageQueue queue = targetSession.getOutboundMessageQueue();
      StringBuffer msg = new StringBuffer(256);
      msg.append(us.getNickname());
      msg.append(" ");
      msg.append(Util.byteArrayToLongIPAddress(us.getIPAddress()));
      msg.append(" ");
      msg.append(user.getDataPort());
      msg.append(" \"");
      msg.append(sr.getAbsolutePath());
      msg.append("\" ");
      msg.append(sr.getMd5Signature());
      msg.append(" ");
      msg.append(user.getLinkSpeedValue());
      Message outMessage = new Message(MessageTypes.SERVER_DOWNLOAD_ACK, msg.toString());

      try {
        queue.queueMessage(outMessage);
      } catch (InvalidatedQueueException iqe) {
      }
    }
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.CLIENT_UPLOAD_ACCEPT);
  }
}
