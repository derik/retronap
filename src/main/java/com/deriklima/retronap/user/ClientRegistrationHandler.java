package com.deriklima.retronap.user;

import com.deriklima.retronap.message.*;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.ValidMessageChecker;
import com.deriklima.retronap.model.User;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles client registration messages. Really only checks that the nickname requested is
 * available.
 */
@Component
@Slf4j
public class ClientRegistrationHandler extends MessageHandler {

  private final UserPersistenceStore userPersistenceStore;

  private final Message persistenceError =
      new Message(MessageTypes.SERVER_ERROR, "Persistence error occured.");
  private final Message registrationSuccess =
      new Message(MessageTypes.SERVER_REGISTRATION_SUCCESS, "");
  private final Message nicknameAlreadyRegisteredError =
      new Message(MessageTypes.SERVER_NICKNAME_ALREADY_REGISTERED, "");

  public ClientRegistrationHandler(UserPersistenceStore userPersistenceStore) {
    this.userPersistenceStore = userPersistenceStore;
    addPreConditionChecker(new ValidMessageChecker());
  }

  protected void processMessage(Message m, MessageContext session) {
    String nickname = m.getDataString(0);

    User u;
    try {
      u = userPersistenceStore.findByNickname(nickname);
    } catch (UserNotFoundException e) {
      u = null;
    }

    OutboundMessageQueue queue = session.getOutboundMessageQueue();
    Message outMessage = null;

    if (u == null) {
      outMessage = registrationSuccess;
    } else {
      outMessage = nicknameAlreadyRegisteredError;
    }

    try {
      queue.queueMessage(outMessage);
    } catch (InvalidatedQueueException iqe) {
    }
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.CLIENT_REGISTRATION);
  }
}
