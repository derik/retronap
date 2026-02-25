package com.deriklima.retronap.user;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Banlist facility class. Centralizes tracking of banned ips and nicknames. */
@Component
public class Banlist {
  private final Map<Long, BanRecord> bannedIps = new ConcurrentHashMap<>();
  private final Set<String> bannedNicks = new HashSet<>();

  public void addBannedIP(long ipAddress, String adminNick, String reason) {
    bannedIps.put(ipAddress, new BanRecord(ipAddress, adminNick, reason, getUTCTimeInSeconds()));
  }

  public void addBannedNick(String nick) {
    bannedNicks.add(nick);
  }

  public boolean isBannedIP(long ipAddress) {
    Long ip = ipAddress;
    return bannedIps.get(ip) != null;
  }

  public boolean isBannedNick(String nick) {
    return bannedNicks.contains(nick);
  }

  public void removeBannedIP(long ipAddress) {
    bannedIps.remove(ipAddress);
  }

  public void removeBannedNick(String s) {
    bannedNicks.remove(s);
  }

  public BanRecord[] getBannedIPs() {
    return bannedIps.values().toArray(new BanRecord[0]);
  }

  public String[] getBannedNicks() {
    return bannedNicks.toArray(new String[0]);
  }

  public static long getUTCTimeInSeconds() {
    return System.currentTimeMillis() / (long) 1000;
  }
}
