package com.deriklima.retronap.util;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Miscellaneous utility functions (data conversions, etc) go here */
public class Util {
  // Takes two-byte array in little endian format and
  // converts to java int
  public static int littleToBigEndian(byte[] b) {
    if (b != null && b.length == 2) {
      return (b[0] & 0xFF) | ((b[1] & 0xFF) << 8);
    }
    return 0;
  }

  // Makes two-byte array out of a int, in little endian order
  public static byte[] bigToLittleEndian(int s) {
    return new byte[] {(byte) (s & 0xFF), (byte) (s >> 8)};
  }

  public static <T> List<T> concatLists(List<T> list1, List<T> list2) {
    if (list1 == null || list1.isEmpty()) return list2;
    if (list2 == null || list2.isEmpty()) return list1;
    return Stream.concat(list1.stream(), list2.stream()).collect(Collectors.toList());
  }

  public static long byteArrayToLongIPAddress(byte[] b) {
    if (b == null || b.length != 4) {
      return 0;
    }
    return ((b[3] & 0xFFL) << 24) | ((b[2] & 0xFFL) << 16) | ((b[1] & 0xFFL) << 8) | (b[0] & 0xFFL);
  }

  public static String longToStringIPAddress(long l) {
    return String.format(
        "%d.%d.%d.%d", (l & 0xFF), ((l >> 8) & 0xFF), ((l >> 16) & 0xFF), ((l >> 24) & 0xFF));
  }

  /**
   * Converts a string ip address representation (xxx.xxx.xxx.xxx) to a long value. Returns -1 if
   * couldn't be converted.
   */
  public static long stringToLongIPAddress(String s) {
    if (s == null) {
      return -1;
    }
    String[] parts = s.split("\\.");
    if (parts.length != 4) {
      return -1;
    }
    try {
      byte[] ipAddrArray = new byte[4];
      for (int i = 0; i < 4; i++) {
        int octet = Integer.parseInt(parts[i]);
        if (octet < 0 || octet > 255) {
          return -1;
        }
        ipAddrArray[i] = (byte) octet;
      }
      return byteArrayToLongIPAddress(ipAddrArray);
    } catch (NumberFormatException e) {
      return -1;
    }
  }
}
