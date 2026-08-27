package com.deriklima.retronap.channel;

import com.deriklima.retronap.model.UserLevelTypes;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Stores information about a channel */
@Entity
@Table(name = "channel")
@Getter
@Setter
public class Channel {
  @Id
  @Column(nullable = false, length = 50)
  private String name;

  @Column(nullable = false, length = 255)
  private String topic;

  @Column(nullable = false)
  private boolean permanent = false;

  @Column(name = "channel_limit", nullable = false)
  private int limit = 0;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private UserLevelTypes level = UserLevelTypes.USER; // required level defaults to just "user"
}
