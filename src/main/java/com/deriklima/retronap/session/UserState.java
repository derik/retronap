package com.deriklima.retronap.session;

import com.deriklima.retronap.*;
import com.deriklima.retronap.model.ResumeParameters;
import com.deriklima.retronap.model.SearchParameters;
import com.deriklima.retronap.model.SharedFile;
import com.deriklima.retronap.model.User;
import com.deriklima.retronap.statistics.DecreaseFileCountEvent;
import com.deriklima.retronap.statistics.DecreaseTotalLibSizeEvent;
import com.deriklima.retronap.statistics.DecreaseUserCountEvent;
import com.deriklima.retronap.statistics.IncreaseFileCountEvent;
import com.deriklima.retronap.statistics.IncreaseTotalLibSizeEvent;
import com.deriklima.retronap.statistics.IncreaseUserCountEvent;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** UserState maintains some state information about a logged-in user, */
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class UserState {
  private final List<SharedFile> shares = new ArrayList<>();
  private final ApplicationEventPublisher publisher;
  private final int bytesPerMeg = 1000000;
  @Getter private User user;
  private byte[] ipAddress;
  @Getter private boolean loggedIn = false;

  /** User can set nickname / User obj only once */
  private boolean nickSetOnce = false;

  public void addShare(SharedFile s) {
    shares.add(s);
    publisher.publishEvent(new IncreaseFileCountEvent(1));
    publisher.publishEvent(new IncreaseTotalLibSizeEvent(s.getSize() / bytesPerMeg));
  }

  public void removeShareByFilename(String filename) {
    for (SharedFile s : shares) {
      if (filename.equals(s.getAbsolutePath())) {
        shares.remove(s);
        publisher.publishEvent(new DecreaseFileCountEvent(1));
        publisher.publishEvent(new DecreaseTotalLibSizeEvent(s.getSize() / bytesPerMeg));
        return;
      }
    }
  }

  public SharedFile getShareByFilename(String filename) {
    for (SharedFile s : shares) {
      if (filename.contains(s.getAbsolutePath())) {
        return s;
      }
    }
    return null;
  }

  public SharedFile[] getShares() {
    if (shares.isEmpty()) return null;
    return shares.toArray(new SharedFile[0]);
  }

  /** Triggers user count too */
  public void setUser(User user) {
    this.user = user;
    if (!nickSetOnce) {
      publisher.publishEvent(new IncreaseUserCountEvent(1));
      nickSetOnce = true;
    }
  }

  public String getNickname() {
    if (user == null) return null;
    return user.getNickname();
  }

  public byte[] getIPAddress() {
    return ipAddress;
  }

  public void setIPAddress(byte[] s) {
    ipAddress = s;
  }

  public List<SharedFile> search(SearchParameters params) {
    if (!params.isMatch(SearchParameters.FIELD_LINESPEED, user.getLinkSpeedValue())) return null;

    List<SharedFile> results = new ArrayList<>();
    for (SharedFile s : shares) {
      if (params.isMatch(SearchParameters.FIELD_BITRATE, s.getBitrate())
          && params.isMatch(SearchParameters.FIELD_FREQUENCY, s.getFrequency())
          && params.containsSongNameTokens(s.getAbsolutePath())
          && params.containsArtistNameTokens(s.getAbsolutePath())
      //			s.getFilenameUppercase().indexOf(params.getArtistNameUppercase()) != -1
      ) {
        results.add(s);
      }
    }
    if (results.isEmpty()) return null;
    return results;
  }

  public List<SharedFile> search(ResumeParameters params) {
    List<SharedFile> results = new ArrayList<>();
    for (SharedFile s : shares) {
      if (params.getFilesize() == s.getSize() && params.getChecksum().equals(s.getMd5Signature())) {
        results.add(s);
      }
    }
    if (results.isEmpty()) return null;
    return results;
  }

  public void removeStatistics() {
    int fileCount = shares.size();
    int totalSize = 0;
    for (SharedFile sr : shares) {
      totalSize += sr.getSize();
    }
    if (nickSetOnce) {
      this.publisher.publishEvent(new DecreaseUserCountEvent(1));
    }
    this.publisher.publishEvent(new DecreaseFileCountEvent(fileCount));
    this.publisher.publishEvent(new DecreaseTotalLibSizeEvent(totalSize / bytesPerMeg));
  }

  public void setLoggedIn() {
    loggedIn = true;
  }
}
