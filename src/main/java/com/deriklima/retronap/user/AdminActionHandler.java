package com.deriklima.retronap.user;

import com.deriklima.retronap.message.*;
import com.deriklima.retronap.message.LevelAtLeastAdminChecker;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.UserLoggedInChecker;
import com.deriklima.retronap.message.ValidMessageChecker;
import com.deriklima.retronap.model.User;
import com.deriklima.retronap.model.UserLevelTypes;
import com.deriklima.retronap.session.SessionManager;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Handles various admin requests. */
@Slf4j
@Component
public class AdminActionHandler extends MessageHandler {

  private final UserPersistenceStore userPersistenceStore;
  private final SessionManager sessionManager;
  private final PasswordEncoder passwordEncoder;

  public AdminActionHandler(
      final UserPersistenceStore userPersistenceStore,
      final SessionManager sessionManager,
      PasswordEncoder passwordEncoder) {
    this.userPersistenceStore = userPersistenceStore;
    this.sessionManager = sessionManager;
    addPreConditionChecker(new UserLoggedInChecker());
    addPreConditionChecker(new ValidMessageChecker());
    addPreConditionChecker(new LevelAtLeastAdminChecker());
    this.passwordEncoder = passwordEncoder;
  }

  protected void processMessage(Message m, MessageContext session) {
    switch (m.getType()) {
      case MessageTypes.CLIENT_ADMIN_DISCONNECT -> {
        String user = m.getDataString(0);
        MessageContext targetSession = sessionManager.searchForSession(user);
        if (targetSession != null) {
          targetSession.kill();
        }
      }
      case MessageTypes.CLIENT_CHANGE_USER_LEVEL -> {
        String user = m.getDataString(0);
        String newLevelStr = m.getDataString(1);
        UserLevelTypes newLevel = UserLevelTypes.fromType(newLevelStr);

        MessageContext targetSession = sessionManager.searchForSession(user);
        User u;

        if (targetSession != null) {
          u = targetSession.getUser();
        } else {
          u = userPersistenceStore.findByNickname(user);
        }

        if (u != null) {
          u.setLevel(newLevel);
          userPersistenceStore.save(u);
        }
      }
      case MessageTypes.CLIENT_CHANGE_USER_PASSWORD -> {
        String user = m.getDataString(0);
        String newPassword = m.getDataString(1);

        User u;

        MessageContext targetSession = sessionManager.searchForSession(user);
        if (targetSession != null) {
          u = targetSession.getUser();
        } else {
          // not logged in, so get straight from user file
          u = userPersistenceStore.findByNickname(user);
        }
        if (u != null) {
          u.setPassword(passwordEncoder.encode(newPassword));
          userPersistenceStore.save(u);
        }
      }
      case MessageTypes.CLIENT_ADMIN_NUKE -> {
        String user = m.getDataString(0);

        MessageContext targetSession = sessionManager.searchForSession(user);
        if (targetSession != null) {
          targetSession.kill();
        }

        User u = userPersistenceStore.findByNickname(user);
        if (u != null) {
          userPersistenceStore.delete(u.getId());
        }
      }
      case MessageTypes.CLIENT_ADMIN_GLOBAL_MESSAGE -> {
        Collection<MessageContext> sessions = sessionManager.getSessions();
        String msgText = m.getDataString();
        msgText = msgText.substring(msgText.indexOf(' ') + 1);
        msgText = session.getUser().getNickname() + " " + msgText;
        Message globalMsg = new Message(MessageTypes.CLIENT_ADMIN_GLOBAL_MESSAGE, msgText);
        for (MessageContext s : sessions) {
          OutboundMessageQueue queue = s.getOutboundMessageQueue();
          try {
            queue.queueMessage(globalMsg);
          } catch (InvalidatedQueueException iqe) {
            // Ignore, the queue is no longer valid
          }
        }
      }
    }
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(
        MessageTypes.CLIENT_ADMIN_DISCONNECT,
        MessageTypes.CLIENT_CHANGE_USER_LEVEL,
        MessageTypes.CLIENT_ADMIN_NUKE,
        MessageTypes.CLIENT_ADMIN_GLOBAL_MESSAGE,
        MessageTypes.CLIENT_CHANGE_USER_PASSWORD);
  }
}
