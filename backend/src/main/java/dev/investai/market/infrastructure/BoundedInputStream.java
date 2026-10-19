package dev.investai.market.infrastructure;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Fails once more than a given number of bytes has been read: caps the inflated size of a download
 * (zip bomb, unexpected file).
 */
final class BoundedInputStream extends FilterInputStream {

  private final long limit;
  private long count;

  BoundedInputStream(InputStream in, long limit) {
    super(in);
    this.limit = limit;
  }

  @Override
  public int read() throws IOException {
    int value = super.read();
    if (value >= 0) {
      add(1);
    }
    return value;
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    int read = super.read(buffer, offset, length);
    if (read > 0) {
      add(read);
    }
    return read;
  }

  @Override
  public long skip(long n) throws IOException {
    long skipped = super.skip(n);
    add(skipped);
    return skipped;
  }

  private void add(long bytes) throws LimitExceededException {
    count += bytes;
    if (count > limit) {
      throw new LimitExceededException();
    }
  }

  /** The limit was reached. */
  static final class LimitExceededException extends IOException {

    private static final long serialVersionUID = 1L;

    LimitExceededException() {
      super("uncompressed size limit exceeded");
    }
  }
}
