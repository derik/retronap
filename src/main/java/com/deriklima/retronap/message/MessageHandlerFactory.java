package com.deriklima.retronap.message;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * A factory that provides the correct {@link MessageHandler} for a given message type. It discovers
 * all MessageHandler beans from the Spring context and maps them by the message types they handle.
 */
@Component
public class MessageHandlerFactory {
  private final Map<Integer, MessageHandler> typeToHandlerMappings = new ConcurrentHashMap<>();
  private final MessageHandler unknownMessageHandler;

  /**
   * Constructs the factory by injecting all available MessageHandler beans from the Spring context.
   *
   * @param handlers A list of all beans that extend {@link MessageHandler}.
   * @param unknownMessageHandler The specific handler for unknown message types.
   */
  public MessageHandlerFactory(
      List<MessageHandler> handlers, UnknownMessageHandler unknownMessageHandler) {
    this.unknownMessageHandler = unknownMessageHandler;
    for (MessageHandler handler : handlers) {
      for (Integer type : handler.getHandledMessageTypes()) {
        typeToHandlerMappings.put(type, handler);
      }
    }
  }

  public MessageHandler getHandlerForType(int type) {
    return typeToHandlerMappings.getOrDefault(type, unknownMessageHandler);
  }
}
