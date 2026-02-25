package com.deriklima.retronap.user;

import com.deriklima.retronap.message.*;
import com.deriklima.retronap.model.User;
import com.deriklima.retronap.session.SessionEvent;
import com.deriklima.retronap.util.Util;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * This handler handles login messages by an "OpenDoor" login policy: User's account name is checked
 * against the user database. If an account doesn't exist: user is allowed to log in, regardless of
 * password, with user privileges. If an account does exist: user is allowed to log in ONLY if
 * password supplied matches, ie. an ordinary login process.
 */
@Component
@ConditionalOnProperty(name = "retronap.policy.opendoor", havingValue = "true")
@Slf4j
public class OpenDoorLoginHandler extends MessageHandler {

  private final ApplicationEventPublisher publisher;

  private final Message loginError =
      new Message(MessageTypes.SERVER_ERROR, "Login error.(no account or bad password)");
  private final Message banError =
      new Message(MessageTypes.SERVER_ERROR, "You have been banned from using this server.");

  private final PasswordEncoder passwordEncoder;
  private final UserPersistenceStore userPersistenceStore;
  private final Banlist banlist;
  private final MessageHandlerFactory messageHandlerFactory;

  public OpenDoorLoginHandler(
      ApplicationEventPublisher publisher,
      PasswordEncoder passwordEncoder,
      UserPersistenceStore userPersistenceStore,
      Banlist banlist,
      MessageHandlerFactory messageHandlerFactory) {
    this.publisher = publisher;
    this.passwordEncoder = passwordEncoder;
    this.userPersistenceStore = userPersistenceStore;
    this.banlist = banlist;
    this.messageHandlerFactory = messageHandlerFactory;
    addPreConditionChecker(new ValidMessageChecker());
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.CLIENT_LOGIN_REQUEST);
  }

  protected void processMessage(Message m, MessageContext session) {
    String nickname = m.getDataString(0);
    String clientInfo = m.getDataString(3);
    int linkType = Integer.parseInt(m.getDataString(4));
    int dataPort = Integer.parseInt(m.getDataString(2));

    User u = userPersistenceStore.findByNickname(nickname);

    boolean banned = false;
    boolean loginSuccess = true;
    boolean realUser = false;
    String email = "";

    if (u != null) {
      realUser = true;
      String password = m.getDataString(1);
      if (!passwordEncoder.matches(password, u.getPassword())) {
        loginSuccess = false;
      }
    } else {
      u = new User();
      u.setNickname(nickname);
      u.setEmail("tempuser");
    }

    if ((!banlist.isBannedNick(u.getNickname()))
        && (!banlist.isBannedIP(Util.byteArrayToLongIPAddress(session.getIPAddress())))) {
      u.setClientInfo(clientInfo);
      u.setLinkSpeedValue(linkType);
      u.setDataPort(dataPort);

      MessageContext userState = session;
      userState.setLoggedIn();
      userState.setUser(u);
      email = u.getEmail();

      u.setLastSeen(System.currentTimeMillis() / 1000);
      if (realUser) {
        userPersistenceStore.save(u);
        loginSuccess = true;
      }
    } else {
      banned = true;
    }

    OutboundMessageQueue queue = session.getOutboundMessageQueue();
    Message outMessage;

    if (loginSuccess) {
      outMessage = new Message(MessageTypes.SERVER_LOGIN_ACK, email);
    } else {
      if (banned) {
        outMessage = banError;
      } else {
        outMessage = loginError;
      }
    }

    try {
      queue.queueMessage(outMessage);
    } catch (InvalidatedQueueException iqe) {
    }

    if (loginSuccess) {
      // send motd
      MessageHandler handler =
          messageHandlerFactory.getHandlerForType(MessageTypes.MESSAGE_OF_THE_DAY);
      handler.handleMessage(m, session);

      // start status update thread
      session.startStatusUpdateThread();

      // notify listeners
      publisher.publishEvent(new SessionEvent(session, SessionEvent.SIGNON));
    } else {
      session.kill();
    }
  }
}
