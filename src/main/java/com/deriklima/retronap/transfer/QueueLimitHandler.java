package com.deriklima.retronap.transfer;

import com.deriklima.retronap.message.*;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.model.SharedFile;
import com.deriklima.retronap.session.*;
import java.util.List;
import org.springframework.stereotype.Component;

/** Handles queue limit messages */
@Component
public class QueueLimitHandler extends MessageHandler {
  private final SessionManager sessionManager;

  public QueueLimitHandler(SessionManager sessionManager) {
    this.sessionManager = sessionManager;
    addPreConditionChecker(new UserLoggedInChecker());
    addPreConditionChecker(new ValidMessageChecker());
  }

  public void processMessage(Message m, MessageContext session) {
    String downloader = m.getDataString(0);
    String filename = m.getDataString(1);
    String limit = m.getDataString(2);

    MessageContext targetSession = sessionManager.searchForSession(downloader);
    SharedFile sr = session.getShareByFilename(filename);
    if (targetSession != null && sr != null) {
      String uploader = session.getUser().getNickname();
      OutboundMessageQueue queue = targetSession.getOutboundMessageQueue();

      // <nick> \"<filename>\" <filesize> <digit>
      StringBuffer buf = new StringBuffer(80);
      buf.append(uploader);
      buf.append(" \"");
      buf.append(filename);
      buf.append("\" ");
      buf.append(sr.getSize());
      buf.append(" ");
      buf.append(limit);
      try {
        queue.queueMessage(new Message(MessageTypes.SERVER_QUEUE_LIMIT, buf.toString()));
      } catch (InvalidatedQueueException iqe) {
      }
    }
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.CLIENT_QUEUE_LIMIT);
  }
}
