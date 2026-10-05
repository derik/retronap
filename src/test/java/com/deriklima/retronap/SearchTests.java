package com.deriklima.retronap;

import static org.assertj.core.api.Assertions.assertThat;

import com.deriklima.retronap.model.SharedFile;
import com.deriklima.retronap.model.User;
import com.deriklima.retronap.model.UserLevelTypes;
import com.deriklima.retronap.model.UserLinkTypes;
import com.deriklima.retronap.search.NewSearchParameters;
import com.deriklima.retronap.search.SharedFileService;
import com.deriklima.retronap.user.UserPersistenceStore;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"retronap.server.enabled=false", "retronap.metaserver.enabled=false"})
@ActiveProfiles("test")
@Transactional
class SearchTests {
  @Autowired private SharedFileService sharedFileService;
  @Autowired private UserPersistenceStore users;
  private User owner;

  @BeforeEach
  void shareFiles() {
    owner = new User();
    owner.setNickname("search-owner");
    owner.setEmail("search@test.com");
    owner.setPassword("unused");
    owner.setLevel(UserLevelTypes.USER);
    owner.setLinkSpeed(UserLinkTypes.T1);
    owner.setClientInfo("v2.0 BETA 6");
    owner.setIpAddress(new byte[] {127, 0, 0, 1});
    owner = users.save(owner);
    for (String filename :
        List.of("Sunshine.MP3", "Moonlight.mp3", "100%_Live.mp3", "100XALive.mp3")) {
      share("music/", filename);
    }
  }

  @Test
  void matchesSubstringsIgnoringCase() {
    assertThat(sharedFileService.search("sHiNe"))
        .extracting(SharedFile::getFilename)
        .containsExactly("Sunshine.MP3");
    assertThat(sharedFileService.search("music")).isEmpty();
  }

  @Test
  void matchesAnyWhitespaceSeparatedTermAndRemovesDuplicateResults() {
    assertThat(sharedFileService.search("  SHINE\tmoon\nSun SHINE  "))
        .extracting(SharedFile::getFilename)
        .containsExactlyInAnyOrder("Sunshine.MP3", "Moonlight.mp3");
  }

  @Test
  void keepsDistinctFilesWithTheSameFilename() {
    share("other/", "Sunshine.MP3");
    assertThat(sharedFileService.search("sun SHINE"))
        .extracting(SharedFile::getAbsolutePath)
        .containsExactlyInAnyOrder("music/Sunshine.MP3", "other/Sunshine.MP3");
  }

  @Test
  void treatsSqlWildcardCharactersLiterally() {
    for (String query : List.of("%", "_", "100%_")) {
      assertThat(sharedFileService.search(query))
          .extracting(SharedFile::getFilename)
          .containsExactly("100%_Live.mp3");
    }
    assertThat(sharedFileService.search("100%X")).isEmpty();
  }

  @Test
  void blankAndMissingTermsHaveNoMatches() {
    assertThat(sharedFileService.search(null)).isEmpty();
    assertThat(sharedFileService.search("")).isEmpty();
    assertThat(sharedFileService.search(" \t\n ")).isEmpty();
    assertThat(sharedFileService.search("not-present")).isEmpty();
  }

  @Test
  void combinesFilenameClausesAndMapsOwnerDetails() {
    NewSearchParameters params = new NewSearchParameters();
    params.setArtistName("sHiNe");
    params.setSongName("moon SHINE");
    var results = sharedFileService.searchWithParams(params);
    assertThat(results)
        .extracting(result -> result.getShare().getFilename())
        .containsExactlyInAnyOrder("Sunshine.MP3", "Moonlight.mp3");
    assertThat(results)
        .allSatisfy(
            result -> {
              assertThat(result.getNickname()).isEqualTo(owner.getNickname());
              assertThat(result.getIpAddress()).containsExactly(127, 0, 0, 1);
              assertThat(result.getLinkType()).isEqualTo(owner.getLinkSpeedValue());
            });
  }

  private void share(String directory, String filename) {
    SharedFile file =
        new SharedFile(
            directory, filename, directory + filename, "checksum", 1000, 128, 44100, 120);
    file.setUser(owner);
    sharedFileService.save(file);
  }
}
