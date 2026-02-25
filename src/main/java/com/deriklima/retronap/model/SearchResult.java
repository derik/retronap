package com.deriklima.retronap.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Used to encapsulate info about a search result. Both for regular searches and "resume" searches
 * (port not specified in constructor, use setPort())
 */
public class SearchResult {
  @Getter private final String nickname;
  @Getter private final byte[] ipAddress;
  @Getter private final SharedFile share;
  @Getter private final int linkType;
  @Getter @Setter private int port;

  public SearchResult(SharedFile share, String nickname, byte[] ipAddress, int linkType) {
    this.share = share;
    this.nickname = nickname;
    this.ipAddress = ipAddress;
    this.linkType = linkType;
  }
}
