package com.deriklima.retronap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.deriklima.retronap.model.SharedFile;
import com.deriklima.retronap.model.User;
import com.deriklima.retronap.model.UserLevelTypes;
import com.deriklima.retronap.model.UserLinkTypes;
import com.deriklima.retronap.search.SharedFileService;
import com.deriklima.retronap.user.UserPersistenceStore;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.util.HexFormat;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

class FileDatabaseTests {
  @TempDir Path directory;

  @Test
  void defaultConfigurationPersistsUsersAndSharesAcrossReopens() throws Exception {
    Path database = directory.resolve("nested/retronap");
    UUID userId;
    UUID fileId;
    Integer checksum;
    try (var context = open(database)) {
      try (var connection = context.getBean(DataSource.class).getConnection()) {
        assertEquals("jdbc:h2:file:" + database, connection.getMetaData().getURL());
      }
      User user = new User();
      user.setNickname("persistent");
      user.setEmail("persistent@test.com");
      user.setPassword(context.getBean(PasswordEncoder.class).encode("password"));
      user.setLevel(UserLevelTypes.USER);
      user.setLinkSpeed(UserLinkTypes.T1);
      user.setClientInfo("v2.0 BETA 6");
      user.setIpAddress(new byte[] {127, 0, 0, 1});
      user = context.getBean(UserPersistenceStore.class).save(user);
      userId = user.getId();

      SharedFile file =
          new SharedFile(
              "music/", "Sunshine.mp3", "music/Sunshine.mp3", "checksum", 1000, 128, 44100, 120);
      file.setUser(user);
      fileId = context.getBean(SharedFileService.class).save(file).getId();
      checksum = context.getBean(Flyway.class).info().current().getChecksum();
    }

    assertTrue(Files.isRegularFile(Path.of(database + ".mv.db")));
    try (var context = open(database)) {
      User user = context.getBean(UserPersistenceStore.class).findByNickname("persistent");
      assertEquals(userId, user.getId());
      assertEquals("persistent@test.com", user.getEmail());
      assertArrayEquals(new byte[] {127, 0, 0, 1}, user.getIpAddress());
      assertNotNull(user.getCreated());
      assertTrue(context.getBean(PasswordEncoder.class).matches("password", user.getPassword()));

      var files = context.getBean(SharedFileService.class).search("SHINE");
      assertEquals(1, files.size());
      assertEquals(fileId, files.getFirst().getId());
      assertEquals(userId, files.getFirst().getUser().getId());
      assertEquals("music/Sunshine.mp3", files.getFirst().getAbsolutePath());

      Flyway flyway = context.getBean(Flyway.class);
      flyway.validate();
      assertEquals(checksum, flyway.info().current().getChecksum());
      assertEquals(1, flyway.info().applied().length);
      assertEquals(0, flyway.info().pending().length);
    }
  }

  @Test
  void existingFileMigrationHistoryRemainsValidAfterRelocation() throws Exception {
    byte[] migration = new ClassPathResource("db/migration/V1.0__init.sql").getContentAsByteArray();
    // Fingerprint of the H2 V1 migration before it became the default migration.
    // Keep V1 immutable; future schema changes belong in new versioned migrations.
    byte[] normalizedMigration =
        new String(migration, StandardCharsets.UTF_8)
            .replace("\r\n", "\n")
            .getBytes(StandardCharsets.UTF_8);
    assertEquals(
        "76c4fe292ecd6353799f3bed572aee1c57ac1abcd308b704b9156493fdb72062",
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalizedMigration)));
    Path previousLocation = Files.createDirectory(directory.resolve("previous-migrations"));
    Files.write(previousLocation.resolve("V1.0__init.sql"), migration);
    Path database = directory.resolve("existing");
    String url = "jdbc:h2:file:" + database;
    Flyway previous =
        Flyway.configure()
            .dataSource(url, "sa", "")
            .locations("filesystem:" + previousLocation)
            .load();
    assertEquals(1, previous.migrate().migrationsExecuted);
    Integer checksum = previous.info().current().getChecksum();
    try (var connection = DriverManager.getConnection(url, "sa", "");
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO users (username, password, enabled) VALUES ('existing', 'stored-hash', TRUE)");
    }
    Files.delete(previousLocation.resolve("V1.0__init.sql"));
    Files.delete(previousLocation);

    try (var context = open(database)) {
      Flyway flyway = context.getBean(Flyway.class);
      flyway.validate();
      assertEquals(checksum, flyway.info().current().getChecksum());
      assertEquals(1, flyway.info().applied().length);
      assertEquals(0, flyway.migrate().migrationsExecuted);
      assertEquals(
          "stored-hash",
          context
              .getBean(JdbcTemplate.class)
              .queryForObject(
                  "SELECT password FROM users WHERE username = ?", String.class, "existing"));
    }
  }

  private ConfigurableApplicationContext open(Path database) {
    return new SpringApplicationBuilder(Application.class)
        .run(
            "--RETRONAP_FILE_DATABASE=" + database,
            "--RETRONAP_FILE_DATABASE_USERNAME=sa",
            "--RETRONAP_FILE_DATABASE_PASSWORD=",
            "--retronap.server.enabled=false",
            "--retronap.metaserver.enabled=false",
            "--spring.jpa.show-sql=false",
            "--logging.level.root=WARN",
            "--logging.level.com.deriklima.retronap=WARN");
  }
}
