package com.deriklima.retronap.search;

import com.deriklima.retronap.message.ConditionChecker;
import com.deriklima.retronap.message.Message;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.MessageHandler;
import com.deriklima.retronap.message.MessageTypes;
import com.deriklima.retronap.message.UserLoggedInChecker;
import com.deriklima.retronap.model.SharedFile;
import com.deriklima.retronap.model.User;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles client directory share notification messages from client. (new in windows Napster Beta6
 * client)
 */
@Component
@Slf4j
public class ClientDirShareHandler extends MessageHandler {

  private final SharedFileService sharedFileService;

  public ClientDirShareHandler(SharedFileService sharedFileService) {
    this.sharedFileService = sharedFileService;
    addPreConditionChecker(new UserLoggedInChecker());
    ConditionChecker validMessageChecker =
        new ConditionChecker() {
          public boolean checkCondition(MessageContext session, Message message) {
            if ((message.numDataStringTokens() - 1) % 6 != 0) {
              String errorMsg = "invalid number of tokens in message 870";
              if (!session.isLoggedIn())
                setErrorMessage(new Message(MessageTypes.SERVER_ERROR, errorMsg));
              else setErrorMessage(new Message(MessageTypes.SERVER_GENERAL_ERROR, errorMsg));
              return false;
            }
            return true;
          }
        };
    addPreConditionChecker(validMessageChecker);
  }

  protected void processMessage(Message m, MessageContext session) {
    String dir = m.getDataString(0);

    MessageContext userState = session;
    User user = userState.getUser();

    List<SharedFile> sharedFiles = new ArrayList<>();

    int x = 1;
    while (x < m.numDataStringTokens()) {
      String filename = m.getDataString(x);
      x++;
      String md5 = m.getDataString(x);
      x++;
      int size = Integer.parseInt(m.getDataString(x));
      x++;
      int bitrate = Integer.parseInt(m.getDataString(x));
      x++;
      int freq = Integer.parseInt(m.getDataString(x));
      x++;
      int time = Integer.parseInt(m.getDataString(x));
      x++;

      String absolutePath = dir + filename;
      SharedFile share =
          new SharedFile(dir, filename, absolutePath, md5, size, bitrate, freq, time);
      sharedFiles.add(share);

      userState.addShare(share);
    }
    sharedFileService.saveAll(sharedFiles, user.getId());
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.CLIENT_DIR_SHARE_NOTIFICATION);
  }
}
