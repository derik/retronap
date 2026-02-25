package com.deriklima.retronap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.deriklima.retronap.config.RetroNapConfig;
import com.deriklima.retronap.message.Message;
import com.deriklima.retronap.message.MessageFactory;
import com.deriklima.retronap.message.MessageTypes;
import com.deriklima.retronap.model.User;
import com.deriklima.retronap.model.UserLevelTypes;
import com.deriklima.retronap.model.UserLinkTypes;
import com.deriklima.retronap.user.Banlist;
import com.deriklima.retronap.user.UserNotFoundException;
import com.deriklima.retronap.user.UserPersistenceStore;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserJourneyTests {
  private static final int TEST_SERVER_PORT = findAvailablePort();
  private static final String DEFAULT_PASSWORD = "testpassword";
  private static final String CLIENT_INFO = "v2.0 BETA 6";

  @DynamicPropertySource
  static void dynamicProperties(DynamicPropertyRegistry registry) {
    registry.add("retronap.server.port", () -> TEST_SERVER_PORT);
    registry.add("retronap.metaserver.enabled", () -> false);
  }

  @Autowired private RetroNapConfig retroNapConfig;
  @Autowired private UserPersistenceStore userPersistenceStore;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private MessageFactory messageFactory;
  @Autowired private Banlist banlist;

  @BeforeEach
  void setUp() {
    // Keep defaults for compatibility with existing tests and manual checks.
    upsertUser("testuser", DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);
    upsertUser("hotlistuser1", DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);
    upsertUser("hotlistuser2", DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);
  }

  @Test
  void loginSuccessSendsAckAndMotd() throws Exception {
    String nick = uniqueNick("loginok");
    upsertUser(nick, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    try (TestClient client = connectClient()) {
      sendLogin(client, nick, DEFAULT_PASSWORD);

      Message ack = readNextOfType(client, MessageTypes.SERVER_LOGIN_ACK, 20);
      assertNotNull(ack);
      assertEquals(nick + "@test.com", ack.getDataString());

      Message motd = readNextOfType(client, MessageTypes.MESSAGE_OF_THE_DAY, 20);
      assertNotNull(motd);
      assertFalse(motd.getDataString().isBlank());
    }
  }

  @Test
  void loginFailureSendsServerErrorAndClosesSocket() throws Exception {
    String unknownNick = uniqueNick("unknown");

    try (TestClient client = connectClient()) {
      sendLogin(client, unknownNick, DEFAULT_PASSWORD);

      Message first = readNextAllowingClosedStream(client);
      if (first != null) {
        assertEquals(MessageTypes.SERVER_ERROR, first.getType());
        assertEquals("Login error.(no account or bad password)", first.getDataString());
      }

      // Server should terminate failed login sessions.
      int eof = client.in().read();
      assertEquals(-1, eof);
    }
  }

  @Test
  void bannedNicknameCannotLogin() throws Exception {
    String bannedNick = uniqueNick("banned");
    upsertUser(bannedNick, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);
    banlist.addBannedNick(bannedNick);

    try (TestClient client = connectClient()) {
      sendLogin(client, bannedNick, DEFAULT_PASSWORD);

      Message error = readNextOfType(client, MessageTypes.SERVER_ERROR, 10);
      assertNotNull(error);
      assertEquals("You have been banned from using this server.", error.getDataString());
      assertEquals(-1, client.in().read());
    }
  }

  @Test
  void altLoginCreatesUserAndLogsIn() throws Exception {
    String nick = uniqueNick("alt");
    String email = nick + "@alt.test";

    try (TestClient client = connectClient()) {
      String payload =
          "%s %s 6699 \"%s\" 6 %s 0".formatted(nick, DEFAULT_PASSWORD, CLIENT_INFO, email);
      send(client, MessageTypes.CLIENT_ALT_LOGIN_REQUEST, payload);

      Message ack = readNextOfType(client, MessageTypes.SERVER_LOGIN_ACK, 20);
      assertNotNull(ack);
      assertEquals(email, ack.getDataString());

      Message motd = readNextOfType(client, MessageTypes.MESSAGE_OF_THE_DAY, 20);
      assertNotNull(motd);

      User created = userPersistenceStore.findByNickname(nick);
      assertNotNull(created);
      assertEquals(email, created.getEmail());
    }
  }

  @Test
  void checkPassSupportsSuccessAndFailure() throws Exception {
    String nick = uniqueNick("checkpass");
    upsertUser(nick, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    try (TestClient successClient = connectClient()) {
      send(successClient, MessageTypes.CLIENT_CHECK_PASS, nick + " " + DEFAULT_PASSWORD);
      Message passOk = readNextOfType(successClient, MessageTypes.SERVER_PASS_OK, 10);
      assertNotNull(passOk);
    }

    try (TestClient failClient = connectClient()) {
      send(failClient, MessageTypes.CLIENT_CHECK_PASS, nick + " wrongpass");
      Message error = readNextOfType(failClient, MessageTypes.SERVER_ERROR, 10);
      assertNotNull(error);
      assertEquals("Incorrect Password.", error.getDataString());
    }
  }

  @Test
  void registrationNicknameCheckReturnsAvailableAndTaken() throws Exception {
    String existingNick = uniqueNick("taken");
    String availableNick = uniqueNick("free");
    upsertUser(existingNick, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    try (TestClient availableClient = connectClient()) {
      send(availableClient, MessageTypes.CLIENT_REGISTRATION, availableNick);
      Message ok = readNextOfType(availableClient, MessageTypes.SERVER_REGISTRATION_SUCCESS, 10);
      assertNotNull(ok);
    }

    try (TestClient existingClient = connectClient()) {
      send(existingClient, MessageTypes.CLIENT_REGISTRATION, existingNick);
      Message taken =
          readNextOfType(existingClient, MessageTypes.SERVER_NICKNAME_ALREADY_REGISTERED, 10);
      assertNotNull(taken);
    }
  }

  @Test
  void hotlistCreateAcksKnownUsersAndUnknownNick() throws Exception {
    String watcher = uniqueNick("watcher");
    String known1 = uniqueNick("known1");
    String known2 = uniqueNick("known2");
    String unknown = uniqueNick("missing");
    upsertUser(watcher, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);
    upsertUser(known1, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);
    upsertUser(known2, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    try (TestClient client = connectClient()) {
      loginAndDrainMotd(client, watcher, DEFAULT_PASSWORD);
      send(client, MessageTypes.CLIENT_HOTLIST_CREATE, known1 + " " + known2 + " " + unknown);

      Message ack1 = readNextOfType(client, MessageTypes.SERVER_HOTLIST_ACK, 20);
      Message ack2 = readNextOfType(client, MessageTypes.SERVER_HOTLIST_ACK, 20);
      Message ack3 = readNextOfType(client, MessageTypes.SERVER_HOTLIST_ACK, 20);

      assertNotNull(ack1);
      assertNotNull(ack2);
      assertNotNull(ack3);
      assertTrue(
          List.of(ack1.getDataString(), ack2.getDataString(), ack3.getDataString())
              .containsAll(List.of(known1, known2, unknown)));
      assertNoMessageOfType(client, MessageTypes.SERVER_ERROR, 500);
    }
  }

  @Test
  void hotlistReceivesSignonAndSignoffEvents() throws Exception {
    String watcher = uniqueNick("stalker");
    String target = uniqueNick("target");
    upsertUser(watcher, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);
    upsertUser(target, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    try (TestClient watcherClient = connectClient()) {
      loginAndDrainMotd(watcherClient, watcher, DEFAULT_PASSWORD);
      send(watcherClient, MessageTypes.CLIENT_HOTLIST_CREATE, target);
      assertNotNull(readNextOfType(watcherClient, MessageTypes.SERVER_HOTLIST_ACK, 20));

      try (TestClient targetClient = connectClient()) {
        loginAndDrainMotd(targetClient, target, DEFAULT_PASSWORD);

        Message signon = readNextOfType(watcherClient, MessageTypes.SERVER_HOTLIST_SIGNON, 30);
        assertNotNull(signon);
        assertTrue(signon.getDataString().startsWith(target + " "));
      }

      Message signoff = readNextOfType(watcherClient, MessageTypes.SERVER_HOTLIST_SIGNOFF, 30);
      assertNotNull(signoff);
      assertEquals(target, signoff.getDataString());
    }
  }

  @Test
  void hotlistRemoveStopsFurtherPresenceNotifications() throws Exception {
    String watcher = uniqueNick("rmwatcher");
    String target = uniqueNick("rmtarget");
    upsertUser(watcher, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);
    upsertUser(target, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    try (TestClient watcherClient = connectClient()) {
      loginAndDrainMotd(watcherClient, watcher, DEFAULT_PASSWORD);
      send(watcherClient, MessageTypes.CLIENT_HOTLIST_CREATE, target);
      assertNotNull(readNextOfType(watcherClient, MessageTypes.SERVER_HOTLIST_ACK, 20));

      send(watcherClient, MessageTypes.CLIENT_HOTLIST_REMOVE, target);

      try (TestClient targetClient = connectClient()) {
        loginAndDrainMotd(targetClient, target, DEFAULT_PASSWORD);
        assertNoMessageOfType(watcherClient, MessageTypes.SERVER_HOTLIST_SIGNON, 1000);
      }
    }
  }

  @Test
  void shareVia100AndBrowseReturnsSharedFile() throws Exception {
    String nick = uniqueNick("share100");
    upsertUser(nick, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    String path = "C:\\\\Music\\\\MySong.mp3";
    String md5 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    try (TestClient client = connectClient()) {
      loginAndDrainMotd(client, nick, DEFAULT_PASSWORD);
      send(
          client,
          MessageTypes.CLIENT_SHARE_NOTIFICATION,
          "%s %s 123456 192 44100 300".formatted(path, md5));
      send(client, MessageTypes.CLIENT_BROWSE_USER_FILES, nick);

      Message browse = readNextOfType(client, MessageTypes.SERVER_BROWSE_USER_RESPONSE, 40);
      Message end = readNextOfType(client, MessageTypes.SERVER_BROWSE_USER_END_RESPONSE, 40);

      assertNotNull(browse);
      assertTrue(browse.getDataString().contains(path));
      assertTrue(browse.getDataString().contains(md5));
      assertNotNull(end);
      assertEquals(nick, end.getDataString());
    }
  }

  @Test
  void directoryShare870SupportsQuotedFilenamesWithSpaces() throws Exception {
    String nick = uniqueNick("share870");
    upsertUser(nick, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    String dir = "C:\\\\SHARED\\\\";
    String song = "01 - Blink 182 - Anthem Part Two.mp3";
    String md5 = "d1d6ecada3f859cf11dadb544ba43a54";

    try (TestClient client = connectClient()) {
      loginAndDrainMotd(client, nick, DEFAULT_PASSWORD);

      String payload = "%s \"%s\" %s 9136438 320 44100 228".formatted(dir, song, md5);
      send(client, MessageTypes.CLIENT_DIR_SHARE_NOTIFICATION, payload);
      send(client, MessageTypes.CLIENT_BROWSE_USER_FILES, nick);

      Message browse = readNextOfType(client, MessageTypes.SERVER_BROWSE_USER_RESPONSE, 50);
      Message end = readNextOfType(client, MessageTypes.SERVER_BROWSE_USER_END_RESPONSE, 50);

      assertNotNull(browse);
      assertTrue(browse.getDataString().contains(dir + song));
      assertTrue(browse.getDataString().contains(md5));
      assertNotNull(end);
    }
  }

  @Test
  void directoryShare870InvalidTupleCountReturns404WhenAuthenticated() throws Exception {
    String nick = uniqueNick("bad870");
    upsertUser(nick, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    try (TestClient client = connectClient()) {
      loginAndDrainMotd(client, nick, DEFAULT_PASSWORD);
      send(client, MessageTypes.CLIENT_DIR_SHARE_NOTIFICATION, "C:\\\\BROKEN\\\\ fileonly");

      Message error = readNextOfType(client, MessageTypes.SERVER_GENERAL_ERROR, 20);
      assertNotNull(error);
      assertEquals("invalid number of tokens in message 870", error.getDataString());
    }
  }

  @Test
  void searchNoHitStillTerminatesWith202() throws Exception {
    String nick = uniqueNick("searchmiss");
    upsertUser(nick, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    try (TestClient client = connectClient()) {
      loginAndDrainMotd(client, nick, DEFAULT_PASSWORD);
      send(
          client,
          MessageTypes.CLIENT_SEARCH_REQUEST,
          "FILENAME CONTAINS \"query-that-does-not-exist\" MAX_RESULTS 10");

      Message end = readNextOfType(client, MessageTypes.SERVER_END_SEARCH_RESULTS, 30);
      assertNotNull(end);
      assertNoMessageOfType(client, MessageTypes.SERVER_SEARCH_RESPONSE, 500);
    }
  }

  @Test
  void searchReturnsAllMatchesEvenWhenMaxResultsIsLarge() throws Exception {
    String sharer = uniqueNick("maxshare");
    String searcher = uniqueNick("maxsearch");
    upsertUser(sharer, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);
    upsertUser(searcher, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    String dir = "C:\\\\CAP\\\\";
    StringBuilder payload = new StringBuilder(dir);
    for (int i = 0; i < 120; i++) {
      payload
          .append(" ")
          .append("\"")
          .append("cap-file-")
          .append(i)
          .append(".mp3\"")
          .append(" ")
          .append(String.format("%032x", i + 1))
          .append(" 1000 128 44100 120");
    }

    try (TestClient sharerClient = connectClient();
        TestClient searchClient = connectClient()) {
      loginAndDrainMotd(sharerClient, sharer, DEFAULT_PASSWORD);
      send(sharerClient, MessageTypes.CLIENT_DIR_SHARE_NOTIFICATION, payload.toString());

      loginAndDrainMotd(searchClient, searcher, DEFAULT_PASSWORD);
      send(
          searchClient,
          MessageTypes.CLIENT_SEARCH_REQUEST,
          "FILENAME CONTAINS \"cap-file\" MAX_RESULTS 999");

      int resultCount = 0;
      for (int i = 0; i < 300; i++) {
        Message msg = readNext(searchClient);
        if (msg.getType() == MessageTypes.SERVER_SEARCH_RESPONSE) {
          resultCount++;
        } else if (msg.getType() == MessageTypes.SERVER_END_SEARCH_RESULTS) {
          break;
        }
      }

      assertEquals(120, resultCount);
    }
  }

  @Test
  void fullChannelListReturns828EntriesAnd827Terminator() throws Exception {
    String nick = uniqueNick("fullchan");
    upsertUser(nick, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    try (TestClient client = connectClient()) {
      loginAndDrainMotd(client, nick, DEFAULT_PASSWORD);
      send(client, MessageTypes.FULL_CHANNEL_LIST, "");

      List<String> entries =
          collectPayloadsUntilTerminator(
              client, MessageTypes.SERVER_FULL_CHANNEL_INFO, MessageTypes.FULL_CHANNEL_LIST, 40);
      String flattened = String.join("\n", entries);

      assertTrue(flattened.contains("General"));
      assertTrue(flattened.contains("ModeratorsOnly"));
      assertTrue(flattened.contains("AdminsOnly"));
      assertTrue(flattened.contains("EliteOnly"));

      assertEquals(4, entries.size());
    }
  }

  @Test
  void listChannelsReturns618EntriesAnd617Terminator() throws Exception {
    String nick = uniqueNick("listchan");
    upsertUser(nick, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    try (TestClient client = connectClient()) {
      loginAndDrainMotd(client, nick, DEFAULT_PASSWORD);
      send(client, MessageTypes.LIST_CHANNELS, "");

      List<String> entries =
          collectPayloadsUntilTerminator(
              client, MessageTypes.LIST_CHANNELS_ENTRY, MessageTypes.LIST_CHANNELS, 30);
      String flattened = String.join("\n", entries);

      assertTrue(flattened.contains("General"));
      assertTrue(flattened.contains("ModeratorsOnly"));
      assertTrue(flattened.contains("AdminsOnly"));
      assertTrue(flattened.contains("EliteOnly"));
      assertEquals(4, entries.size());
    }
  }

  @Test
  void channelJoinAndLeaveBroadcastNotifications() throws Exception {
    String user1 = uniqueNick("chan1");
    String user2 = uniqueNick("chan2");
    String channel = "room" + uniqueNick("x").substring(0, 6);
    upsertUser(user1, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);
    upsertUser(user2, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    try (TestClient c1 = connectClient();
        TestClient c2 = connectClient()) {
      loginAndDrainMotd(c1, user1, DEFAULT_PASSWORD);
      loginAndDrainMotd(c2, user2, DEFAULT_PASSWORD);

      send(c1, MessageTypes.CLIENT_CHANNEL_JOIN, channel);
      assertNotNull(readNextOfType(c1, MessageTypes.SERVER_CHANNEL_JOIN_ACK, 40));

      send(c2, MessageTypes.CLIENT_CHANNEL_JOIN, channel);
      Message joinNotify = readNextOfType(c1, MessageTypes.SERVER_CHANNEL_JOIN_NOTIFY, 50);
      assertNotNull(joinNotify);
      assertTrue(joinNotify.getDataString().contains(channel + " " + user2));

      send(c2, MessageTypes.CLIENT_CHANNEL_LEAVE, channel);
      Message leaveNotify = readNextOfType(c1, MessageTypes.SERVER_CHANNEL_LEAVE_NOTIFY, 40);
      assertNotNull(leaveNotify);
      assertTrue(leaveNotify.getDataString().contains(channel + " " + user2));
    }
  }

  @Test
  void channelJoinWithSpaceInNameReturns404InvalidChannelName() throws Exception {
    String nick = uniqueNick("badchannel");
    upsertUser(nick, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    try (TestClient client = connectClient()) {
      loginAndDrainMotd(client, nick, DEFAULT_PASSWORD);
      send(client, MessageTypes.CLIENT_CHANNEL_JOIN, "\"bad room\"");

      Message error = readNextOfType(client, MessageTypes.SERVER_GENERAL_ERROR, 20);
      assertNotNull(error);
      assertEquals("Invalid channel name.", error.getDataString());
    }
  }

  @Test
  void channelEmoteAndTopicAreBroadcastToMembers() throws Exception {
    String user1 = uniqueNick("emoter");
    String user2 = uniqueNick("listener");
    String channel = "emote" + uniqueNick("room").substring(0, 5);
    upsertUser(user1, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);
    upsertUser(user2, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    try (TestClient c1 = connectClient();
        TestClient c2 = connectClient()) {
      loginAndDrainMotd(c1, user1, DEFAULT_PASSWORD);
      loginAndDrainMotd(c2, user2, DEFAULT_PASSWORD);

      send(c1, MessageTypes.CLIENT_CHANNEL_JOIN, channel);
      readNextOfType(c1, MessageTypes.SERVER_CHANNEL_JOIN_ACK, 40);
      send(c2, MessageTypes.CLIENT_CHANNEL_JOIN, channel);
      readNextOfType(c2, MessageTypes.SERVER_CHANNEL_JOIN_ACK, 40);

      send(c1, MessageTypes.CHANNEL_EMOTE, channel + " \"waves hello\"");
      Message emote = readNextOfType(c2, MessageTypes.CHANNEL_EMOTE, 40);
      assertNotNull(emote);
      assertTrue(emote.getDataString().contains(channel + " " + user1));
      assertTrue(emote.getDataString().contains("waves hello"));

      send(c1, MessageTypes.CHANNEL_TOPIC, channel + " \"new topic\"");
      Message topic = readNextOfType(c2, MessageTypes.CHANNEL_TOPIC, 40);
      assertNotNull(topic);
      assertTrue(topic.getDataString().contains(channel));
      assertTrue(topic.getDataString().contains("new topic"));
    }
  }

  @Test
  void privateMessageSuccessAndOfflineError() throws Exception {
    String sender = uniqueNick("sender");
    String receiver = uniqueNick("receiver");
    String offline = uniqueNick("offline");
    upsertUser(sender, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);
    upsertUser(receiver, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    try (TestClient senderClient = connectClient();
        TestClient receiverClient = connectClient()) {
      loginAndDrainMotd(senderClient, sender, DEFAULT_PASSWORD);
      loginAndDrainMotd(receiverClient, receiver, DEFAULT_PASSWORD);

      send(senderClient, MessageTypes.PRIVATE_MESSAGE, receiver + " hello there");
      Message pm = readNextOfType(receiverClient, MessageTypes.PRIVATE_MESSAGE, 30);
      assertNotNull(pm);
      assertTrue(pm.getDataString().startsWith(sender + " "));
      assertTrue(pm.getDataString().contains("hello there"));

      send(senderClient, MessageTypes.PRIVATE_MESSAGE, offline + " are you online");
      Message err = readNextOfType(senderClient, MessageTypes.SERVER_GENERAL_ERROR, 30);
      assertNotNull(err);
      assertEquals("User " + offline + " not currently online", err.getDataString());
    }
  }

  @Test
  void pingRelaysToOnlineUserAndErrorsWhenOffline() throws Exception {
    String sender = uniqueNick("pinger");
    String receiver = uniqueNick("pong");
    String offline = uniqueNick("nope");
    upsertUser(sender, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);
    upsertUser(receiver, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    try (TestClient senderClient = connectClient();
        TestClient receiverClient = connectClient()) {
      loginAndDrainMotd(senderClient, sender, DEFAULT_PASSWORD);
      loginAndDrainMotd(receiverClient, receiver, DEFAULT_PASSWORD);

      send(senderClient, MessageTypes.PING, receiver);
      Message ping = readNextOfType(receiverClient, MessageTypes.PING, 20);
      assertNotNull(ping);
      assertEquals(sender, ping.getDataString());

      send(senderClient, MessageTypes.PING, offline);
      Message err = readNextOfType(senderClient, MessageTypes.SERVER_GENERAL_ERROR, 20);
      assertNotNull(err);
      assertEquals("ping failed, " + offline + " not online.", err.getDataString());
    }
  }

  @Test
  void downloadRequestRoutes607OnHitAnd206OnMiss() throws Exception {
    String uploader = uniqueNick("uploader");
    String downloader = uniqueNick("downloader");
    upsertUser(uploader, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);
    upsertUser(downloader, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    String file = "C:\\\\Transfer\\\\hit.mp3";
    String md5 = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    try (TestClient uploaderClient = connectClient();
        TestClient downloaderClient = connectClient()) {
      loginAndDrainMotd(uploaderClient, uploader, DEFAULT_PASSWORD);
      loginAndDrainMotd(downloaderClient, downloader, DEFAULT_PASSWORD);

      send(
          uploaderClient,
          MessageTypes.CLIENT_SHARE_NOTIFICATION,
          "%s %s 2048 192 44100 180".formatted(file, md5));

      send(downloaderClient, MessageTypes.CLIENT_DOWNLOAD_REQUEST, uploader + " \"" + file + "\"");
      Message uploadRequest =
          readNextOfType(uploaderClient, MessageTypes.SERVER_UPLOAD_REQUEST, 40);
      assertNotNull(uploadRequest);
      assertTrue(uploadRequest.getDataString().contains(downloader));
      assertTrue(uploadRequest.getDataString().contains(file));

      send(
          downloaderClient,
          MessageTypes.CLIENT_DOWNLOAD_REQUEST,
          uploader + " \"C:\\\\Transfer\\\\miss.mp3\"");
      Message miss = readNextOfType(downloaderClient, MessageTypes.SERVER_DOWNLOAD_ERROR, 40);
      assertNotNull(miss);
      assertTrue(miss.getDataString().contains(uploader));
      assertTrue(miss.getDataString().contains("miss.mp3"));
    }
  }

  @Test
  void uploadAcceptSends204ToDownloader() throws Exception {
    String uploader = uniqueNick("accup");
    String downloader = uniqueNick("accdown");
    upsertUser(uploader, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);
    upsertUser(downloader, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    String file = "C:\\\\Transfer\\\\accept.mp3";
    String md5 = "cccccccccccccccccccccccccccccccc";

    try (TestClient uploaderClient = connectClient();
        TestClient downloaderClient = connectClient()) {
      loginAndDrainMotd(uploaderClient, uploader, DEFAULT_PASSWORD);
      loginAndDrainMotd(downloaderClient, downloader, DEFAULT_PASSWORD);

      send(
          uploaderClient,
          MessageTypes.CLIENT_SHARE_NOTIFICATION,
          "%s %s 4096 192 44100 200".formatted(file, md5));
      send(uploaderClient, MessageTypes.CLIENT_UPLOAD_ACCEPT, downloader + " \"" + file + "\"");

      Message ack = readNextOfType(downloaderClient, MessageTypes.SERVER_DOWNLOAD_ACK, 30);
      assertNotNull(ack);
      assertTrue(ack.getDataString().contains(uploader));
      assertTrue(ack.getDataString().contains(file));
      assertTrue(ack.getDataString().contains(md5));
    }
  }

  @Test
  void altDownloadRequestSends501() throws Exception {
    String requester = uniqueNick("altreq");
    String target = uniqueNick("alttarget");
    upsertUser(requester, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);
    upsertUser(target, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    String file = "C:\\\\Transfer\\\\alt.mp3";
    String md5 = "dddddddddddddddddddddddddddddddd";

    try (TestClient requesterClient = connectClient();
        TestClient targetClient = connectClient()) {
      loginAndDrainMotd(requesterClient, requester, DEFAULT_PASSWORD);
      loginAndDrainMotd(targetClient, target, DEFAULT_PASSWORD);

      send(
          targetClient,
          MessageTypes.CLIENT_SHARE_NOTIFICATION,
          "%s %s 8192 256 44100 220".formatted(file, md5));
      assertTrue(
          waitForBrowseHit(requesterClient, target, file),
          "Expected browse hit before alt download");

      send(requesterClient, MessageTypes.CLIENT_ALT_DOWNLOAD_REQUEST, target + " \"" + file + "\"");
      Message altAck = readNextOfType(targetClient, MessageTypes.SERVER_ALT_DOWNLOAD_ACK, 30);
      assertNotNull(altAck);
      assertTrue(altAck.getDataString().contains(requester));
      assertTrue(altAck.getDataString().contains(file));
    }
  }

  @Test
  void queueLimitMessageRelays620ToDownloader() throws Exception {
    String uploader = uniqueNick("qup");
    String downloader = uniqueNick("qdown");
    upsertUser(uploader, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);
    upsertUser(downloader, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    String file = "C:\\\\Transfer\\\\queue.mp3";
    String md5 = "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";

    try (TestClient uploaderClient = connectClient();
        TestClient downloaderClient = connectClient()) {
      loginAndDrainMotd(uploaderClient, uploader, DEFAULT_PASSWORD);
      loginAndDrainMotd(downloaderClient, downloader, DEFAULT_PASSWORD);

      send(
          uploaderClient,
          MessageTypes.CLIENT_SHARE_NOTIFICATION,
          "%s %s 12345 192 44100 230".formatted(file, md5));
      send(uploaderClient, MessageTypes.CLIENT_QUEUE_LIMIT, downloader + " \"" + file + "\" 5");

      Message queueLimit = readNextOfType(downloaderClient, MessageTypes.SERVER_QUEUE_LIMIT, 30);
      assertNotNull(queueLimit);
      assertTrue(queueLimit.getDataString().contains(uploader));
      assertTrue(queueLimit.getDataString().contains(file));
      assertTrue(queueLimit.getDataString().endsWith(" 5"));
    }
  }

  @Test
  void unknownMessageTypeReturns404Fallback() throws Exception {
    try (TestClient client = connectClient()) {
      send(client, 9999, "mystery payload");
      Message err = readNextOfType(client, MessageTypes.SERVER_GENERAL_ERROR, 10);
      assertNotNull(err);
      assertEquals("Don't know how to handle that message type 9999", err.getDataString());
    }
  }

  @Test
  void invalidTokenCountUnauthenticatedReturnsServerError0() throws Exception {
    try (TestClient client = connectClient()) {
      // Type 7 expects exactly one token; empty payload is invalid.
      send(client, MessageTypes.CLIENT_REGISTRATION, "");
      Message err = readNextOfType(client, MessageTypes.SERVER_ERROR, 10);
      assertNotNull(err);
      assertEquals("The message format is not valid", err.getDataString());
    }
  }

  @Test
  void invalidTokenCountAuthenticatedReturnsServerGeneralError404() throws Exception {
    String nick = uniqueNick("authbad");
    upsertUser(nick, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    try (TestClient client = connectClient()) {
      loginAndDrainMotd(client, nick, DEFAULT_PASSWORD);

      // Type 400 requires one token; two tokens should fail format validation while logged in.
      send(client, MessageTypes.CLIENT_CHANNEL_JOIN, "two tokens");
      Message err = readNextOfType(client, MessageTypes.SERVER_GENERAL_ERROR, 10);
      assertNotNull(err);
      assertEquals("The message format is not valid", err.getDataString());
    }
  }

  @Test
  void privilegedMessageWhileNotLoggedInReturnsServerError0() throws Exception {
    try (TestClient client = connectClient()) {
      send(client, MessageTypes.CLIENT_BROWSE_USER_FILES, "someone");
      Message err = readNextOfType(client, MessageTypes.SERVER_ERROR, 10);
      assertNotNull(err);
      assertEquals(
          "User tried sending privileged message while not logged in", err.getDataString());
    }
  }

  @Test
  void adminCommandRejectedForNonAdminAndAcceptedForAdmin() throws Exception {
    String userNick = uniqueNick("notadmin");
    String adminNick = uniqueNick("admin");
    String banTarget = uniqueNick("bantarget");

    upsertUser(userNick, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);
    upsertUser(adminNick, DEFAULT_PASSWORD, UserLevelTypes.ADMIN, UserLinkTypes.T1);
    upsertUser(banTarget, DEFAULT_PASSWORD, UserLevelTypes.USER, UserLinkTypes.T1);

    try (TestClient normalClient = connectClient()) {
      loginAndDrainMotd(normalClient, userNick, DEFAULT_PASSWORD);
      send(normalClient, MessageTypes.CLIENT_ADMIN_BAN_USER, banTarget);
      Message err = readNextOfType(normalClient, MessageTypes.SERVER_GENERAL_ERROR, 20);
      assertNotNull(err);
      assertEquals(
          "User must be Admin level or above to execute that command!", err.getDataString());
    }

    try (TestClient adminClient = connectClient()) {
      loginAndDrainMotd(adminClient, adminNick, DEFAULT_PASSWORD);
      send(adminClient, MessageTypes.CLIENT_ADMIN_BAN_USER, banTarget);
      send(adminClient, MessageTypes.CLIENT_ADMIN_SHOW_BANS, "");

      Message banEntry = readNextOfType(adminClient, MessageTypes.SERVER_BAN_LIST_USER_ENTRY, 20);
      assertNotNull(banEntry);
      assertEquals(banTarget, banEntry.getDataString());
    }

    try (TestClient bannedLoginClient = connectClient()) {
      sendLogin(bannedLoginClient, banTarget, DEFAULT_PASSWORD);
      Message err = readNextOfType(bannedLoginClient, MessageTypes.SERVER_ERROR, 20);
      assertNotNull(err);
      assertEquals("You have been banned from using this server.", err.getDataString());
    }
  }

  private static int findAvailablePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      socket.setReuseAddress(true);
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("Could not allocate a free TCP port for tests", e);
    }
  }

  private String uniqueNick(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
  }

  private void upsertUser(
      String nickname, String password, UserLevelTypes level, UserLinkTypes linkType) {
    User user;
    try {
      user = userPersistenceStore.findByNickname(nickname);
    } catch (UserNotFoundException e) {
      user = new User();
    }

    user.setNickname(nickname);
    user.setPassword(passwordEncoder.encode(password));
    user.setEmail(nickname + "@test.com");
    user.setLevel(level);
    user.setLinkSpeed(linkType);
    user.setClientInfo(CLIENT_INFO);
    userPersistenceStore.save(user);
  }

  private TestClient connectClient() throws IOException, InterruptedException {
    waitForServer();
    Socket socket = new Socket("localhost", retroNapConfig.getServer().getPort());
    socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(3));
    return new TestClient(socket);
  }

  private void waitForServer() throws InterruptedException {
    // The server starts quickly, but there can be race conditions on initial startup.
    Thread.sleep(250);
  }

  private void sendLogin(TestClient client, String nickname, String password) throws IOException {
    String loginPayload = "%s %s 6699 \"%s\" 6 3697 0".formatted(nickname, password, CLIENT_INFO);
    send(client, MessageTypes.CLIENT_LOGIN_REQUEST, loginPayload);
  }

  private void loginAndDrainMotd(TestClient client, String nickname, String password)
      throws IOException {
    sendLogin(client, nickname, password);
    Message ack = readNextOfType(client, MessageTypes.SERVER_LOGIN_ACK, 30);
    assertNotNull(ack, "Expected login ack");
    Message motd = readNextOfType(client, MessageTypes.MESSAGE_OF_THE_DAY, 30);
    assertNotNull(motd, "Expected at least one MOTD message");
  }

  private void send(TestClient client, int type, String payload) throws IOException {
    Message msg = new Message(type, payload);
    client.out().write(msg.toByteArray());
    client.out().flush();
  }

  private Message readNext(TestClient client) throws IOException {
    Message message = messageFactory.createMessage(client.in());
    assertNotNull(message, "Expected message but stream ended");
    return message;
  }

  private Message readNextAllowingClosedStream(TestClient client) throws IOException {
    try {
      return messageFactory.createMessage(client.in());
    } catch (SocketTimeoutException e) {
      return null;
    }
  }

  private Message readNextOfType(TestClient client, int type, int maxMessages) throws IOException {
    for (int i = 0; i < maxMessages; i++) {
      Message msg = readNext(client);
      if (msg.getType() == type) {
        return msg;
      }
    }
    return null;
  }

  private List<String> collectPayloadsUntilTerminator(
      TestClient client, int entryType, int terminatorType, int maxMessages) throws IOException {
    List<String> payloads = new ArrayList<>();
    for (int i = 0; i < maxMessages; i++) {
      Message msg = readNext(client);
      if (msg.getType() == entryType) {
        payloads.add(msg.getDataString());
      } else if (msg.getType() == terminatorType) {
        assertEquals("", msg.getDataString());
        return payloads;
      }
    }
    throw new AssertionError("Did not receive terminator message type " + terminatorType);
  }

  private boolean waitForBrowseHit(TestClient requester, String targetNick, String file)
      throws IOException, InterruptedException {
    for (int attempt = 0; attempt < 5; attempt++) {
      send(requester, MessageTypes.CLIENT_BROWSE_USER_FILES, targetNick);
      boolean found = false;
      for (int i = 0; i < 40; i++) {
        Message msg = readNext(requester);
        if (msg.getType() == MessageTypes.SERVER_BROWSE_USER_RESPONSE
            && msg.getDataString().contains(file)) {
          found = true;
        }
        if (msg.getType() == MessageTypes.SERVER_BROWSE_USER_END_RESPONSE) {
          break;
        }
      }
      if (found) {
        return true;
      }
      Thread.sleep(100);
    }
    return false;
  }

  private void assertNoMessageOfType(TestClient client, int unexpectedType, long windowMillis)
      throws IOException {
    long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(windowMillis);
    while (System.nanoTime() < deadline) {
      try {
        Message msg = messageFactory.createMessage(client.in());
        if (msg == null) {
          return;
        }
        if (msg.getType() == unexpectedType) {
          throw new AssertionError("Unexpected message type " + unexpectedType + ": " + msg);
        }
      } catch (SocketTimeoutException ignored) {
        return;
      }
    }
  }

  private record TestClient(Socket socket) implements AutoCloseable {
    OutputStream out() throws IOException {
      return socket.getOutputStream();
    }

    InputStream in() throws IOException {
      return socket.getInputStream();
    }

    @Override
    public void close() throws Exception {
      socket.close();
    }
  }
}
