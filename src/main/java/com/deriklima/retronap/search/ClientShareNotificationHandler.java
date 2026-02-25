package com.deriklima.retronap.search;

import com.deriklima.retronap.message.Message;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.MessageHandler;
import com.deriklima.retronap.message.MessageTypes;
import com.deriklima.retronap.message.UserLoggedInChecker;
import com.deriklima.retronap.message.ValidMessageChecker;
import com.deriklima.retronap.model.SharedFile;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Handles client share notification messages from client. */
@Component
@Slf4j
public class ClientShareNotificationHandler extends MessageHandler {

  private final SharedFileService sharedFileService;

  public ClientShareNotificationHandler(SharedFileService sharedFileService) {
    this.sharedFileService = sharedFileService;
    addPreConditionChecker(new UserLoggedInChecker());
    addPreConditionChecker(new ValidMessageChecker());
  }

  protected void processMessage(Message m, MessageContext session) {
    String absolutePath = m.getDataString(0);
    String md5 = m.getDataString(1);
    int size = Integer.parseInt(m.getDataString(2));
    int bitrate = Integer.parseInt(m.getDataString(3));
    int freq = Integer.parseInt(m.getDataString(4));
    int time = Integer.parseInt(m.getDataString(5));

    SharedFile share = new SharedFile("", "", absolutePath, md5, size, bitrate, freq, time);
    // Persist share ownership so DB insert satisfies non-null FK and search/browse are consistent.
    share.setUser(session.getUser());
    MessageContext userState = session;
    userState.addShare(share);
    sharedFileService.save(share);
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.CLIENT_SHARE_NOTIFICATION);
  }
}
