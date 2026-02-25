package com.deriklima.retronap.search;

import com.deriklima.retronap.message.Message;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.MessageHandler;
import com.deriklima.retronap.message.MessageTypes;
import com.deriklima.retronap.message.UserLoggedInChecker;
import com.deriklima.retronap.message.ValidMessageChecker;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Handles share removal messages from client. */
@Component
@Slf4j
public class ClientShareRemovalHandler extends MessageHandler {

  public ClientShareRemovalHandler() {
    addPreConditionChecker(new UserLoggedInChecker());
    addPreConditionChecker(new ValidMessageChecker());
  }

  protected void processMessage(Message m, MessageContext session) {
    String filename = m.getDataString(0);

    MessageContext userState = session;
    userState.removeShareByFilename(filename);
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.CLIENT_SHARE_REMOVAL);
  }
}
