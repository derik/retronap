package com.deriklima.retronap.user;

import com.deriklima.retronap.message.*;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.model.User;
import com.deriklima.retronap.model.UserLevelTypes;
import com.deriklima.retronap.session.*;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Handles alt login messages */
@Component
@Slf4j
public class AltLoginHandler extends MessageHandler {

  private final UserPersistenceStore userPersistenceStore;
  private final MessageOfTheDayHandler messageOfTheDayHandler;
  private final PasswordEncoder passwordEncoder;

  @Setter @Getter
  private Message persistenceError =
      new Message(MessageTypes.SERVER_ERROR, "Persistence error occured.");

  @Setter @Getter
  private Message loginError =
      new Message(MessageTypes.SERVER_ERROR, "Login error.(no account or bad password)");

  public AltLoginHandler(
      UserPersistenceStore userPersistenceStore,
      MessageOfTheDayHandler messageOfTheDayHandler,
      PasswordEncoder passwordEncoder) {
    this.userPersistenceStore = userPersistenceStore;
    this.messageOfTheDayHandler = messageOfTheDayHandler;
    this.passwordEncoder = passwordEncoder;
    addPreConditionChecker(new ValidMessageChecker());
  }

  protected void processMessage(Message m, MessageContext session) {
    // <nick> <pass> <port> \"<client-info>\" <linkspeed> <email-address>
    String nickname = m.getDataString(0);
    String password = m.getDataString(1);
    int port = Integer.parseInt(m.getDataString(2));
    String clientInfo = m.getDataString(3);
    int linkSpeed = Integer.parseInt(m.getDataString(4));
    String email = m.getDataString(5);

    User u = new User();

    u.setNickname(nickname);
    u.setPassword(passwordEncoder.encode(password));
    u.setEmail(email);
    u.setLevel(UserLevelTypes.USER);
    u.setLinkSpeedValue(linkSpeed);
    u.setClientInfo(clientInfo);
    u.setDataPort(port);

    MessageContext userState = session;
    userState.setLoggedIn();
    userState.setUser(u);

    u.setIpAddress(userState.getIPAddress());
    userPersistenceStore.save(u);

    OutboundMessageQueue queue = session.getOutboundMessageQueue();

    try {
      queue.queueMessage(new Message(MessageTypes.SERVER_LOGIN_ACK, email));
    } catch (InvalidatedQueueException iqe) {
    }

    // send motd
    messageOfTheDayHandler.handleMessage(m, session);

    session.startStatusUpdateThread();
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.CLIENT_ALT_LOGIN_REQUEST);
  }
}
