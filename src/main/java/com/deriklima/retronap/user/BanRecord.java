package com.deriklima.retronap.user;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** A BanRecord object stores information about a given IP ban. This is an immutable object. */
@Getter
@RequiredArgsConstructor
public class BanRecord {
  private final long ipAddress;
  private final String adminNick;
  private final String reason;
  private final long time;
}
