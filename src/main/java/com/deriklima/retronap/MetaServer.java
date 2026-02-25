package com.deriklima.retronap;

import com.deriklima.retronap.config.RetroNapConfig;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MetaServer implements Runnable {
  private static final int DEFAULT_NAPSTER_PORT = 8888;

  private final RetroNapConfig config;

  private ExecutorService connectionExecutor;
  private final String[] servers;

  public MetaServer(final RetroNapConfig config) {
    this.config = config;
    String serverListStr = config.getMetaserver().getServerList();
    if (serverListStr.isBlank()) {
      log.warn("Server list not specified. Trying to use localhost...");
      try {
        serverListStr = InetAddress.getLocalHost().getHostAddress() + ":" + DEFAULT_NAPSTER_PORT;
      } catch (UnknownHostException e) {
        throw new RuntimeException(
            "Error trying to get local host address. No serverString list was provided.");
      }
    }
    servers =
        Arrays.stream(serverListStr.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toArray(String[]::new);
    log.info("MetaServer serverString list: {}", serverListStr);
  }

  @Override
  public void run() {
    log.info("Starting Napster MetaServer (port={})", config.getMetaserver().getPort());
    connectionExecutor = Executors.newFixedThreadPool(10);
    try (ServerSocket serverSocket = new ServerSocket(config.getMetaserver().getPort(), 100)) {
      int i = 0;
      while (true) {
        try {
          Socket clientSocket = serverSocket.accept();

          connectionExecutor.submit(new ConnectionHandler(clientSocket, servers[i]));

          // Simplified round-robin logic using the modulo operator
          i = (i + 1) % servers.length;
        } catch (IOException e) {
          throw new RuntimeException(
              "Could not start metaserver on port " + config.getMetaserver().getPort());
        }
      }

    } catch (IOException e) {
      throw new RuntimeException(
          "Could not start metaserver on port " + config.getMetaserver().getPort());
    } finally {
      if (Objects.nonNull(connectionExecutor)) {
        connectionExecutor.shutdown();
      }
    }
  }

  private record ConnectionHandler(Socket clientSocket, String serverString) implements Runnable {
    @Override
    public void run() {
      try (Socket s = this.clientSocket;
          PrintWriter writer = new PrintWriter(s.getOutputStream(), true)) {
        log.debug(
            "Sending server {} to client {}", serverString, clientSocket.getRemoteSocketAddress());
        writer.println(serverString);
      } catch (IOException e) {
        log.warn(
            "ConnectionHandler for {} terminated with an exception: ",
            clientSocket.getRemoteSocketAddress(),
            e);
      }
    }
  }
}
