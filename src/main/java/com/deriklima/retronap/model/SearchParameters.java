package com.deriklima.retronap.model;

import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Getter
public class SearchParameters {
  private static final int NONE = -1;
  public static final int AT_LEAST = 0;
  public static final int AT_BEST = 1;
  public static final int EQUAL_TO = 2;

  public static final int FIELD_BITRATE = 0;
  public static final int FIELD_LINESPEED = 1;
  public static final int FIELD_FREQUENCY = 2;

  private static final String[] opStrings = {"AT LEAST", "AT BEST", "EQUAL TO"};

  private String artistName;
  private String[] artistNameTokens;
  private String song;
  private String[] songTokens;
  @Setter private int maxResults;
  @Setter private int bitrate;
  private int bitrateOperator = NONE;
  @Setter private int linespeed;
  private int linespeedOperator = NONE;
  @Setter private int frequency;
  private int frequencyOperator = NONE;

  public void setArtistName(String s) {
    artistName = s;
    String artistNameUppercase = s.toUpperCase();
    artistNameTokens = artistNameUppercase.split(" ");
  }

  public void setSong(String s) {
    song = s;
    String songUppercase = s.toUpperCase();
    songTokens = songUppercase.split(" ");
  }

  public void setBitrateOperator(String op) {
    for (int x = 0; x < opStrings.length; x++) {
      if (opStrings[x].equalsIgnoreCase(op)) {
        setBitrateOperator(x);
        return;
      }
    }
  }

  public void setBitrateOperator(int x) {
    bitrateOperator = x;
  }

  public void setLinespeedOperator(String op) {
    for (int x = 0; x < opStrings.length; x++) {
      if (opStrings[x].equalsIgnoreCase(op)) {
        setLinespeedOperator(x);
        return;
      }
    }
  }

  public void setLinespeedOperator(int x) {
    linespeedOperator = x;
  }

  public void setFrequencyOperator(String op) {
    for (int x = 0; x < opStrings.length; x++) {
      if (opStrings[x].equalsIgnoreCase(op)) {
        setFrequencyOperator(x);
        return;
      }
    }
  }

  public void setFrequencyOperator(int x) {
    frequencyOperator = x;
  }

  public boolean isMatch(int fieldType, int val) {
    int searchOperator = -2;
    int searchValue = -2;

    switch (fieldType) {
      case (FIELD_BITRATE):
        {
          searchOperator = getBitrateOperator();
          searchValue = getBitrate();
          break;
        }
      case (FIELD_LINESPEED):
        {
          searchOperator = getLinespeedOperator();
          searchValue = getLinespeed();
          break;
        }
      case (FIELD_FREQUENCY):
        {
          searchOperator = getFrequencyOperator();
          searchValue = getFrequency();
          break;
        }
    }

    switch (searchOperator) {
      case (NONE):
        {
          return true;
        }
      case (EQUAL_TO):
        {
          if (searchValue == val) {
            return true;
          }
          break;
        }
      case (AT_LEAST):
        {
          if (val >= searchValue) {
            return true;
          }
          break;
        }
      case (AT_BEST):
        {
          if (val <= searchValue) {
            return true;
          }
          break;
        }
    }
    return false;
  }

  public boolean containsArtistNameTokens(String artistNameToTest) {
    String artistNameToTestUppcase = artistNameToTest.toUpperCase();
    String[] tokens = getArtistNameTokens();
    if (tokens == null) return true; // no tokens to test for, so true.
    int numTokens = tokens.length;
    for (int x = 0; x < numTokens; x++) {
      if (artistNameToTestUppcase.indexOf(tokens[x]) == -1) return false;
    }
    return true;
  }

  public boolean containsSongNameTokens(String songNameToTest) {
    String songNameToTestUppcase = songNameToTest.toUpperCase();
    String[] tokens = getSongTokens();
    if (tokens == null) return true; // no tokens to test for, so true.
    int numTokens = tokens.length;
    for (int x = 0; x < numTokens; x++) {
      if (songNameToTestUppcase.indexOf(tokens[x]) == -1) return false;
    }
    return true;
  }
}
