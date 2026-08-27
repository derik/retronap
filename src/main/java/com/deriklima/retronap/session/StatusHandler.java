package com.deriklima.retronap.session;

import com.deriklima.retronap.message.*;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.UserLoggedInChecker;
import com.deriklima.retronap.statistics.StatisticsMaintainer;
import java.util.List;
import org.springframework.stereotype.Component;

/** Handles Status request messages */
@Component
public class StatusHandler extends MessageHandler {
  private final StatisticsMaintainer statisticsMaintainer;

  public StatusHandler(StatisticsMaintainer statisticsMaintainer) {
    this.statisticsMaintainer = statisticsMaintainer;
    addPreConditionChecker(new UserLoggedInChecker());
    // no checker should be here for validating messages:
    // this handler is used by a separate thread to send occasional
    // status update messages, and spoofs the message (null).
  }

  public void processMessage(Message m, MessageContext session) {
    StringBuilder buf = new StringBuilder(15);
    buf.append(statisticsMaintainer.getUserCount());
    buf.append(" ");
    buf.append(statisticsMaintainer.getFileCount());
    buf.append(" ");
    buf.append(statisticsMaintainer.getTotalLibrarySizeInGigs());

    OutboundMessageQueue out = session.getOutboundMessageQueue();
    if (out != null) {
      try {
        out.queueMessage(new Message(MessageTypes.SERVER_STATS, buf.toString()));
      } catch (InvalidatedQueueException iqe) {
      }
    }
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.SERVER_STATS);
  }
}
