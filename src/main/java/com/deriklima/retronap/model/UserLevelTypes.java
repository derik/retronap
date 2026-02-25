package com.deriklima.retronap.model;

import lombok.Getter;

public enum UserLevelTypes {
  LEECH("leech"),
  USER("user"),
  MODERATOR("moderator"),
  ADMIN("admin"),
  ELITE("elite");

  @Getter private final String type;

  UserLevelTypes(String type) {
    this.type = type;
  }

  public int getValue() {
    return ordinal();
  }

  public static UserLevelTypes fromType(String type) {
    for (UserLevelTypes userLevel : values()) {
      if (userLevel.type.equalsIgnoreCase(type)) {
        return userLevel;
      }
    }
    throw new IllegalArgumentException("No enum constant with type " + type);
  }
}
