package com.deriklima.retronap.user;

import com.deriklima.retronap.message.InvalidatedQueueException;
import com.deriklima.retronap.message.Message;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.MessageHandler;
import com.deriklima.retronap.message.MessageTypes;
import com.deriklima.retronap.model.User;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CheckPassHandler extends MessageHandler {
  private final PasswordEncoder passwordEncoder;
  private final UserPersistenceStore userPersistenceStore;

  // TODO: Implement checkers
  // addPreConditionChecker

  @Override
  protected void processMessage(Message m, MessageContext session) {
    String username = m.getDataString(0);
    String password = m.getDataString(1);
    // Parse m.getDataString() // Format: user1 123
    // Retrieve user
    User user;
    try {
      user = userPersistenceStore.findByNickname(username);
    } catch (UserNotFoundException e) {
      try {
        session
            .getOutboundMessageQueue()
            .queueMessage(new Message(MessageTypes.SERVER_ERROR, "User not found"));
      } catch (InvalidatedQueueException iqe) {
        throw new RuntimeException(e);
      }
      return;
    }
    // Check if password matches
    if (user.getPassword() != null && passwordEncoder.matches(password, user.getPassword())) {
      MessageContext userState = session;
      userState.setLoggedIn();
      userState.setUser(user);
      try {
        session
            .getOutboundMessageQueue()
            .queueMessage(new Message(MessageTypes.SERVER_PASS_OK, ""));
      } catch (InvalidatedQueueException e) {
        throw new RuntimeException(e);
      }
    } else {
      try {
        // TODO:
        //  Still not clear which message type should be returned when password is wrong
        //  According to this: https://www.winmxworld.com/tutorials/opennap_protocol_ref_2.html
        //  "This command is used by Napster only during setup sequence.
        //   If password is correct server replies with message 12. If not
        //   server sends message 0."
        session
            .getOutboundMessageQueue()
            .queueMessage(new Message(MessageTypes.SERVER_ERROR, "Incorrect Password."));
      } catch (InvalidatedQueueException e) {
        throw new RuntimeException(e);
      }
    }
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.CLIENT_CHECK_PASS);
  }
}
