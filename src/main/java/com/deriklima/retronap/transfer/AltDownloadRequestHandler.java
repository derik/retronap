package com.deriklima.retronap.transfer;

import com.deriklima.retronap.message.*;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.model.SharedFile;
import com.deriklima.retronap.model.User;
import com.deriklima.retronap.session.*;
import com.deriklima.retronap.util.*;
import java.util.List;
import org.springframework.stereotype.Component;

/** Handles alt download requests */
@Component
public class AltDownloadRequestHandler extends MessageHandler {

  private final SessionManager sessionManager;

  public AltDownloadRequestHandler(SessionManager sessionManager) {
    this.sessionManager = sessionManager;
    addPreConditionChecker(new UserLoggedInChecker());
    addPreConditionChecker(new ValidMessageChecker());
  }

  protected void processMessage(Message m, MessageContext session) {
    String nick = m.getDataString(0);
    String filename = m.getDataString(1);

    MessageContext us = session;
    User user = us.getUser();

    MessageContext targetSession = sessionManager.searchForSession(nick);

    if (targetSession != null) {
      SharedFile sr = targetSession.getShareByFilename(filename);

      // <nick> <ip> <port> \"<filename>\" <md5> <speed>
      OutboundMessageQueue queue = targetSession.getOutboundMessageQueue();
      StringBuffer msg = new StringBuffer(128);
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
      Message outMessage = new Message(MessageTypes.SERVER_ALT_DOWNLOAD_ACK, msg.toString());
      try {
        queue.queueMessage(outMessage);
      } catch (InvalidatedQueueException iqe) {
      }
    }
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.CLIENT_ALT_DOWNLOAD_REQUEST);
  }
}
