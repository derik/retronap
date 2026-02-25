package com.deriklima.retronap.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfiguration {
  // No additional configuration needed if you're not tracking the user who made the changes
}
