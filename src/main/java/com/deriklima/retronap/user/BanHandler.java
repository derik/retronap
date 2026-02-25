package com.deriklima.retronap.user;

import com.deriklima.retronap.message.*;
import com.deriklima.retronap.message.LevelAtLeastAdminChecker;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.UserLoggedInChecker;
import com.deriklima.retronap.message.ValidMessageChecker;
import com.deriklima.retronap.util.Util;
import java.util.List;
import org.springframework.stereotype.Component;

/** Handles requests that have to do with bans. */
@Component
public class BanHandler extends MessageHandler {
  private final Banlist banlist;

  public BanHandler(Banlist banlist) {
    this.banlist = banlist;
    addPreConditionChecker(new UserLoggedInChecker());
    addPreConditionChecker(new ValidMessageChecker());
    addPreConditionChecker(new LevelAtLeastAdminChecker());
  }

  protected void processMessage(Message m, MessageContext session) {
    switch (m.getType()) {
      case MessageTypes.CLIENT_ADMIN_BAN_USER:
        {
          String nickOrIP = m.getDataString(0);
          long ip = Util.stringToLongIPAddress(nickOrIP);
          if (ip != -1) {
            banlist.addBannedIP(ip, session.getUser().getNickname(), "No reason given.");
          } else {
            banlist.addBannedNick(nickOrIP);
          }
          break;
        }
      case MessageTypes.CLIENT_ADMIN_UNBAN_USER:
        {
          String nickOrIP = m.getDataString(0);
          long ip = Util.stringToLongIPAddress(nickOrIP);
          if (ip != -1) {
            banlist.removeBannedIP(ip);
          } else {
            banlist.removeBannedNick(nickOrIP);
          }
          break;
        }
      case MessageTypes.CLIENT_ADMIN_SHOW_BANS:
        {
          BanRecord[] records = banlist.getBannedIPs();
          if (records != null) {
            int numRecords = records.length;
            for (BanRecord singleRecord : records) {
              StringBuilder msgBuf = new StringBuilder(80);
              msgBuf.append(Util.longToStringIPAddress(singleRecord.getIpAddress()));
              msgBuf.append(" ");
              msgBuf.append(singleRecord.getAdminNick());
              msgBuf.append(" \"");
              msgBuf.append(singleRecord.getReason());
              msgBuf.append("\" ");
              msgBuf.append(singleRecord.getTime());

              OutboundMessageQueue queue = session.getOutboundMessageQueue();
              try {
                queue.queueMessage(
                    new Message(MessageTypes.SERVER_BAN_LIST_ENTRY, msgBuf.toString()));
              } catch (InvalidatedQueueException iqe) {
              }
            }
          }
          String[] nicks = banlist.getBannedNicks();
          if (nicks != null) {
            int numRecords = nicks.length;
            for (String nick : nicks) {
              StringBuilder msgBuf = new StringBuilder(80);
              msgBuf.append(nick);
              OutboundMessageQueue queue = session.getOutboundMessageQueue();
              try {
                queue.queueMessage(
                    new Message(MessageTypes.SERVER_BAN_LIST_USER_ENTRY, msgBuf.toString()));
              } catch (InvalidatedQueueException iqe) {
              }
            }
          }
          break;
        }
    } // end switch
  } // end processMessage()

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(
        MessageTypes.CLIENT_ADMIN_BAN_USER,
        MessageTypes.CLIENT_ADMIN_UNBAN_USER,
        MessageTypes.CLIENT_ADMIN_SHOW_BANS);
  }
}
