package com.deriklima.retronap.message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Message type definitions, if messages apply to both client and server, then the clients numTokens
 * was used. If numTokens varies, -1 is used.
 */
public class MessageTypes {
  public static final int SERVER_ERROR = 0;
  public static final int CLIENT_LOGIN_REQUEST = 2;
  public static final int SERVER_LOGIN_ACK = 3;
  public static final int SERVER_VERSION_CHECK = 4;
  public static final int CLIENT_ALT_LOGIN_REQUEST = 6;
  public static final int CLIENT_REGISTRATION = 7;
  public static final int SERVER_REGISTRATION_SUCCESS = 8;
  public static final int SERVER_NICKNAME_ALREADY_REGISTERED = 9;
  public static final int CLIENT_CHECK_PASS = 11;
  public static final int SERVER_PASS_OK = 12;
  public static final int CLIENT_REGISTRATION_INFO = 14;
  public static final int CLIENT_SHARE_NOTIFICATION = 100;
  public static final int CLIENT_SHARE_REMOVAL = 102;
  public static final int CLIENT_SEARCH_REQUEST = 200;
  public static final int SERVER_SEARCH_RESPONSE = 201;
  public static final int SERVER_END_SEARCH_RESULTS = 202;
  public static final int CLIENT_DOWNLOAD_REQUEST = 203;
  public static final int SERVER_DOWNLOAD_ACK = 204;
  public static final int PRIVATE_MESSAGE = 205;
  public static final int SERVER_DOWNLOAD_ERROR = 206;
  public static final int CLIENT_HOTLIST_ADD = 207;
  public static final int CLIENT_HOTLIST_CREATE = 208;
  public static final int SERVER_HOTLIST_SIGNON = 209;
  public static final int SERVER_HOTLIST_SIGNOFF = 210;
  public static final int CLIENT_BROWSE_USER_FILES = 211;
  public static final int SERVER_BROWSE_USER_RESPONSE = 212;
  public static final int SERVER_BROWSE_USER_END_RESPONSE = 213;
  public static final int SERVER_STATS = 214;
  public static final int CLIENT_RESUME_REQUEST = 215;
  public static final int SERVER_RESUME_LIST_ENTRY = 216;
  public static final int SERVER_RESUME_LIST_END = 217;
  public static final int CLIENT_DOWNLOAD_NOTIFICATION = 218;
  public static final int CLIENT_DOWNLOAD_COMPLETE_NOTIFICATION = 219;
  public static final int CLIENT_UPLOAD_NOTIFICATION = 220;
  public static final int CLIENT_UPLOAD_COMPLETE_NOTIFICATION = 221;
  public static final int CLIENT_CHECK_PORT = 300;
  public static final int SERVER_HOTLIST_ACK = 301;
  public static final int SERVER_HOTLIST_ERROR = 302;
  public static final int CLIENT_HOTLIST_REMOVE = 303;
  public static final int CLIENT_CHANNEL_JOIN = 400;
  public static final int CLIENT_CHANNEL_LEAVE = 401;
  public static final int CLIENT_CHANNEL_PUBLIC_MESSAGE = 402;
  public static final int SERVER_CHANNEL_PUBLIC_MESSAGE = 403;
  public static final int SERVER_GENERAL_ERROR = 404;
  public static final int SERVER_CHANNEL_JOIN_ACK = 405;
  public static final int SERVER_CHANNEL_JOIN_NOTIFY = 406;
  public static final int SERVER_CHANNEL_LEAVE_NOTIFY = 407;
  public static final int SERVER_CHANNEL_USER_LIST_ENTRY = 408;
  public static final int SERVER_CHANNEL_USER_LIST_END = 409;
  public static final int CHANNEL_TOPIC = 410;
  public static final int CLIENT_ALT_DOWNLOAD_REQUEST = 500;
  public static final int SERVER_ALT_DOWNLOAD_ACK = 501;
  public static final int CLIENT_LINKSPEED_REQUEST = 600;
  public static final int SERVER_LINKSPEED_RESPONSE = 601;
  public static final int CLIENT_WHOIS_REQUEST = 603;
  public static final int SERVER_WHOIS_RESPONSE = 604;
  public static final int SERVER_WHOWAS_RESPONSE = 605;
  public static final int CLIENT_CHANGE_USER_LEVEL = 606;
  public static final int SERVER_UPLOAD_REQUEST = 607;
  public static final int CLIENT_UPLOAD_ACCEPT = 608;
  public static final int CLIENT_ADMIN_DISCONNECT = 610;
  public static final int CLIENT_ADMIN_NUKE = 611;
  public static final int CLIENT_ADMIN_BAN_USER = 612;
  public static final int CLIENT_CHANGE_USER_DATAPORT = 613;
  public static final int CLIENT_ADMIN_UNBAN_USER = 614;
  public static final int CLIENT_ADMIN_SHOW_BANS = 615;
  public static final int SERVER_BAN_LIST_ENTRY = 616;
  public static final int LIST_CHANNELS = 617;
  public static final int LIST_CHANNELS_ENTRY = 618;
  public static final int CLIENT_QUEUE_LIMIT = 619;
  public static final int SERVER_QUEUE_LIMIT = 620;
  public static final int MESSAGE_OF_THE_DAY = 621;
  public static final int CLIENT_CHANGE_USER_LINKSPEED = 625;
  public static final int CLIENT_DATA_PORT_ERROR = 626;
  public static final int CLIENT_ADMIN_GLOBAL_MESSAGE = 628;
  public static final int SERVER_BAN_LIST_USER_ENTRY = 629;
  public static final int CLIENT_CHANGE_LINKSPEED = 700;
  public static final int CLIENT_CHANGE_PASSWORD = 701;
  public static final int CLIENT_CHANGE_EMAIL = 702;
  public static final int CLIENT_CHANGE_DATAPORT = 703;
  public static final int PING = 751;
  public static final int PONG = 752;
  public static final int CLIENT_CHANGE_USER_PASSWORD = 753;
  public static final int CHANNEL_EMOTE = 824;
  public static final int SERVER_CHANNEL_USER_LIST_ENTRY_2 = 825;
  public static final int FULL_CHANNEL_LIST = 827;
  public static final int SERVER_FULL_CHANNEL_INFO = 828;
  public static final int CLIENT_CHANNEL_USER_LIST = 830;
  public static final int CLIENT_DIR_SHARE_NOTIFICATION = 870;

  private static final Map<Integer, Integer> tokens = new ConcurrentHashMap<>();
  /* hash : message type to number of tokens.  For messages with varying number of tokens,
  assigning value of -1 */
  public static final int VARIABLETOKENS = -1;

  static {
    addToken(SERVER_ERROR, 1);
    addToken(CLIENT_LOGIN_REQUEST, VARIABLETOKENS);
    addToken(SERVER_LOGIN_ACK, 1);
    addToken(CLIENT_ALT_LOGIN_REQUEST, 7);
    addToken(CLIENT_REGISTRATION, 1);
    addToken(SERVER_REGISTRATION_SUCCESS, 0);
    addToken(SERVER_NICKNAME_ALREADY_REGISTERED, 0);
    addToken(CLIENT_SHARE_NOTIFICATION, 6);
    addToken(CLIENT_SHARE_REMOVAL, 1);
    addToken(CLIENT_SEARCH_REQUEST, VARIABLETOKENS);
    addToken(SERVER_SEARCH_RESPONSE, 9);
    addToken(SERVER_END_SEARCH_RESULTS, 0);
    addToken(CLIENT_DOWNLOAD_REQUEST, 2);
    addToken(SERVER_DOWNLOAD_ACK, 6);
    addToken(PRIVATE_MESSAGE, VARIABLETOKENS);
    addToken(SERVER_DOWNLOAD_ERROR, 2);
    addToken(CLIENT_HOTLIST_ADD, 1);
    addToken(CLIENT_HOTLIST_CREATE, VARIABLETOKENS);
    addToken(SERVER_HOTLIST_SIGNON, 2);
    addToken(SERVER_HOTLIST_SIGNOFF, 1);
    addToken(CLIENT_BROWSE_USER_FILES, 1);
    addToken(SERVER_BROWSE_USER_RESPONSE, 7);
    addToken(SERVER_BROWSE_USER_END_RESPONSE, 1);
    addToken(SERVER_STATS, 0);
    addToken(CLIENT_RESUME_REQUEST, 2);
    addToken(CLIENT_DOWNLOAD_NOTIFICATION, 0);
    addToken(CLIENT_DOWNLOAD_COMPLETE_NOTIFICATION, 0);
    addToken(CLIENT_UPLOAD_NOTIFICATION, 0);
    addToken(CLIENT_UPLOAD_COMPLETE_NOTIFICATION, 0);
    addToken(SERVER_HOTLIST_ACK, 1);
    addToken(SERVER_HOTLIST_ERROR, 1);
    addToken(CLIENT_HOTLIST_REMOVE, 1);
    addToken(CLIENT_CHANNEL_JOIN, 1);
    addToken(CLIENT_CHANNEL_LEAVE, 1);
    addToken(CLIENT_CHANNEL_PUBLIC_MESSAGE, VARIABLETOKENS);
    addToken(CHANNEL_TOPIC, 2);
    addToken(CLIENT_ALT_DOWNLOAD_REQUEST, 2);
    addToken(SERVER_ALT_DOWNLOAD_ACK, 6);
    addToken(CLIENT_LINKSPEED_REQUEST, 1);
    addToken(SERVER_LINKSPEED_RESPONSE, 2);
    addToken(CLIENT_WHOIS_REQUEST, 1);
    addToken(SERVER_WHOIS_RESPONSE, 6);
    addToken(SERVER_WHOWAS_RESPONSE, 3);
    addToken(CLIENT_CHANGE_USER_LEVEL, 2);
    addToken(SERVER_UPLOAD_REQUEST, 2);
    addToken(CLIENT_UPLOAD_ACCEPT, 2);
    addToken(CLIENT_ADMIN_DISCONNECT, 1);
    addToken(CLIENT_ADMIN_NUKE, 1);
    addToken(CLIENT_ADMIN_BAN_USER, 1);
    addToken(CLIENT_CHANGE_USER_DATAPORT, 2);
    addToken(CLIENT_ADMIN_UNBAN_USER, 1);
    addToken(CLIENT_ADMIN_SHOW_BANS, 0);
    addToken(LIST_CHANNELS, 0);
    addToken(CLIENT_QUEUE_LIMIT, 3);
    addToken(MESSAGE_OF_THE_DAY, VARIABLETOKENS);
    addToken(CLIENT_CHANGE_USER_LINKSPEED, 2);
    addToken(CLIENT_DATA_PORT_ERROR, 2);
    addToken(CLIENT_ADMIN_GLOBAL_MESSAGE, 1);
    addToken(CLIENT_CHANGE_LINKSPEED, 1);
    addToken(CLIENT_CHANGE_PASSWORD, 1);
    addToken(CLIENT_CHANGE_EMAIL, 1);
    addToken(CLIENT_CHANGE_DATAPORT, 1);
    addToken(PING, 1);
    addToken(PONG, 1);
    addToken(CLIENT_CHANGE_USER_PASSWORD, 2); // should be 3, but we'll ignore the 'reason' part.
    addToken(CHANNEL_EMOTE, 2);
    addToken(CLIENT_CHANNEL_USER_LIST, 1);
    addToken(CLIENT_DIR_SHARE_NOTIFICATION, VARIABLETOKENS);
  }

  public static int getNumTokens(int type) {
    Integer mType = tokens.get(type);
    if (mType != null) return mType;
    else return -1;
  }

  private static void addToken(int messageNum, int tokenNum) {
    tokens.put(messageNum, tokenNum);
  }
}
