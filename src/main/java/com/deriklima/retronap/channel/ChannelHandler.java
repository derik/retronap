package com.deriklima.retronap.channel;

import com.deriklima.retronap.message.InvalidatedQueueException;
import com.deriklima.retronap.message.Message;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.MessageHandler;
import com.deriklima.retronap.message.MessageTypes;
import com.deriklima.retronap.message.OutboundMessageQueue;
import com.deriklima.retronap.message.UserLoggedInChecker;
import com.deriklima.retronap.message.ValidMessageChecker;
import com.deriklima.retronap.model.UserLevelTypes;
import java.util.List;
import org.springframework.stereotype.Component;

/** Handles channel-related messages */
@Component
public class ChannelHandler extends MessageHandler {

  private static final Message atCapacityError =
      new Message(
          MessageTypes.SERVER_GENERAL_ERROR,
          "Channel is at capacity, you cannot join at this time.");
  private static final Message channelNameError =
      new Message(MessageTypes.SERVER_GENERAL_ERROR, "Invalid channel name.");
  private static final Message permissionError =
      new Message(
          MessageTypes.SERVER_GENERAL_ERROR,
          "You do not meet permission requirements necessary to join that channel.");

  private final ChannelManager channelManager;

  public ChannelHandler(ChannelManager channelManager) {
    this.channelManager = channelManager;
    addPreConditionChecker(new UserLoggedInChecker());
    addPreConditionChecker(new ValidMessageChecker());
  }

  private static boolean meetsLevelRequirement(
      UserLevelTypes requiredLevel, UserLevelTypes levelToCheck) {
    if (UserLevelTypes.USER.equals(requiredLevel)) {
      return true; // everyone's a user
    } else if (UserLevelTypes.MODERATOR.equals(requiredLevel)) {
      return UserLevelTypes.MODERATOR.equals(levelToCheck)
          || UserLevelTypes.ADMIN.equals(levelToCheck)
          || UserLevelTypes.ELITE.equals(levelToCheck);
    } else if (UserLevelTypes.ADMIN.equals(requiredLevel)) {
      return UserLevelTypes.ADMIN.equals(levelToCheck) || UserLevelTypes.ELITE.equals(levelToCheck);
    } else if (UserLevelTypes.ELITE.equals(requiredLevel)) {
      return UserLevelTypes.ELITE.equals(levelToCheck);
    }
    return false;
  }

  public void processMessage(Message m, MessageContext session) {
    switch (m.getType()) {
      case MessageTypes.CLIENT_CHANNEL_JOIN:
        {
          String channelName = m.getDataString(0);
          String nickName = session.getUser().getNickname();
          StringBuilder topicMsg = new StringBuilder(64);
          boolean channelExists = channelManager.getChannel(channelName) != null;
          OutboundMessageQueue queue = session.getOutboundMessageQueue();
          if (channelManager.isAtCapacity(channelName)) // make sure there's room in channel
          {
            try {
              queue.queueMessage(atCapacityError);
            } catch (InvalidatedQueueException iqe) {
            }
          } else if (channelName.contains(" ")) // no spaces allowed in channel names
          {
            try {
              queue.queueMessage(channelNameError);
            } catch (InvalidatedQueueException iqe) {
            }
          } else if (channelExists
              && !meetsLevelRequirement(
                  channelManager.getChannel(channelName).getLevel(),
                  session.getUser().getLevel())) {
            try {
              queue.queueMessage(permissionError);
            } catch (InvalidatedQueueException iqe) {
            }
          } else {
            channelManager.addUserToChannel(channelName, nickName);
            channelManager.notifyChannelOfUserAction(
                ChannelManager.ACTION_JOIN, channelName, nickName);

            topicMsg.append(channelName);
            topicMsg.append(" ");
            topicMsg.append(channelManager.getChannel(channelName).getTopic());

            try {
              queue.queueMessage(new Message(MessageTypes.SERVER_CHANNEL_JOIN_ACK, channelName));
              queue.queueMessage(new Message(MessageTypes.CHANNEL_TOPIC, topicMsg.toString()));
            } catch (InvalidatedQueueException iqe) {
            }
            channelManager.sendChannelList(session, channelName, ChannelManager.NON_USER_INITIATED);
          }
          break;
        }
      case MessageTypes.CLIENT_CHANNEL_LEAVE:
        {
          String channelName = m.getDataString(0);
          String nickName = session.getUser().getNickname();
          channelManager.removeUserFromChannel(channelName, nickName);
          channelManager.notifyChannelOfUserAction(
              ChannelManager.ACTION_LEAVE, channelName, nickName);
          break;
        }
      case MessageTypes.CLIENT_CHANNEL_PUBLIC_MESSAGE:
        {
          String channelName = m.getDataString(0);
          String textToSend = m.getDataString();
          textToSend = textToSend.substring(textToSend.indexOf(' ') + 1);
          channelManager.sendMessage(channelName, session.getUser().getNickname(), textToSend);
          break;
        }
      case MessageTypes.FULL_CHANNEL_LIST:
        {
          Channel[] channels = channelManager.getChannels();
          OutboundMessageQueue queue = session.getOutboundMessageQueue();
          if (channels != null) {
            for (Channel channel : channels) {
              StringBuilder buf = new StringBuilder(128);
              buf.append(channel.getName());
              buf.append(" ");
              buf.append(channelManager.numMembers(channel.getName()));
              buf.append(" ");
              buf.append(channel.isPermanent() ? "0" : "1");
              buf.append(" ");
              buf.append(channel.getLevel().getValue());
              buf.append(" ");
              buf.append(channel.getLimit());
              buf.append(" ");
              buf.append("\"").append(channel.getTopic()).append("\"");
              try {
                queue.queueMessage(
                    new Message(MessageTypes.SERVER_FULL_CHANNEL_INFO, buf.toString()));
              } catch (InvalidatedQueueException iqe) {
              }
            }
          }
          // send end msg
          try {
            queue.queueMessage(new Message(MessageTypes.FULL_CHANNEL_LIST, ""));
          } catch (InvalidatedQueueException iqe) {
          }
          break;
        }
      case MessageTypes.LIST_CHANNELS:
        {
          Channel[] channels = channelManager.getChannels();
          OutboundMessageQueue queue = session.getOutboundMessageQueue();
          if (channels != null) {
            for (Channel channel : channels) {
              StringBuilder buf = new StringBuilder(128);
              Channel c = channel;
              buf.append(c.getName());
              buf.append(" ");
              buf.append(channelManager.numMembers(c.getName()));
              buf.append(" ");
              buf.append(c.getTopic());
              try {
                queue.queueMessage(new Message(MessageTypes.LIST_CHANNELS_ENTRY, buf.toString()));
              } catch (InvalidatedQueueException iqe) {
              }
            }
          }
          // send end msg
          try {
            queue.queueMessage(new Message(MessageTypes.LIST_CHANNELS, ""));
          } catch (InvalidatedQueueException iqe) {
          }
          break;
        }
      case MessageTypes.CHANNEL_EMOTE:
        {
          String channelName = m.getDataString(0);
          String emoteText = m.getDataString(1);
          String user = session.getUser().getNickname();

          String buf = channelName + " " + user + " \"" + emoteText + "\"";

          channelManager.sendMessage(channelName, new Message(MessageTypes.CHANNEL_EMOTE, buf));
          break;
        }
      case MessageTypes.CHANNEL_TOPIC:
        {
          String channelName = m.getDataString(0);
          String topic = m.getDataString(1);

          String wholeStr = m.getDataString();
          String topicMsgToSend = channelName + " " + wholeStr.substring(wholeStr.indexOf(' ') + 1);
          channelManager.updateChannelTopic(channelName, topic);

          channelManager.sendMessage(
              channelName, new Message(MessageTypes.CHANNEL_TOPIC, topicMsgToSend));
          break;
        }
      case MessageTypes.CLIENT_CHANNEL_USER_LIST:
        {
          String channelName = m.getDataString(0);
          channelManager.sendChannelList(session, channelName, ChannelManager.USER_INITIATED);
          break;
        }
    } // end switch
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(
        MessageTypes.CLIENT_CHANNEL_JOIN,
        MessageTypes.CLIENT_CHANNEL_LEAVE,
        MessageTypes.CLIENT_CHANNEL_PUBLIC_MESSAGE,
        MessageTypes.LIST_CHANNELS,
        MessageTypes.CHANNEL_EMOTE,
        MessageTypes.CHANNEL_TOPIC,
        MessageTypes.FULL_CHANNEL_LIST,
        MessageTypes.CLIENT_CHANNEL_USER_LIST);
  }
}
