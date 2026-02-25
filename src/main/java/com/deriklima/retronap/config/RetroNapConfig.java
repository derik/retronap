package com.deriklima.retronap.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "retronap")
@Getter
@Setter
public class RetroNapConfig {

  private Server server;
  private Metaserver metaserver;
  private String userPersistenceStore;
  private PathConfig pathConfig;

  @Getter
  @Setter
  public static class Server {
    private boolean enabled;
    private int port;
    private int maxConnections;
    private int sessionTimeout;
  }

  @Getter
  @Setter
  public static class Metaserver {
    private boolean enabled;
    private String serverList;
    private int port;
  }

  @Getter
  @Setter
  public static class PathConfig {
    private String motd;
    private String channels;
  }
}
