package com.deriklima.retronap.message;

import com.deriklima.retronap.model.ResumeParameters;
import com.deriklima.retronap.model.SearchParameters;
import com.deriklima.retronap.model.SharedFile;
import com.deriklima.retronap.model.User;
import java.util.List;
import java.util.UUID;

public interface MessageContext {
  OutboundMessageQueue getOutboundMessageQueue();

  User getUser();

  void setUser(User user);

  void kill();

  void startStatusUpdateThread();

  void stopStatusUpdateThread();

  int getTimeConnected();

  boolean isLoggedIn();

  void setLoggedIn();

  String getNickname();

  UUID getUserId();

  int getLinkSpeedValue();

  byte[] getIPAddress();

  void setIPAddress(byte[] ip);

  void addShare(SharedFile share);

  void removeShareByFilename(String filename);

  SharedFile getShareByFilename(String filename);

  SharedFile[] getShares();

  List<SharedFile> search(SearchParameters params);

  List<SharedFile> search(ResumeParameters params);
}
