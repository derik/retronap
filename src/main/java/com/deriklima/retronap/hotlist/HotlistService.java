package com.deriklima.retronap.hotlist;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HotlistService {
  private final HotlistRepository hotlistRepository;

  @Transactional
  public void save(HotlistEntry hotlistEntry) {
    hotlistRepository.save(hotlistEntry);
  }

  @Transactional
  public void removeStalkerForUser(UUID id, UUID stalkerId) {
    hotlistRepository.deleteByTargetUserIdAndStalkerUserId(id, stalkerId);
  }

  public List<HotlistEntry> findByTargetUserId(UUID id) {
    return hotlistRepository.findByTargetUserId(id);
  }
}
