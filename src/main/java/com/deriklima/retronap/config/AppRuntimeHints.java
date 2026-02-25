package com.deriklima.retronap.config;

import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

class AppRuntimeHints implements RuntimeHintsRegistrar {

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    // Register all config files
    hints.resources().registerPattern("configs/*");
    hints.reflection().registerType(java.util.UUID[].class);

    // If you have subdirectories
    // hints.resources().registerPattern("config/**");
  }
}
