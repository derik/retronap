package com.deriklima.retronap.user;

import com.deriklima.retronap.model.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByNickname(String nickname);

  @Query("SELECT u.nickname FROM User u WHERE u.id = :stalkerUserId")
  Optional<String> findNicknameById(UUID stalkerUserId);

  @Modifying
  @Query("UPDATE User u SET u.downloads = u.downloads + 1 WHERE u.id = :userId")
  void incrementDownloadCount(@Param("userId") UUID userId);

  @Modifying
  @Query("UPDATE User u SET u.downloads = u.downloads - 1 WHERE u.id = :userId AND u.downloads > 0")
  void decrementDownloadCount(@Param("userId") UUID userId);

  @Modifying
  @Query("UPDATE User u SET u.uploads = u.uploads + 1 WHERE u.id = :userId")
  void incrementUploadCount(@Param("userId") UUID userId);

  @Modifying
  @Query("UPDATE User u SET u.uploads = u.uploads - 1 WHERE u.id = :userId AND u.uploads > 0")
  void decrementUploadCount(@Param("userId") UUID userId);
}
