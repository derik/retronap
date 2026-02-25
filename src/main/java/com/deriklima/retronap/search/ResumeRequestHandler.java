package com.deriklima.retronap.search;

import com.deriklima.retronap.message.*;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.UserLoggedInChecker;
import com.deriklima.retronap.message.ValidMessageChecker;
import com.deriklima.retronap.model.ResumeParameters;
import com.deriklima.retronap.model.SearchResult;
import com.deriklima.retronap.model.SharedFile;
import com.deriklima.retronap.util.Util;
import java.util.List;
import org.springframework.stereotype.Component;

/** Handles client search request messages from client. */
@Component
public class ResumeRequestHandler extends MessageHandler {
  private final Message endResultsMessage = new Message(MessageTypes.SERVER_RESUME_LIST_END, "");
  private final SearchService searchService;

  public ResumeRequestHandler(SearchService searchService) {
    this.searchService = searchService;
    addPreConditionChecker(new UserLoggedInChecker());
    addPreConditionChecker(new ValidMessageChecker());
  }

  private static StringBuffer getStringBuffer(SearchResult sr) {
    SharedFile share = sr.getShare();
    //	<user> <ip> <port> <filename> <checksum> <size> <speed>
    StringBuffer msgBuf = new StringBuffer(256);
    msgBuf.append(sr.getNickname());
    msgBuf.append(" ");
    msgBuf.append(Util.byteArrayToLongIPAddress(sr.getIpAddress()));
    msgBuf.append(" ");
    msgBuf.append(sr.getPort());
    msgBuf.append(" \"");
    msgBuf.append(share.getAbsolutePath());
    msgBuf.append("\" ");
    msgBuf.append(share.getMd5Signature());
    msgBuf.append(" ");
    msgBuf.append(share.getSize());
    msgBuf.append(" ");
    msgBuf.append(sr.getLinkType());
    return msgBuf;
  }

  public void processMessage(Message m, MessageContext session) {
    OutboundMessageQueue queue = session.getOutboundMessageQueue();
    ResumeParameters params = new ResumeParameters();

    params.setChecksum(m.getDataString(0));
    params.setFilesize(Integer.parseInt(m.getDataString(1)));

    try {
      List<SearchResult> results = searchService.searchForShares(params);
      if (results != null) {
        for (SearchResult sr : results) {
          StringBuffer msgBuf = getStringBuffer(sr);
          queue.queueMessage(new Message(MessageTypes.SERVER_RESUME_LIST_ENTRY, msgBuf.toString()));
        }
      }
      queue.queueMessage(endResultsMessage);
    } catch (InvalidatedQueueException iqe) {
    }
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.CLIENT_RESUME_REQUEST);
  }
}
