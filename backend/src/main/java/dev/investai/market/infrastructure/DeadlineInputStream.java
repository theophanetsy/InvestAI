package dev.investai.market.infrastructure;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

/**
 * Fails once a download has lasted longer than allowed: a server sending one byte just before each
 * read timeout would otherwise keep the collection job busy for hours.
 */
final class DeadlineInputStream extends FilterInputStream {

  private final Duration timeout;
  private final long deadline;

  DeadlineInputStream(InputStream in, Duration timeout) {
    super(in);
    this.timeout = timeout;
    this.deadline = System.nanoTime() + timeout.toNanos();
  }

  @Override
  public int read() throws IOException {
    check();
    return super.read();
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    check();
    return super.read(buffer, offset, length);
  }

  private void check() throws DeadlineExceededException {
    if (System.nanoTime() - deadline >= 0) {
      throw new DeadlineExceededException(timeout);
    }
  }

  /** The download lasted longer than allowed. */
  static final class DeadlineExceededException extends IOException {

    private static final long serialVersionUID = 1L;

    DeadlineExceededException(Duration timeout) {
      super("download longer than " + timeout);
    }
  }
}
