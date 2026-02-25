package com.deriklima.retronap.search;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class NewSearchParameters {
  private String artistName;
  private String songName;
  private int maxResults;
  private int bitrate;
  private int lineSpeed;

  public void setLinespeedOperator(String operator) {}

  public void setBitrateOperator(String operator) {}

  public String getSearchToken() {
    if (artistName == null && songName == null) {
      return "";
    }
    if (artistName != null && songName == null) {
      return artistName;
    }
    if (artistName == null && songName != null) {
      return songName;
    }
    return artistName + " " + songName;
  }
}
