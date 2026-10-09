package com.deriklima.retronap.session;

import com.deriklima.retronap.config.RetroNapConfig;
import com.deriklima.retronap.message.InvalidatedQueueException;
import com.deriklima.retronap.message.Message;
import com.deriklima.retronap.message.MessageContext;
import com.deriklima.retronap.message.MessageFactory;
import com.deriklima.retronap.message.MessageHandler;
import com.deriklima.retronap.message.MessageHandlerFactory;
import com.deriklima.retronap.message.MessageTypes;
import com.deriklima.retronap.message.OutboundMessageQueue;
import com.deriklima.retronap.model.ResumeParameters;
import com.deriklima.retronap.model.SearchParameters;
import com.deriklima.retronap.model.SharedFile;
import com.deriklima.retronap.model.User;
import com.deriklima.retronap.search.SharedFileService;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Session objects are instantiated once a network connection has been established. Responsible for
 * handling incoming messages and sending back replies, as well as keeping some state information
 * about this "session."
 */
@Component
@Scope("prototype")
@Getter
@Slf4j
public class Session implements Runnable, MessageContext {
  private final UserState userState;
  private final MessageFactory messageFactory;
  private final MessageHandlerFactory messageHandlerFactory;
  private final RetroNapConfig config;
  private final ApplicationEventPublisher eventPublisher;
  private final SharedFileService sharedFileService;
  private final SessionStatusScheduler statusScheduler;
  private Socket socket;

  /**
   * Note: no other objects should maintain a reference to this queue, it may be invalidated at any
   * point
   */
  private OutboundMessageQueue outboundMessageQueue;

  private long timeAtWhichConnected;
  private ScheduledFuture<?> statusUpdateTask;
  private boolean terminated;

  public Session(
      UserState userState,
      MessageFactory messageFactory,
      MessageHandlerFactory messageHandlerFactory,
      RetroNapConfig config,
      ApplicationEventPublisher eventPublisher,
      SharedFileService sharedFileService,
      SessionStatusScheduler statusScheduler) {
    this.userState = userState;
    this.messageFactory = messageFactory;
    this.messageHandlerFactory = messageHandlerFactory;
    this.config = config;
    this.eventPublisher = eventPublisher;
    this.sharedFileService = sharedFileService;
    this.statusScheduler = statusScheduler;
  }

  public void init(Socket socket) throws IOException {
    this.socket = socket;
    this.outboundMessageQueue =
        new OutboundMessageQueue(socket.getOutputStream(), config.getServer().getSessionTimeout());
    outboundMessageQueue.startQueue();
    getUserState().setIPAddress(socket.getInetAddress().getAddress());
    this.timeAtWhichConnected = System.currentTimeMillis();
  }

  @Override
  public void run() {
    eventPublisher.publishEvent(new SessionEvent(this, SessionEvent.REGISTER));

    final Socket localSocket = socket;
    try (localSocket;
        InputStream is = localSocket.getInputStream()) {
      while (!Thread.currentThread().isInterrupted() && !localSocket.isClosed()) {
        Message clientMessage = null;
        try {
          clientMessage = messageFactory.createMessage(is);
        } catch (EOFException e) {
          log.warn("End of stream reached for session: {}", getUserState().getNickname());
        }
        if (clientMessage != null) {
          log.debug(
              "[RECEIVED] (USER={}, IP: {}): {}",
              getUserState().getNickname(),
              socket.getInetAddress().getHostAddress(),
              clientMessage);
          MessageHandler handler = messageHandlerFactory.getHandlerForType(clientMessage.getType());
          try {
            handler.handleMessage(clientMessage, this);
          } catch (Exception e) {
            log.error("Error handling message: {}", handler.getClass().getSimpleName(), e);
            try {
              outboundMessageQueue.queueMessage(
                  new Message(MessageTypes.SERVER_ERROR, "Server Error"));
            } catch (InvalidatedQueueException ex) {
              throw new RuntimeException(ex);
            }
          }
        } else {
          break;
        }
      }
    } catch (IOException ioe) {
      log.debug(
          "Client {} disconnected abruptly. Probably the machine went into sleeping mode...",
          getUserState().getNickname());
    } finally {
      cleanup();
    }
  }

  private void cleanup() {
    log.debug("Cleaning up session...");
    synchronized (this) {
      terminated = true;
      stopStatusUpdateThread();
    }
    outboundMessageQueue.invalidate();
    eventPublisher.publishEvent(new SessionEvent(this, SessionEvent.TERMINATE));
    getUserState().removeStatistics();
    cleanUpSharedFiled();
    log.debug("Session clean up. Socket closed: {}", socket.isClosed());
  }

  private void cleanUpSharedFiled() {
    if (getUser() != null) {
      sharedFileService.deleteAllByUserId(getUser().getId());
    }
  }

  public User getUser() {
    return getUserState().getUser();
  }

  @Override
  public void setUser(User user) {
    getUserState().setUser(user);
  }

  /** Returns how long this session has been connected, in secs */
  public int getTimeConnected() {
    return (int) ((System.currentTimeMillis() - timeAtWhichConnected) / 1000);
  }

  @Override
  public boolean isLoggedIn() {
    return getUserState().isLoggedIn();
  }

  @Override
  public void setLoggedIn() {
    getUserState().setLoggedIn();
  }

  @Override
  public String getNickname() {
    return getUserState().getNickname();
  }

  @Override
  public UUID getUserId() {
    return (getUser() != null) ? getUser().getId() : null;
  }

  @Override
  public int getLinkSpeedValue() {
    return (getUser() != null) ? getUser().getLinkSpeedValue() : 0;
  }

  @Override
  public byte[] getIPAddress() {
    return getUserState().getIPAddress();
  }

  @Override
  public void setIPAddress(byte[] ip) {
    getUserState().setIPAddress(ip);
  }

  @Override
  public void addShare(SharedFile share) {
    getUserState().addShare(share);
  }

  @Override
  public void removeShareByFilename(String filename) {
    getUserState().removeShareByFilename(filename);
  }

  @Override
  public SharedFile getShareByFilename(String filename) {
    return getUserState().getShareByFilename(filename);
  }

  @Override
  public SharedFile[] getShares() {
    return getUserState().getShares();
  }

  @Override
  public List<SharedFile> search(SearchParameters params) {
    return getUserState().search(params);
  }

  @Override
  public List<SharedFile> search(ResumeParameters params) {
    return getUserState().search(params);
  }

  public synchronized void startStatusUpdateThread() {
    if (statusUpdateTask == null && !terminated) {
      MessageHandler statusHandler =
          messageHandlerFactory.getHandlerForType(MessageTypes.SERVER_STATS);

      statusUpdateTask =
          statusScheduler.schedule(
              () -> {
                try {
                  OutboundMessageQueue out = getOutboundMessageQueue();
                  if (out == null || out.isInvalid()) {
                    stopStatusUpdateThread();
                  } else {
                    statusHandler.handleMessage(null, this);
                  }
                } catch (Exception e) {
                  log.error("Error in status update thread", e);
                }
              });
    }
  }

  public synchronized void stopStatusUpdateThread() {
    if (statusUpdateTask != null) {
      statusUpdateTask.cancel(false);
      statusUpdateTask = null;
    }
  }

  /**
   * Terminates this session by causing the socket to throw an exception if something tries to read
   * from it. Before doing so, tries flushing the outgoing queue.
   */
  public void kill() {
    getOutboundMessageQueue().flushQueue();
    while (getOutboundMessageQueue().hasMessages()) {
      try {
        //noinspection BusyWait
        Thread.sleep(100);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
    }
    try {
      // force the socket closed.
      socket.close();
    } catch (Exception e) {
      // ignore
    }
  }
}
