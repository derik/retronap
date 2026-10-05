package com.deriklima.retronap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {"retronap.server.enabled=false", "retronap.metaserver.enabled=false"})
@ActiveProfiles("test")
class ApplicationTests {

  @Test
  void contextLoads() {
    ApplicationModules modules = ApplicationModules.of(Application.class);
    modules.detectViolations().throwIfPresent();
    modules.verify();
  }
}
