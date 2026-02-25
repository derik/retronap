package com.deriklima.retronap.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "napster_users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {
  @Column(length = 30, nullable = false)
  private String nickname;

  @Column(length = 500, nullable = false)
  private String password;

  @Column(length = 10, nullable = false)
  @Enumerated(EnumType.STRING)
  private UserLevelTypes level;

  @Column(nullable = false)
  @Enumerated(EnumType.ORDINAL)
  private UserLinkTypes linkSpeed;

  @Column(length = 25)
  private String clientInfo;

  @Column(length = 30, nullable = false, unique = true)
  private String email;

  private byte[] ipAddress;
  private int downloads;
  private int uploads;
  private int dataPort;

  private long createdLegacy;
  private long lastSeen;

  public User(
      String nickname,
      String password,
      UserLevelTypes level,
      UserLinkTypes linkSpeed,
      String clientInfo,
      String email,
      long createdLegacy,
      long lastSeen) {
    this.nickname = nickname;
    this.password = password;
    this.level = level;
    this.linkSpeed = linkSpeed;
    this.clientInfo = clientInfo;
    this.email = email;
    this.createdLegacy = createdLegacy;
    this.lastSeen = lastSeen;
  }

  public int getLinkSpeedValue() {
    return linkSpeed.getValue();
  }

  public void setLinkSpeedValue(int linkSpeed) {
    setLinkSpeed(UserLinkTypes.fromValue(linkSpeed));
  }
}
