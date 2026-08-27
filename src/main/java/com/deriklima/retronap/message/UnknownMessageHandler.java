package com.deriklima.retronap.message;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Handler used when an unknown message type is received. */
@Component
@Slf4j
public class UnknownMessageHandler extends MessageHandler {
  /* Not adding condition checkers, we want to handle all unknown messages without
  using inherited preconditions */

  protected void processMessage(Message m, MessageContext session) {
    log.warn("Received unknown message: {}", m.getType());

    OutboundMessageQueue queue = session.getOutboundMessageQueue();
    try {
      queue.queueMessage(
          new Message(
              MessageTypes.SERVER_GENERAL_ERROR,
              "Don't know how to handle that message type " + m.getType()));
    } catch (InvalidatedQueueException iqe) {
    }
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of();
  }
}
