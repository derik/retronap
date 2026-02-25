package com.deriklima.retronap.hotlist;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HotlistRepository
    extends JpaRepository<HotlistEntry, HotlistEntry.HotlistEntryId> {

  List<HotlistEntry> findByTargetUserId(UUID targetUserId);

  void deleteByTargetUserIdAndStalkerUserId(UUID targetUserId, UUID stalkerUserId);
}
