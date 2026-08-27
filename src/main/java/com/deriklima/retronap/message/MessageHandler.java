package com.deriklima.retronap.message;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementations of MessageHandlers should know what to do with the specific Message it is
 * expecting.
 */
public abstract class MessageHandler {
  private final List<ConditionChecker> preConditionCheckers = new ArrayList<>();
  private final List<ConditionChecker> postConditionCheckers = new ArrayList<>();

  public void addPreConditionChecker(ConditionChecker pch) {
    preConditionCheckers.add(pch);
  }

  public void addPostConditionChecker(ConditionChecker pch) {
    postConditionCheckers.add(pch);
  }

  public void handleMessage(Message message, MessageContext session) {

    if (checkConditions(preConditionCheckers, message, session)) {
      processMessage(message, session);
      checkConditions(postConditionCheckers, message, session);
    }
  }

  /* Each sublcass must override this method to 'handle' their associated message */
  protected abstract void processMessage(Message m, MessageContext session);

  /** Returns a list of message types this handler can process. */
  public abstract List<Integer> getHandledMessageTypes();

  /* Cycles through all pre or post condition handlers.  If a pre or post condition fails,
  this method will send an error message to the client, log the error as a WARNING. */
  private boolean checkConditions(
      List<ConditionChecker> conditionCheckers, Message message, MessageContext session) {
    if (conditionCheckers == null) return true;

    for (ConditionChecker cc : conditionCheckers) {
      if (!cc.checkCondition(session, message)) {
        try {
          OutboundMessageQueue queue = session.getOutboundMessageQueue();
          Message errorMsg = cc.getErrorMessage();
          queue.queueMessage(errorMsg);
          //          log.warn("Received message: {}", message + "\n\t" + errorMsg.getDataString());
        } catch (InvalidatedQueueException ignored) {
        }
        return false;
      }
    }
    return true;
  }
}
