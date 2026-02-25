package com.deriklima.retronap;

import com.deriklima.retronap.config.RetroNapConfig;
import com.deriklima.retronap.session.Session;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Server class serves as entry point for napster server. Network handling and objects of
 * application-wide scope go here, to be passed to other objects if necessary.
 */
@Slf4j
@Component
public class Server implements Runnable {
  private final int port;
  private final int maxConnections;
  private final ApplicationContext applicationContext;
  private volatile boolean running = true;
  private ServerSocket serverSocket;
  private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

  public Server(RetroNapConfig config, ApplicationContext applicationContext) {
    this.port = config.getServer().getPort();
    this.maxConnections = config.getServer().getMaxConnections();
    this.applicationContext = applicationContext;
  }

  @Override
  public void run() {
    log.info("Starting Napster Server (port={}, maxConnections={})", port, maxConnections);
    try {
      serverSocket = new ServerSocket(port, maxConnections);
      while (running) {
        Socket clientSocket = serverSocket.accept();
        Session session = applicationContext.getBean(Session.class);
        session.init(clientSocket);
        executor.submit(session);
      }
    } catch (IOException e) {
      if (running) {
        log.error("Error in server socket loop: ", e);
      }
    } finally {
      shutdown();
    }
  }

  public void shutdown() {
    running = false;
    try {
      if (serverSocket != null && !serverSocket.isClosed()) {
        serverSocket.close();
      }
    } catch (IOException e) {
      log.error("Error closing server socket", e);
    }
    executor.shutdown();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException e) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
    log.info("Napster server shutting down.");
  }
}
