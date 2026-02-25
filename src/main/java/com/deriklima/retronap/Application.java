package com.deriklima.retronap;

import com.deriklima.retronap.config.RetroNapConfig;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@RequiredArgsConstructor
public class Application implements CommandLineRunner {

  private final RetroNapConfig config;
  private final MetaServer metaServer;
  private final Server server;

  static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }

  @Override
  public void run(String @NonNull ... args) {
    if (config.getServer().isEnabled()) {
      new Thread(server).start();
    }
    if (config.getMetaserver().isEnabled()) {
      new Thread(metaServer).start();
    }
  }
}
