package com.deriklima.retronap.user;

import com.deriklima.retronap.model.User;
import com.deriklima.retronap.model.UserInfo;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService implements UserPersistenceStore {
  private final UserRepository userRepository;
  private final UserInfoRepository userInfoRepository;

  @Override
  public User findByNickname(String nickname) {
    return userRepository
        .findByNickname(nickname)
        .orElseThrow(() -> new UserNotFoundException("User not found: " + nickname));
  }

  @Override
  public Optional<User> findById(UUID stalkerUserId) {
    return userRepository.findById(stalkerUserId);
  }

  @Override
  public Optional<String> findNicknameById(UUID stalkerUserId) {
    return userRepository.findNicknameById(stalkerUserId);
  }

  @Override
  @Transactional
  public User save(User user) {
    if (Objects.isNull(user.getId())) {
      user.setCreatedLegacy(System.currentTimeMillis() / 1000);
      user.setLastSeen(System.currentTimeMillis() / 1000);
    }
    return userRepository.save(user);
  }

  @Override
  @Transactional
  public void delete(UUID id) {
    userRepository.deleteById(id);
  }

  @Transactional
  public UserInfo saveUserInfo(UserInfo userInfo, String nickname) {
    User user = userRepository.findByNickname(nickname).orElseThrow();
    userInfo.setUser(user);
    return userInfoRepository.save(userInfo);
  }

  @Transactional
  public void incrementDownloadCount(UUID userId) {
    userRepository.incrementDownloadCount(userId);
  }

  @Transactional
  public void decrementDownloadCount(UUID userId) {
    userRepository.decrementDownloadCount(userId);
  }

  @Transactional
  public void incrementUploadCount(UUID userId) {
    userRepository.incrementUploadCount(userId);
  }

  @Transactional
  public void decrementUploadCount(UUID userId) {
    userRepository.decrementUploadCount(userId);
  }
}
