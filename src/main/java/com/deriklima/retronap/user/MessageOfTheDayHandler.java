package com.deriklima.retronap.user;

import com.deriklima.retronap.config.RetroNapConfig;
import com.deriklima.retronap.message.*;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.UserLoggedInChecker;
import com.deriklima.retronap.message.ValidMessageChecker;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** Handles motd request messages */
@Component
public class MessageOfTheDayHandler extends MessageHandler {
  private final RetroNapConfig configuration;
  private Message[] motdMsg = null;

  private final ResourceLoader resourceLoader;

  public MessageOfTheDayHandler(RetroNapConfig configuration, ResourceLoader resourceLoader) {
    this.configuration = configuration;
    this.resourceLoader = resourceLoader;
    addPreConditionChecker(new UserLoggedInChecker());
    addPreConditionChecker(new ValidMessageChecker());
  }

  protected void processMessage(Message m, MessageContext session) {
    OutboundMessageQueue queue = session.getOutboundMessageQueue();

    Message[] msgs = getMotdMsg();
    int numMsgs = msgs.length;
    try {
      for (int x = 0; x < numMsgs; x++) {
        queue.queueMessage(msgs[x]);
      }
    } catch (InvalidatedQueueException iqe) {
    }
    session.startStatusUpdateThread();
  }

  private Message[] getMotdMsg() {
    if (motdMsg == null) {
      String[] lines = readMotdFile(configuration.getPathConfig().getMotd());
      int numLines = lines.length;
      motdMsg = new Message[numLines];
      for (int x = 0; x < numLines; x++) {
        motdMsg[x] = new Message(MessageTypes.MESSAGE_OF_THE_DAY, lines[x]);
      }
    }
    return motdMsg;
  }

  public String[] readMotdFile(String motd) {
    if (motd == null || motd.isEmpty()) {
      return new String[] {"Welcome, this is a Napster server."};
    }

    List<String> lines = null;

    // Try loading from classpath first
    try (InputStream is = resourceLoader.getResource("classpath:" + motd).getInputStream()) {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
        lines = reader.lines().collect(Collectors.toList());
      }
    } catch (Exception e) {
      // Could log this, but for now we'll just fall through to the file system attempt
    }

    // If not found in classpath, try file system
    if (lines == null) {
      try {
        lines = Files.readAllLines(Paths.get(motd));
      } catch (IOException e) {
        // File not found or readable, will return default MOTD.
      }
    }

    if (lines != null && !lines.isEmpty()) {
      return lines.toArray(new String[0]);
    }

    return new String[] {"Welcome, this is a Napster server."};
  }

  @Override
  public List<Integer> getHandledMessageTypes() {
    return List.of(MessageTypes.MESSAGE_OF_THE_DAY);
  }
}
