package com.deriklima.retronap.search;

import com.deriklima.retronap.message.*;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.UserLoggedInChecker;
import com.deriklima.retronap.message.ValidMessageChecker;
import com.deriklima.retronap.model.SearchParameters;
import com.deriklima.retronap.model.SearchResult;
import com.deriklima.retronap.model.SharedFile;
import com.deriklima.retronap.util.Util;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Handles client search request messages from client. */
@Component
@Slf4j
@Deprecated
/*
 Use {@link com.deriklima.retronap.message.handlers.NewClientSearchRequestHandler} instead.
 TODO: Remove this class when the new search handler is fully functional.
*/
public class ClientSearchRequestHandler extends MessageHandler {

  private static final int MAX_RESULTS = 100;
  private final SearchService searchService;
  private final Message endSearchResultsMessage =
      new Message(MessageTypes.SERVER_END_SEARCH_RESULTS, "");

  public ClientSearchRequestHandler(SearchService searchService) {
    this.searchService = searchService;
    addPreConditionChecker(new UserLoggedInChecker());
    addPreConditionChecker(new ValidMessageChecker());
  }

  private static StringBuffer getStringBuffer(SearchResult sr) {
    SharedFile share = sr.getShare();
    // "<filename>" <md5> <size> <bitrate> <frequency> <length> <nick> <ip> <link-type>
    StringBuffer msgBuf = new StringBuffer(256);
    msgBuf.append("\"");
    msgBuf.append(share.getAbsolutePath());
    msgBuf.append("\" ");
    msgBuf.append(share.getMd5Signature());
    msgBuf.append(" ");
    msgBuf.append(share.getSize());
    msgBuf.append(" ");
    msgBuf.append(share.getBitrate());
    msgBuf.append(" ");
    msgBuf.append(share.getFrequency());
    msgBuf.append(" ");
    msgBuf.append(share.getSeconds());
    msgBuf.append(" ");
    msgBuf.append(sr.getNickname());
    msgBuf.append(" ");
    msgBuf.append(Util.byteArrayToLongIPAddress(sr.getIpAddress()));
    msgBuf.append(" ");
    msgBuf.append(sr.getLinkType());
    return msgBuf;
  }

  private static SearchParameters getSearchParams(Message m, int totalTokens) {
    SearchParameters params = new SearchParameters();
    int x = 0;
    while (x < totalTokens) {
      String token = m.getDataString(x);
      if ("FILENAME".equals(token)) // artist name
      {
        if (x == 0) {
          params.setArtistName(m.getDataString(x + 2));
        } else // must be song
        {
          params.setSong(m.getDataString(x + 2));
        }
        x = x + 2;
      } else if ("MAX_RESULTS".equals(token)) {
        params.setMaxResults(Integer.parseInt(m.getDataString(x + 1)));
        x = x + 1;
      } else if ("LINESPEED".equals(token)) {
        params.setLinespeedOperator(m.getDataString(x + 1));
        params.setLinespeed(Integer.parseInt(m.getDataString(x + 2)));
        x = x + 2;
      } else if ("BITRATE".equals(token)) {
        params.setBitrateOperator(m.getDataString(x + 1));
        params.setBitrate(Integer.parseInt(m.getDataString(x + 2)));
        x = x + 2;
      } else if ("FREQ".equals(token)) {
        params.setFrequencyOperator(m.getDataString(x + 1));
        params.setFrequency(Integer.parseInt(m.getDataString(x + 2)));
        x = x + 2;
      }
      x++;
    }

    int maxResults = params.getMaxResults();
    if (maxResults <= 0 || maxResults > MAX_RESULTS) {
      params.setMaxResults(MAX_RESULTS);
    }
    return params;
  }

  @Override
  protected void processMessage(Message m, MessageContext session) {
    OutboundMessageQueue queue = session.getOutboundMessageQueue();

    // parse the search message into a SearchParameters object
    int totalTokens = m.numDataStringTokens();
    SearchParameters params = getSearchParams(m, totalTokens);

    try {
      List<SearchResult> results = searchService.searchForShares(params);
      if (results != null) {
        for (SearchResult sr : results) {
          StringBuffer msgBuf = getStringBuffer(sr);
          queue.queueMessage(new Message(MessageTypes.SERVER_SEARCH_RESPONSE, msgBuf.toString()));
        }
      }
      queue.queueMessage(endSearchResultsMessage);
    } catch (InvalidatedQueueException iqe) {
    }
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    // return List.of(MessageTypes.CLIENT_SEARCH_REQUEST);
    return List.of();
  }
}
