package com.deriklima.retronap.search;

import com.deriklima.retronap.model.SearchResult;
import com.deriklima.retronap.model.SharedFile;
import com.deriklima.retronap.model.User;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    return searchByTokens(input);
  }

  public Long countByUserId(UUID userId) {
    return sharedFileRepository.countByUserId(userId);
  }

  public List<SearchResult> searchWithParams(NewSearchParameters params) {
    return searchByTokens(params.getSearchToken()).stream()
        .map(
            sharedFile -> {
              User user = sharedFile.getUser();
              return new SearchResult(
                  sharedFile, user.getNickname(), user.getIpAddress(), user.getLinkSpeedValue());
            })
        .toList();
  }

  /** Matches any whitespace-separated term as a case-insensitive filename substring. */
  private List<SharedFile> searchByTokens(String input) {
    String normalized = input == null ? "" : input.trim();
    if (normalized.isEmpty()) {
      return List.of();
    }

    Map<UUID, SharedFile> matches = new LinkedHashMap<>();
    for (String token : normalized.split("\\s+")) {
      sharedFileRepository
          .findByFilenameContainingIgnoreCase(token)
          .forEach(file -> matches.putIfAbsent(file.getId(), file));
    }
    return List.copyOf(matches.values());
  }
}
