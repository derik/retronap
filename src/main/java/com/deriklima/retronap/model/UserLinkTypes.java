package com.deriklima.retronap.model;

import java.util.Arrays;

public enum UserLinkTypes {
  UNKNOWN(0),
  _144KPS(1),
  _288KPS(2),
  _336KPS(3),
  _576KPS(4),
  _64K_ISDN(5),
  _128K_ISDN(6),
  CABLE(7),
  DSL(8),
  T1(9),
  T3(10);

  private final int value;

  UserLinkTypes(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }

  public static UserLinkTypes fromValue(int value) {
    return Arrays.stream(UserLinkTypes.values())
        .filter(linkType -> linkType.value == value)
        .findFirst()
        .orElse(UNKNOWN);
  }
}
