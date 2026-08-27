package com.deriklima.retronap.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * ResumeParameters contains parameters for searching for file matches based on checksum and
 * filesize.
 */
@Getter
@Setter
public class ResumeParameters {
  private String checksum;
  private int filesize;

  @Setter(AccessLevel.NONE)
  private int maxResults = 100;
}
