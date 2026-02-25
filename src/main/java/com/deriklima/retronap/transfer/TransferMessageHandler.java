package com.deriklima.retronap.transfer;

import com.deriklima.retronap.message.Message;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.MessageHandler;
import com.deriklima.retronap.message.MessageTypes;
import com.deriklima.retronap.message.UserLoggedInChecker;
import com.deriklima.retronap.message.ValidMessageChecker;
import com.deriklima.retronap.model.User;
import com.deriklima.retronap.user.UserService;
import java.util.List;
import org.springframework.stereotype.Component;

/** Handles upload and download notification messages */
@Component
public class TransferMessageHandler extends MessageHandler {

  private final UserService userService;

  public TransferMessageHandler(UserService userService) {
    this.userService = userService;
    addPreConditionChecker(new UserLoggedInChecker());
    addPreConditionChecker(new ValidMessageChecker());
  }

  protected void processMessage(Message m, MessageContext session) {
    User user = session.getUser();

    switch (m.getType()) {
      case MessageTypes.CLIENT_DOWNLOAD_NOTIFICATION:
        {
          userService.incrementDownloadCount(user.getId());
          break;
        }
      case MessageTypes.CLIENT_DOWNLOAD_COMPLETE_NOTIFICATION:
        {
          userService.decrementDownloadCount(user.getId());
          break;
        }
      case MessageTypes.CLIENT_UPLOAD_NOTIFICATION:
        {
          userService.incrementUploadCount(user.getId());
          break;
        }
      case MessageTypes.CLIENT_UPLOAD_COMPLETE_NOTIFICATION:
        {
          userService.decrementUploadCount(user.getId());
          break;
        }
    }
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(
        MessageTypes.CLIENT_DOWNLOAD_NOTIFICATION,
        MessageTypes.CLIENT_DOWNLOAD_COMPLETE_NOTIFICATION,
        MessageTypes.CLIENT_UPLOAD_NOTIFICATION,
        MessageTypes.CLIENT_UPLOAD_COMPLETE_NOTIFICATION);
  }
}
