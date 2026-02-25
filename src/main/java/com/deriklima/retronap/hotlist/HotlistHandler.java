package com.deriklima.retronap.hotlist;

import com.deriklima.retronap.message.*;
import com.deriklima.retronap.session.SessionManager;
import com.deriklima.retronap.user.UserNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Handles msgs relating to hotlists */
@Component
@Slf4j
public class HotlistHandler extends MessageHandler {
  private static final int HOTLIST_CREATE_LOOP_THRESHOLD = 5;
  private static final long HOTLIST_CREATE_LOOP_WINDOW_MS = 250;
  private static final long HOTLIST_CREATE_SUPPRESS_MS = 5_000;
  private static final long HOTLIST_CREATE_ACK_THROTTLE_MS = 100;

  private final Map<UUID, HotlistCreateLoopState> hotlistCreateLoopState =
      new ConcurrentHashMap<>();
  private final Hotlist hotlist;

  public HotlistHandler(Hotlist hotlist, SessionManager sessionManager) {
    this.hotlist = hotlist;
    addPreConditionChecker(new ValidMessageChecker());
    addPreConditionChecker(new UserLoggedInChecker());
  }

  protected void processMessage(Message m, MessageContext session) {
    switch (m.getType()) {
      case MessageTypes.CLIENT_HOTLIST_CREATE:
        {
          if (shouldSuppressHotlistCreate(session, m)) {
            maybeQueueSyntheticCreateAck(session, m);
            return;
          }

          int numTokens = m.numDataStringTokens();
          for (int x = 0; x < numTokens; x++) {
            String nickToAdd = m.getDataString(x);
            try {
              hotlist.addStalkerForUser(session, nickToAdd);
            } catch (UserNotFoundException e) {
              queueHotlistAck(session, nickToAdd);
            }
          }
          break;
        }
      case MessageTypes.CLIENT_HOTLIST_ADD:
        {
          String nickToAdd = m.getDataString(0);
          hotlist.addStalkerForUser(session, nickToAdd);
          break;
        }
      case MessageTypes.CLIENT_HOTLIST_REMOVE:
        {
          String nickToRemove = m.getDataString(0);
          hotlist.removeStalkerForUser(session.getUser().getId(), nickToRemove);
          break;
        }
    } // end switch
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(
        MessageTypes.CLIENT_HOTLIST_CREATE,
        MessageTypes.CLIENT_HOTLIST_ADD,
        MessageTypes.CLIENT_HOTLIST_REMOVE);
  }

  private boolean shouldSuppressHotlistCreate(MessageContext session, Message message) {
    UUID userId = session.getUserId();
    if (userId == null) {
      return false;
    }

    long now = System.currentTimeMillis();
    String payload = message.getDataString();
    HotlistCreateLoopState state =
        hotlistCreateLoopState.computeIfAbsent(userId, ignored -> new HotlistCreateLoopState());

    synchronized (state) {
      if (now < state.suppressedUntilMillis && payload.equals(state.lastPayload)) {
        return true;
      }

      if (payload.equals(state.lastPayload)
          && now - state.windowStartMillis <= HOTLIST_CREATE_LOOP_WINDOW_MS) {
        state.repeatCount++;
      } else {
        state.lastPayload = payload;
        state.windowStartMillis = now;
        state.repeatCount = 1;
      }

      if (state.repeatCount >= HOTLIST_CREATE_LOOP_THRESHOLD) {
        state.suppressedUntilMillis = now + HOTLIST_CREATE_SUPPRESS_MS;
        log.warn(
            "Detected CLIENT_HOTLIST_CREATE loop for user '{}' and payload '{}'; suppressing for {} ms",
            session.getNickname(),
            payload,
            HOTLIST_CREATE_SUPPRESS_MS);
        return true;
      }
    }

    return false;
  }

  private void maybeQueueSyntheticCreateAck(MessageContext session, Message message) {
    UUID userId = session.getUserId();
    if (userId == null) {
      return;
    }

    HotlistCreateLoopState state = hotlistCreateLoopState.get(userId);
    if (state == null) {
      return;
    }

    long now = System.currentTimeMillis();
    synchronized (state) {
      if (now - state.lastSyntheticAckMillis < HOTLIST_CREATE_ACK_THROTTLE_MS) {
        return;
      }
      state.lastSyntheticAckMillis = now;
    }

    int numTokens = message.numDataStringTokens();
    for (int x = 0; x < numTokens; x++) {
      queueHotlistAck(session, message.getDataString(x));
    }
  }

  private void queueHotlistAck(MessageContext session, String nickToAdd) {
    OutboundMessageQueue queue = session.getOutboundMessageQueue();
    try {
      queue.queueMessage(new Message(MessageTypes.SERVER_HOTLIST_ACK, nickToAdd));
    } catch (InvalidatedQueueException ignored) {
    }
  }

  private static final class HotlistCreateLoopState {
    private String lastPayload = "";
    private long windowStartMillis;
    private int repeatCount;
    private long suppressedUntilMillis;
    private long lastSyntheticAckMillis;
  }
}
