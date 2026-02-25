package com.deriklima.retronap.session;

import com.deriklima.retronap.message.Message;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.MessageHandler;
import com.deriklima.retronap.message.MessageTypes;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class VersionCheckHandler extends MessageHandler {

  @Override
  protected void processMessage(Message m, MessageContext session) {
    try {
      session
          .getOutboundMessageQueue()
          .queueMessage(new Message(MessageTypes.SERVER_VERSION_CHECK, ""));
    } catch (Exception e) {
      log.warn("Could not send version check message");
    }
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.SERVER_VERSION_CHECK);
  }
}
