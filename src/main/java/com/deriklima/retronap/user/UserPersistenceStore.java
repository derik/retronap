package com.deriklima.retronap.user;

import com.deriklima.retronap.model.User;
import com.deriklima.retronap.model.UserInfo;
import java.util.Optional;
import java.util.UUID;

public interface UserPersistenceStore {
  User findByNickname(String nickname);

  User save(User user);

  void delete(UUID id);

  UserInfo saveUserInfo(UserInfo userInfo, String nickname);

  Optional<User> findById(UUID stalkerUserId);

  Optional<String> findNicknameById(UUID stalkerUserId);
}
