package com.deriklima.retronap.search;

import com.deriklima.retronap.model.SharedFile;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
interface SharedFileRepository extends JpaRepository<SharedFile, UUID> {

  @Modifying
  @Query("DELETE FROM SharedFile s WHERE s.user.id = :userId")
  void deleteAllByUserId(UUID userId);

  List<SharedFile> findByBitrate(Integer bitrate);

  List<SharedFile> findByFilenameContainingIgnoreCase(String token);

  List<SharedFile> findByBitrateAndFilename(Integer bitrate, String filename);

  List<SharedFile> findByFilenameContaining(String filename);

  Long countByUserId(UUID userId);
}
