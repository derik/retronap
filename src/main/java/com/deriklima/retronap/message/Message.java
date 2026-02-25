package com.deriklima.retronap.message;

import com.deriklima.retronap.util.Util;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a message, either incoming or outgoing, with some utility functions for easier
 * handling of the data.
 */
public class Message {
  private final int messageType;
  private final int messageLength;
  private final byte[] messageData;
  private List<String> stringTokens = null;
  private boolean parsed = false;

  public Message(int mType, int mLength, byte[] mData) {
    this.messageType = mType;
    this.messageLength = mLength;
    this.messageData = mData;
  }

  public Message(int mType, String strData) {
    this.messageType = mType;
    this.messageData = strData.getBytes(StandardCharsets.ISO_8859_1);
    this.messageLength = this.messageData.length;
  }

  public int getType() {
    return messageType;
  }

  public int getLength() {
    return messageLength;
  }

  /* Need to check for -1 because some messages have varying number of tokens and therefore
  cannot be judged for validity */
  public boolean isValid() {
    if (MessageTypes.getNumTokens(messageType) == MessageTypes.VARIABLETOKENS) {
      return true;
    } else {
      return numDataStringTokens() == MessageTypes.getNumTokens(messageType);
    }
  }

  public byte[] getData() {
    return messageData;
  }

  private synchronized void parseDataToStringTokens() {
    if (parsed) {
      return;
    }

    stringTokens = new ArrayList<>();
    if (messageData.length == 0) {
      parsed = true;
      return;
    }

    boolean parsingToken = false;
    boolean lookForEndQuote = false;
    StringBuilder tokenBuf = new StringBuilder(32);

    for (byte datum : messageData) {
      char ch = (char) datum;
      if (ch == ' ' && !lookForEndQuote) {
        if (parsingToken) {
          stringTokens.add(tokenBuf.toString());
          tokenBuf = new StringBuilder(32);
          parsingToken = false;
        }
      } else if (ch == '"') {
        if (lookForEndQuote) {
          stringTokens.add(tokenBuf.toString());
          tokenBuf = new StringBuilder(32);
          parsingToken = false;
          lookForEndQuote = false;
        } else if (!parsingToken) { // start of new token?
          parsingToken = true;
          lookForEndQuote = true;
        }
      } else {
        tokenBuf.append(ch);
        parsingToken = true;
      }
    }
    if (tokenBuf.length() > 0) {
      stringTokens.add(tokenBuf.toString());
    }
    parsed = true;
  }

  public int numDataStringTokens() {
    parseDataToStringTokens();
    return stringTokens.size();
  }

  public String getDataString() {
    return new String(messageData, StandardCharsets.ISO_8859_1);
  }

  public String getDataString(int index) {
    parseDataToStringTokens();
    if (index >= 0 && index < stringTokens.size()) {
      return stringTokens.get(index);
    }
    return null; // Or throw exception
  }

  public byte[] toByteArray() {
    byte[] retArray = new byte[4 + messageLength];

    byte[] mType = Util.bigToLittleEndian(getType());
    byte[] mLength = Util.bigToLittleEndian(getLength());

    System.arraycopy(mLength, 0, retArray, 0, 2);
    System.arraycopy(mType, 0, retArray, 2, 2);
    System.arraycopy(messageData, 0, retArray, 4, messageLength);

    return retArray;
  }

  /** For debugging */
  @Override
  public String toString() {
    return new StringBuilder(getLength() + 40)
        .append("[Message Packet: type=")
        .append(getType())
        .append(",length=")
        .append(getLength())
        .append(",data=")
        .append(getDataString())
        .append("]")
        .toString();
  }
}
