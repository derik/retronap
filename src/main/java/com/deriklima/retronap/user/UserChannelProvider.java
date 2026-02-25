package com.deriklima.retronap.user;

public interface UserChannelProvider {
  String[] getChannelNamesForUser(String nickname);
}
