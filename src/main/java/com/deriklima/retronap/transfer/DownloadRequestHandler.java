package com.deriklima.retronap.transfer;

import com.deriklima.retronap.message.*;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.model.SharedFile;
import com.deriklima.retronap.session.*;
import java.util.List;
import org.springframework.stereotype.Component;

/** Handles download requests */
@Component
public class DownloadRequestHandler extends MessageHandler {

  private final SessionManager sessionManager;

  public DownloadRequestHandler(SessionManager sessionManager) {
    this.sessionManager = sessionManager;
    addPreConditionChecker(new ValidMessageChecker());
    addPreConditionChecker(new UserLoggedInChecker());
  }

  protected void processMessage(Message m, MessageContext session) {
    String nick = m.getDataString(0);
    String filename = m.getDataString(1); // Full file name, including path

    SharedFile sr = null;
    MessageContext us = null;
    MessageContext targetSession = sessionManager.searchForSession(nick);
    if (targetSession != null) {
      us = targetSession;
      if (us != null) {
        sr = us.getShareByFilename(filename);
      }
    }

    OutboundMessageQueue queue;
    Message outMessage;
    if (us != null && sr != null) {
      queue = targetSession.getOutboundMessageQueue(); // use target session's queue
      //	<nick> <ip> <port> \"<filename>\" <md5> <linespeed>
      StringBuffer msg = new StringBuffer();
      msg.append(session.getNickname());
      msg.append(" \"");
      msg.append(filename);
      msg.append("\"");
      outMessage = new Message(MessageTypes.SERVER_UPLOAD_REQUEST, msg.toString());
    } else {
      queue = session.getOutboundMessageQueue(); // use this client's queue
      StringBuffer msg = new StringBuffer(64);
      msg.append(nick);
      msg.append(" \"");
      msg.append(filename);
      msg.append("\"");
      outMessage = new Message(MessageTypes.SERVER_DOWNLOAD_ERROR, msg.toString());
    }

    try {
      queue.queueMessage(outMessage);
    } catch (InvalidatedQueueException iqe) {
    }
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.CLIENT_DOWNLOAD_REQUEST);
  }
}
