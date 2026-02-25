package com.deriklima.retronap.user;

import com.deriklima.retronap.message.Message;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.MessageHandler;
import com.deriklima.retronap.message.MessageTypes;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class IgnoreCommandHandler extends MessageHandler {
  @Override
  protected void processMessage(Message m, MessageContext session) {
    // Just ignore this message for now
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    // TODO: Implement these messages
    return List.of(MessageTypes.CLIENT_CHECK_PORT);
  }
}
