package com.deriklima.retronap.message;

import com.deriklima.retronap.util.Util;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.stereotype.Component;

/** MessageFactory creates Message objects given an inputstream */
@Component
public class MessageFactory {

  /** Returns null on failure to read */
  public Message createMessage(InputStream is) throws IOException {
    DataInputStream dis = new DataInputStream(is);

    byte[] mLengthBuf = blockRead(dis, 2);
    byte[] mTypeBuf = blockRead(dis, 2);

    if (mLengthBuf == null || mTypeBuf == null) {
      return null;
    }

    int mLength = Util.littleToBigEndian(mLengthBuf);
    int mType = Util.littleToBigEndian(mTypeBuf);
    byte[] mData = new byte[mLength];

    if (mLength > 0) {
      mData = blockReadFully(dis, mLength);
    }

    return new Message(mType, mLength, mData);
  }

  /**
   * For use with InputStream/BufferedInputStream WARNING: THIS DOESN'T WORK. Data sometimes gets
   * lost from stream using read()
   */
  private static byte[] blockRead(InputStream is, int size) throws IOException {
    byte[] tmp = new byte[size];
    int bytesReadTotal = 0;
    while (bytesReadTotal != size) {
      int remaining = size - bytesReadTotal;
      int bytesReadPass = is.read(tmp, bytesReadTotal, remaining);
      if (bytesReadPass != -1) // ! end of stream
      {
        bytesReadTotal += bytesReadPass;
      } else {
        return null;
      }
    }
    return tmp;
  }

  /** For use with DataInputStreams */
  private static byte[] blockReadFully(DataInputStream is, int size) throws IOException {
    byte[] tmp = new byte[size];
    is.readFully(tmp);
    return tmp;
  }
}
