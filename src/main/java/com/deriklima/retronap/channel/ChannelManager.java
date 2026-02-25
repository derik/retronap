package com.deriklima.retronap.channel;

import com.deriklima.retronap.config.RetroNapConfig;
import com.deriklima.retronap.message.InvalidatedQueueException;
import com.deriklima.retronap.message.Message;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.MessageTypes;
import com.deriklima.retronap.message.OutboundMessageQueue;
import com.deriklima.retronap.model.UserLevelTypes;
import com.deriklima.retronap.search.SharedFileService;
import com.deriklima.retronap.session.SessionEvent;
import com.deriklima.retronap.session.SessionManager;
import com.deriklima.retronap.user.UserChannelProvider;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Manages channels server-wide.
 *
 * <p>Keeps redundant info on channel membership, for efficiency's sake.
 *
 * <p>Calling code should use addUserToChannel and removeUserFromChannel, but should never call
 * addMember, removeMember in the Channel objects, or horrible things will happen.
 */
@Component
public class ChannelManager implements UserChannelProvider {
  public static final int ACTION_JOIN = 0;
  public static final int ACTION_LEAVE = 1;
  public static final int USER_INITIATED = 0;
  public static final int NON_USER_INITIATED = 1;
  private static final Logger logger = LoggerFactory.getLogger(ChannelManager.class);
  private final Map<String, Set<String>> channelMembers = new HashMap<>();
  private final Map<String, Set<String>> users = new HashMap<>(); // username to channel names
  private final SessionManager sessionManager;
  private final SharedFileService sharedFileService;
  private final ResourceLoader resourceLoader;
  private final ChannelRepository channelRepository;

  public ChannelManager(
      SessionManager sessionManager,
      RetroNapConfig configuration,
      SharedFileService sharedFileService,
      ResourceLoader resourceLoader,
      ChannelRepository channelRepository) {
    this.sessionManager = sessionManager;
    this.sharedFileService = sharedFileService;
    this.resourceLoader = resourceLoader;
    this.channelRepository = channelRepository;
    initChannelsFile(configuration.getPathConfig().getChannels());
  }

  /** Sets up permanent channels */
  public void initChannelsFile(String filename) {
    if (filename == null || filename.trim().isEmpty()) {
      return;
    }

    InputStream inputStream = null;
    try {
      inputStream = resourceLoader.getResource("classpath:" + filename).getInputStream();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    if (inputStream == null) {
      try {
        inputStream = new FileInputStream(filename);
      } catch (FileNotFoundException e) {
        logger.warn("Could not find channels file '{}' in classpath or on filesystem.", filename);
        return;
      }
    }

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (line.indexOf("#") != 0 && !line.isEmpty()) {
          Message m = new Message(0, line); // using Message as a parser
          String name = m.getDataString(0);
          String limit = m.getDataString(1);
          UserLevelTypes level = UserLevelTypes.fromType(m.getDataString(2));

          if (name.indexOf(' ') == -1) {
            Channel c = new Channel();
            c.setName(name);
            c.setTopic("Welcome to the " + name + " channel");
            c.setLimit(Integer.parseInt(limit));
            c.setLevel(level);
            c.setPermanent(true);
            addChannel(c);
          } else {
            logger.warn("Ignoring invalid line in channels file: {}", line);
          }
        }
      }
    } catch (IOException e) {
      logger.warn("Error reading channels file:", e);
    }
  }

  /** Adds a user to a channel (join) */
  public void addUserToChannel(String channel, String user) {
    Channel c = getChannel(channel);
    if (c == null) // doesnt exist yet
    {
      c = new Channel();
      c.setName(channel);
      c.setTopic("Welcome to the " + channel + " channel");
      addChannel(c);
    }
    channelMembers.computeIfAbsent(channel, k -> new java.util.HashSet<>()).add(user);
    users.computeIfAbsent(user, k -> new java.util.HashSet<>()).add(channel);
  }

  /**
   * sends channel user list to session s type specifies either USER_INITIATED or NON_USER_INITIATED
   */
  public void sendChannelList(MessageContext s, String channel, int type) {
    int entryMsgType = MessageTypes.SERVER_CHANNEL_USER_LIST_ENTRY;
    int endListMsgType = MessageTypes.SERVER_CHANNEL_USER_LIST_END;
    if (type == USER_INITIATED) {
      entryMsgType = MessageTypes.SERVER_CHANNEL_USER_LIST_ENTRY_2;
      endListMsgType = MessageTypes.CLIENT_CHANNEL_USER_LIST;
    }

    Set<String> members = channelMembers.get(channel);
    if (members != null && !members.isEmpty()) {
      OutboundMessageQueue queue = s.getOutboundMessageQueue();
      for (String singleMember : new ArrayList<>(members)) {
        MessageContext memberMessageContext = sessionManager.searchForSession(singleMember);
        if (memberMessageContext != null) {
          Long numShares = sharedFileService.countByUserId(memberMessageContext.getUserId());
          int linkType = memberMessageContext.getLinkSpeedValue();
          String msg =
              String.join(
                  " ", channel, singleMember, String.valueOf(numShares), String.valueOf(linkType));
          try {
            queue.queueMessage(new Message(entryMsgType, msg));
          } catch (InvalidatedQueueException ignored) {
          }
        }
      }
      try {
        queue.queueMessage(new Message(endListMsgType, channel));
      } catch (InvalidatedQueueException ignored) {
      }
    }
  }

  /** Sends msgs to all users in channel, that user has joined/left. */
  public void notifyChannelOfUserAction(int actionType, String channel, String user) {
    int msgType = -1;
    switch (actionType) {
      case ACTION_JOIN -> msgType = MessageTypes.SERVER_CHANNEL_JOIN_NOTIFY;
      case ACTION_LEAVE -> msgType = MessageTypes.SERVER_CHANNEL_LEAVE_NOTIFY;
    }

    // gather info about user we're sending notification about.
    MessageContext userMessageContext = sessionManager.searchForSession(user);
    if (userMessageContext != null) {
      Long numShares = sharedFileService.countByUserId(userMessageContext.getUserId());
      int linkType = userMessageContext.getLinkSpeedValue();

      String msg =
          String.join(" ", channel, user, String.valueOf(numShares), String.valueOf(linkType));

      sendMessage(channel, new Message(msgType, msg));
    }
  }

  /** Removes a user from a channel */
  public void removeUserFromChannel(String channel, String user) {
    Channel c = getChannel(channel);
    Set<String> members = channelMembers.get(channel);
    if (members != null) {
      members.remove(user);
      if (members.isEmpty()) {
        channelMembers.remove(channel);
      }
    }
    if (c != null) {
      if (numMembers(channel) == 0 && !c.isPermanent()) {
        removeChannel(c);
      }
    }
    Set<String> channelList = users.get(user);
    if (channelList != null) {
      channelList.remove(channel);
      if (channelList.isEmpty()) {
        users.remove(user);
      }
    }
  }

  /** Sends a public msg to channel, from user */
  public void sendMessage(String channel, String user, String msg) {
    Message msgToSend =
        new Message(MessageTypes.SERVER_CHANNEL_PUBLIC_MESSAGE, channel + " " + user + " " + msg);
    sendMessage(channel, msgToSend);
  }

  /** variation of sendMessage(), takes a preconstructed msg and broadcasts to channel */
  public void sendMessage(String channel, Message msg) {
    Set<String> members = channelMembers.get(channel);
    if (members == null || members.isEmpty()) {
      return;
    }
    for (String member : new ArrayList<>(members)) {
      MessageContext targetMessageContext = sessionManager.searchForSession(member);
      if (targetMessageContext != null) {
        OutboundMessageQueue queue = targetMessageContext.getOutboundMessageQueue();
        try {
          queue.queueMessage(msg);
        } catch (InvalidatedQueueException ignored) {
        }
      } // end if
    } // end for
  }

  /** Returns array of channels that user is currently a member of. */
  public Channel[] getChannelsForUser(String user) {
    Set<String> channelList = users.get(user);
    if (channelList != null && !channelList.isEmpty()) {
      return channelList.stream()
          .map(this::getChannel)
          .filter(c -> c != null)
          .toArray(Channel[]::new);
    }
    return null;
  }

  /** Returns array of all channels in system. */
  public Channel[] getChannels() {
    List<Channel> channels = channelRepository.findAll(Sort.by(Sort.Direction.ASC, "name"));
    if (!channels.isEmpty()) {
      return channels.toArray(new Channel[0]);
    }
    return null;
  }

  /** Returns the Channel object for given channel name */
  public Channel getChannel(String channel) {
    return channelRepository.findById(channel).orElse(null);
  }

  public void addChannel(Channel channel) {
    if (channel == null || channel.getName() == null || channel.getName().isBlank()) {
      return;
    }
    if (channelRepository.existsById(channel.getName())) {
      return;
    }
    channelRepository.save(channel);
  }

  public void removeChannel(Channel channel) {
    if (channel == null || channel.getName() == null) {
      return;
    }
    channelMembers.remove(channel.getName());
    channelRepository.deleteById(channel.getName());
  }

  public void updateChannelTopic(String channelName, String topic) {
    if (topic == null) {
      return;
    }
    Channel channel = getChannel(channelName);
    if (channel == null) {
      return;
    }
    channel.setTopic(topic);
    channelRepository.save(channel);
  }

  @Override
  public String[] getChannelNamesForUser(String user) {
    Set<String> channelList = users.get(user);
    if (channelList != null && !channelList.isEmpty()) {
      return channelList.toArray(new String[0]);
    }
    return new String[0];
  }

  /** Returns true if channelName is at capacity */
  public boolean isAtCapacity(String channelName) {
    Channel c = getChannel(channelName);
    if (c != null) {
      return c.getLimit() != 0 && numMembers(channelName) >= c.getLimit();
    }
    return false;
  }

  public int numMembers(String channelName) {
    return channelMembers.getOrDefault(channelName, Collections.emptySet()).size();
  }

  /** Listens for session terminate events; removes the user from all channels */
  @EventListener
  public void processEvent(SessionEvent se) {
    if (se.getType() == SessionEvent.TERMINATE) {
      MessageContext s = se.getSession();
      String nickname = s.getNickname();
      if (nickname != null) {
        Channel[] channels = getChannelsForUser(nickname);
        if (channels != null) {
          for (Channel channel : channels) {
            String channelName = channel.getName();
            removeUserFromChannel(channelName, nickname);
            notifyChannelOfUserAction(ACTION_LEAVE, channelName, nickname);
          }
        }
      }
    }
  }
}
