package com.deriklima.retronap.user;

import com.deriklima.retronap.message.Message;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.MessageHandler;
import com.deriklima.retronap.message.MessageTypes;
import com.deriklima.retronap.message.UserLoggedInChecker;
import com.deriklima.retronap.message.ValidMessageChecker;
import com.deriklima.retronap.model.User;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Handles various change option requests */
@Component
@Slf4j
public class ChangeOptionHandler extends MessageHandler {

  private final PasswordEncoder passwordEncoder;
  private final UserPersistenceStore userPersistenceStore;

  public ChangeOptionHandler(
      PasswordEncoder passwordEncoder, UserPersistenceStore userPersistenceStore) {
    this.passwordEncoder = passwordEncoder;
    this.userPersistenceStore = userPersistenceStore;
    addPreConditionChecker(new UserLoggedInChecker());
    addPreConditionChecker(new ValidMessageChecker());
  }

  protected void processMessage(Message m, MessageContext session) {
    User user = session.getUser();
    switch (m.getType()) {
      case MessageTypes.CLIENT_CHANGE_DATAPORT:
        {
          String newDataPort = m.getDataString(0);
          user.setDataPort(Integer.parseInt(newDataPort));
          userPersistenceStore.save(user);
          break;
        }
      case MessageTypes.CLIENT_CHANGE_PASSWORD:
        {
          String password = m.getDataString(0);
          user.setPassword(passwordEncoder.encode(password));
          userPersistenceStore.save(user);
          break;
        }
      case MessageTypes.CLIENT_CHANGE_EMAIL:
        {
          String email = m.getDataString(0);
          user.setEmail(email);
          userPersistenceStore.save(user);
          break;
        }
      case MessageTypes.CLIENT_CHANGE_LINKSPEED:
        {
          String newLinkSpeed = m.getDataString(0);
          user.setLinkSpeedValue(Integer.parseInt(newLinkSpeed));
          userPersistenceStore.save(user);
          break;
        }
    }
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(
        MessageTypes.CLIENT_CHANGE_LINKSPEED,
        MessageTypes.CLIENT_CHANGE_DATAPORT,
        MessageTypes.CLIENT_CHANGE_EMAIL,
        MessageTypes.CLIENT_CHANGE_PASSWORD);
  }
}
