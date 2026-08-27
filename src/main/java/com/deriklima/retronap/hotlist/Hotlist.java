package com.deriklima.retronap.hotlist;

import com.deriklima.retronap.message.InvalidatedQueueException;
import com.deriklima.retronap.message.Message;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.MessageTypes;
import com.deriklima.retronap.message.OutboundMessageQueue;
import com.deriklima.retronap.model.User;
import com.deriklima.retronap.session.SessionEvent;
import com.deriklima.retronap.session.SessionManager;
import com.deriklima.retronap.user.UserPersistenceStore;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * This class encapsulates functionality for "hotlist" feature. For lack of a better vocabulary:
 * target: user who is on a hotlist stalker: user who should be notified when a target signs on/off
 *
 * <p>Checks the status of stalkers on demand
 *
 * <p>Uses Spring's event system to handle session events
 */
@Slf4j
@Component
public class Hotlist {
  private final HotlistService hotlistService;
  private final UserPersistenceStore userPersistenceStore;

  private final SessionManager sessionManager;

  public Hotlist(
      HotlistService hotlistService,
      UserPersistenceStore userPersistenceStore,
      SessionManager sessionManager) {
    this.hotlistService = hotlistService;
    this.userPersistenceStore = userPersistenceStore;
    this.sessionManager = sessionManager;
  }

  /** registers a stalker as listening for sign-on and sign-off events for nickName */
  @Transactional
  public void addStalkerForUser(MessageContext stalkerMessageContext, String nickToAdd) {
    // check existence of nickName.
    User userToAdd = userPersistenceStore.findByNickname(nickToAdd);
    if (userToAdd == null) {
      OutboundMessageQueue queue = stalkerMessageContext.getOutboundMessageQueue();
      try {
        queue.queueMessage(new Message(MessageTypes.SERVER_HOTLIST_ERROR, nickToAdd));
      } catch (InvalidatedQueueException ignored) {
      }
      return;
    }

    hotlistService.save(
        new HotlistEntry(userToAdd.getId(), stalkerMessageContext.getUser().getId()));

    OutboundMessageQueue queue = stalkerMessageContext.getOutboundMessageQueue();
    try {
      queue.queueMessage(new Message(MessageTypes.SERVER_HOTLIST_ACK, nickToAdd));
    } catch (InvalidatedQueueException iqe) {
    }

    // check if nickName is logged in; send msg immediately
    MessageContext targetMessageContext = sessionManager.searchForSession(nickToAdd);
    if (targetMessageContext != null) {
      notifySignonSingle(
          stalkerMessageContext, nickToAdd, targetMessageContext.getUser().getLinkSpeedValue());
    }
  }

  /** removes stalker from listening for sign-on and sign-off events for user */
  public void removeStalkerForUser(UUID stalkerId, User user) {
    hotlistService.removeStalkerForUser(user.getId(), stalkerId);
  }

  public void removeStalkerForUser(UUID id, String nickToRemove) {
    User user = userPersistenceStore.findByNickname(nickToRemove);
    if (user != null) {
      removeStalkerForUser(id, user);
    }
  }

  /**
   * returns array of nicknames who are listening for sign-on and sign-off events for user NOTE:
   * this method has no knowledge of whether names in list returned are actually still logged in.
   */
  private List<String> getStalkersForUser(User user) {
    return hotlistService.findByTargetUserId(user.getId()).stream()
        .map(hotlistEntry -> userPersistenceStore.findNicknameById(hotlistEntry.getStalkerUserId()))
        .flatMap(Optional::stream)
        .toList();
  }

  /**
   * Returns list of valid sessions who are listening for events for user calls getStalkersForUser()
   * and filters out those nicks who are not logged in anymore, removing them from internal data
   * structures.
   */
  public MessageContext[] getStalkerMessageContextsForUser(User user) {
    List<String> stalkers = getStalkersForUser(user);
    if (!stalkers.isEmpty()) {
      List<MessageContext> sessions = new java.util.ArrayList<>();
      for (String stalkerName : stalkers) {
        MessageContext session = sessionManager.searchForSession(stalkerName);
        if (session != null) {
          sessions.add(session);
        } else {
          User stalker = userPersistenceStore.findByNickname(stalkerName);
          if (stalker != null) {
            removeStalkerForUser(stalker.getId(), user);
          }
        }
      }
      return sessions.toArray(new MessageContext[0]);
    }
    return new MessageContext[0];
  }

  /** Sends sign-on notification to all users who added nickname to their hotlists */
  public void notifySignon(User user) {
    MessageContext[] sessionsToNotify = getStalkerMessageContextsForUser(user);
    for (MessageContext singleMessageContext : sessionsToNotify) {
      notifySignonSingle(singleMessageContext, user.getNickname(), user.getLinkSpeedValue());
    }
  }

  /** Sends a single msg to sessionToNotify, that nickname has signed on with linkType */
  public void notifySignonSingle(MessageContext sessionToNotify, String nickname, int linkType) {
    String msg = nickname + " " + linkType;
    OutboundMessageQueue queue = sessionToNotify.getOutboundMessageQueue();
    try {
      queue.queueMessage(new Message(MessageTypes.SERVER_HOTLIST_SIGNON, msg));
    } catch (InvalidatedQueueException ignored) {
    }
  }

  /** Sends sign-off notification to all users who added nickname to their hotlists */
  public void notifySignoff(User user) {
    MessageContext[] sessionsToNotify = getStalkerMessageContextsForUser(user);
    for (MessageContext singleMessageContext : sessionsToNotify) {
      notifySignoffSingle(singleMessageContext, user.getNickname());
    }
  }

  /** Sends a single msg to sessionToNotify, that nickname has signed off */
  public void notifySignoffSingle(MessageContext sessionToNotify, String nickname) {
    OutboundMessageQueue queue = sessionToNotify.getOutboundMessageQueue();
    try {
      queue.queueMessage(new Message(MessageTypes.SERVER_HOTLIST_SIGNOFF, nickname));
    } catch (InvalidatedQueueException ignored) {
    }
  }

  @EventListener
  public void handleSessionEvent(SessionEvent se) {
    User user = se.getSession().getUser();
    if (user == null) {
      return;
    }
    switch (se.getType()) {
      case SessionEvent.TERMINATE -> notifySignoff(user);
      case SessionEvent.SIGNON -> notifySignon(user);
    }
  }
}
