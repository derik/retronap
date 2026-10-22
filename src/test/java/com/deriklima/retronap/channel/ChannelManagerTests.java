package com.deriklima.retronap.channel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.deriklima.retronap.config.RetroNapConfig;
import com.deriklima.retronap.message.Message;
import com.deriklima.retronap.message.MessageTypes;
import com.deriklima.retronap.session.SessionManager;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ChannelManagerTests {
  private final ChannelRepository repository = mock(ChannelRepository.class);
  private final SessionManager sessions = mock(SessionManager.class);
  private final Map<String, Channel> channels = new ConcurrentHashMap<>();

  private ChannelManager manager() {
    when(repository.findById(anyString()))
        .thenAnswer(call -> Optional.ofNullable(channels.get(call.getArgument(0))));
    when(repository.existsById(anyString()))
        .thenAnswer(call -> channels.containsKey(call.getArgument(0)));
    when(repository.save(any(Channel.class)))
        .thenAnswer(
            call -> {
              Channel channel = call.getArgument(0);
              channels.put(channel.getName(), channel);
              return channel;
            });
    doAnswer(call -> channels.remove(call.getArgument(0))).when(repository).deleteById(anyString());
    var config = new RetroNapConfig();
    config.setPathConfig(new RetroNapConfig.PathConfig());
    return new ChannelManager(sessions, config, null, null, repository);
  }

  @Test
  void joiningWaitsForLastMembersChannelDeletionBeforeRecreatingIt() throws Exception {
    var manager = manager();
    manager.addUserToChannel("room", "alice");
    var deleting = new CountDownLatch(1);
    var allowDeletion = new CountDownLatch(1);
    doAnswer(
            call -> {
              deleting.countDown();
              assertTrue(allowDeletion.await(5, TimeUnit.SECONDS));
              channels.remove(call.getArgument(0));
              return null;
            })
        .when(repository)
        .deleteById("room");

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var leaving = executor.submit(() -> manager.removeUserFromChannel("room", "alice"));
      assertTrue(deleting.await(5, TimeUnit.SECONDS));
      var joinStarted = new CountDownLatch(1);
      var joining =
          executor.submit(
              () -> {
                joinStarted.countDown();
                manager.addUserToChannel("room", "bob");
              });
      try {
        assertTrue(joinStarted.await(5, TimeUnit.SECONDS));
        assertThrows(TimeoutException.class, () -> joining.get(100, TimeUnit.MILLISECONDS));
      } finally {
        allowDeletion.countDown();
      }
      leaving.get(5, TimeUnit.SECONDS);
      joining.get(5, TimeUnit.SECONDS);
    }
    assertNotNull(manager.getChannel("room"));
    assertEquals(1, manager.numMembers("room"));
    assertArrayEquals(new String[] {"room"}, manager.getChannelNamesForUser("bob"));
    assertEquals(1, manager.getChannelsForUser("bob").length);
  }

  @Test
  @SuppressWarnings("unchecked")
  void disconnectCannotShrinkMembershipWhileBroadcastCopiesItsRecipients() throws Exception {
    var manager = manager();
    manager.addUserToChannel("room", "alice");
    manager.addUserToChannel("room", "bob");
    var copying = new CountDownLatch(1);
    var allowCopy = new CountDownLatch(1);
    var removing = new CountDownLatch(1);
    // Force the size/copy interleaving that can leave a trailing null in a HashSet snapshot.
    var members =
        new HashSet<String>(Set.of("alice", "bob")) {
          @Override
          public Object[] toArray() {
            int originalSize = size();
            copying.countDown();
            try {
              assertTrue(allowCopy.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
              throw new AssertionError(e);
            }
            return super.toArray(new Object[originalSize]);
          }

          @Override
          public boolean remove(Object value) {
            removing.countDown();
            return super.remove(value);
          }
        };
    var memberships =
        (Map<String, Set<String>>) ReflectionTestUtils.getField(manager, "channelMembers");
    memberships.put("room", members);
    var message = new Message(MessageTypes.SERVER_CHANNEL_PUBLIC_MESSAGE, "room alice hello");

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var broadcast = executor.submit(() -> manager.sendMessage("room", message));
      assertTrue(copying.await(5, TimeUnit.SECONDS));
      var leaveStarted = new CountDownLatch(1);
      var leaving =
          executor.submit(
              () -> {
                leaveStarted.countDown();
                manager.removeUserFromChannel("room", "alice");
              });
      try {
        assertTrue(leaveStarted.await(5, TimeUnit.SECONDS));
        assertFalse(removing.await(100, TimeUnit.MILLISECONDS));
      } finally {
        allowCopy.countDown();
      }
      broadcast.get(5, TimeUnit.SECONDS);
      leaving.get(5, TimeUnit.SECONDS);
    }
    verify(sessions).searchForSession("alice");
    verify(sessions).searchForSession("bob");
    verify(sessions, never()).searchForSession(isNull());
    assertEquals(1, manager.numMembers("room"));
  }
}
