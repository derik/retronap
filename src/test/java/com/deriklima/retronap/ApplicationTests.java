package com.deriklima.retronap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.core.ApplicationModules;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ApplicationTests {

  @Test
  void contextLoads() {
    ApplicationModules modules = ApplicationModules.of(Application.class);
    modules.detectViolations().throwIfPresent();
    modules.verify();
  }
}
