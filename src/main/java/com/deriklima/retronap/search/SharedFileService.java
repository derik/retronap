package com.deriklima.retronap.search;

import com.deriklima.retronap.model.SearchResult;
import com.deriklima.retronap.model.SharedFile;
import com.deriklima.retronap.model.User;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SharedFileService {
  private final SharedFileRepository sharedFileRepository;

  @Transactional
  public List<SharedFile> saveAll(List<SharedFile> records, UUID userId) {
    User userRef = new User();
    userRef.setId(userId);
    records.forEach(r -> r.setUser(userRef));
    return sharedFileRepository.saveAll(records);
  }

  @Transactional
  public void deleteAllByUserId(UUID userId) {
    sharedFileRepository.deleteAllByUserId(userId);
  }

  @Transactional
  public SharedFile save(SharedFile sharedFile) {
    return sharedFileRepository.save(sharedFile);
  }

  @Transactional
  public void deleteAll() {
    sharedFileRepository.deleteAll();
  }

  public List<SharedFile> search(String input) {
    // Replace spaces with OR operators for tsquery and add :* at the end of every token
    String tsQuery = input.trim().replaceAll("\\s+", ":* | ") + ":*";
    log.info("tsQuery: {}", tsQuery);
    return sharedFileRepository.searchByTokens(tsQuery);
  }

  public Long countByUserId(UUID userId) {
    return sharedFileRepository.countByUserId(userId);
  }

  public List<SearchResult> searchWithParams(NewSearchParameters params) {
    String tsQuery = params.getSearchToken().trim().replaceAll("\\s+", ":* | ") + ":*";
    log.info("tsQuery: {}", tsQuery);
    return sharedFileRepository.searchByTokens(tsQuery).stream()
        .map(
            sharedFile -> {
              User user = sharedFile.getUser();
              return new SearchResult(
                  sharedFile, user.getNickname(), user.getIpAddress(), user.getLinkSpeedValue());
            })
        .toList();
  }
}
