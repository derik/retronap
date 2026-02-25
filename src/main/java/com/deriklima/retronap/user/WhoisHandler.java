package com.deriklima.retronap.user;

import com.deriklima.retronap.message.*;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.model.User;
import com.deriklima.retronap.model.UserLevelTypes;
import com.deriklima.retronap.search.SharedFileService;
import com.deriklima.retronap.session.SessionManager;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Handles Whois messages */
@Slf4j
@Component
public class WhoisHandler extends MessageHandler {
  private final UserPersistenceStore userPersistenceStore;
  private final SessionManager sessionManager;
  private final UserChannelProvider userChannelProvider;
  private final SharedFileService sharedFileService;

  public WhoisHandler(
      UserPersistenceStore userPersistenceStore,
      SessionManager sessionManager,
      UserChannelProvider userChannelProvider,
      SharedFileService sharedFileService) {
    this.userPersistenceStore = userPersistenceStore;
    this.sessionManager = sessionManager;
    this.userChannelProvider = userChannelProvider;
    this.sharedFileService = sharedFileService;
    addPreConditionChecker(new ValidMessageChecker());
    addPreConditionChecker(new UserLoggedInChecker());
  }

  protected void processMessage(Message m, MessageContext session) {
    String nickname = m.getDataString(0);

    MessageContext targetSession = sessionManager.searchForSession(nickname);

    if (targetSession != null) {
      User user = targetSession.getUser();
      StringBuilder msg = new StringBuilder(160);

      StringBuilder channelNames = new StringBuilder(50);
      String[] channels = userChannelProvider.getChannelNamesForUser(nickname);
      if (channels != null) {
        boolean firstTime = true;
        for (String channelName : channels) {
          if (!firstTime) {
            channelNames.append(" ");
          }
          channelNames.append(channelName);
          firstTime = false;
        }
      }

      // <nick> "<user-level>" <time> "<channels>" <status> <shared>
      // <downloads> <uploads> <link-type> "<client-info>"
      msg.append(nickname);
      msg.append(" \"");
      if (UserLevelTypes.USER.equals(user.getLevel())) {
        msg.append("User");
      } else {
        msg.append("Admin");
      }
      msg.append("\" ");
      msg.append(targetSession.getTimeConnected());
      msg.append(" ");
      msg.append("\"");
      msg.append(channelNames);
      msg.append("\"");
      msg.append(" \"Active.\" ");
      msg.append(sharedFileService.countByUserId(user.getId()));
      msg.append(" ");
      msg.append(user.getDownloads());
      msg.append(" ");
      msg.append(user.getUploads());
      msg.append(" ");
      msg.append(user.getLinkSpeedValue());
      msg.append(" \"");
      msg.append(user.getClientInfo());
      msg.append("\"");

      Message response = new Message(MessageTypes.SERVER_WHOIS_RESPONSE, msg.toString());
      OutboundMessageQueue out = session.getOutboundMessageQueue();
      try {
        out.queueMessage(response);
      } catch (InvalidatedQueueException iqe) {
      }
    } else // user's not logged in right now
    {
      User u = userPersistenceStore.findByNickname(nickname);

      if (u != null) {
        String msg = nickname + " " + u.getLevel() + " " + u.getLastSeen();
        Message response = new Message(MessageTypes.SERVER_WHOWAS_RESPONSE, msg);
        OutboundMessageQueue out = session.getOutboundMessageQueue();
        try {
          out.queueMessage(response);
        } catch (InvalidatedQueueException iqe) {
        }
      }
    }
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.CLIENT_WHOIS_REQUEST);
  }
}
