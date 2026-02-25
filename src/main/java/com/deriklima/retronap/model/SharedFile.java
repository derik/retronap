package com.deriklima.retronap.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** SharedFile objects represent a file shared by a user. */
@Entity
@Table(name = "shared_file")
@NoArgsConstructor
@Getter
@Setter
public class SharedFile extends BaseEntity {
  private String dir;
  private String filename;
  private String absolutePath;

  @Column(name = "md5signature")
  private String md5Signature;

  private int size;
  private int bitrate;
  private int frequency;
  private int seconds;

  @OneToOne
  @JoinColumn(name = "napster_user_id")
  private User user;

  public SharedFile(
      String dir,
      String filename,
      String absolutePath,
      String md5Signature,
      int size,
      int bitrate,
      int frequency,
      int seconds) {
    this.dir = dir;
    this.filename = filename;
    this.absolutePath = absolutePath;
    this.md5Signature = md5Signature;
    this.size = size;
    this.bitrate = bitrate;
    this.frequency = frequency;
    this.seconds = seconds;
  }
}
