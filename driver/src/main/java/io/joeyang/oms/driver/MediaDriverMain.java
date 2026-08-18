package io.joeyang.oms.driver;

import io.joeyang.oms.core.Greeting;

/**
 * Entry point for the standalone media driver process.
 *
 * <p>This process will launch the Aeron {@code MediaDriver}. Running it standalone rather than
 * embedded keeps the transport lifecycle independent of any application process, which matters when
 * duty-cycle threads are pinned to isolated cores.
 */
public final class MediaDriverMain {

  private MediaDriverMain() {}

  /**
   * Process entry point.
   *
   * @param args command-line arguments; currently unused
   */
  public static void main(final String[] args) {
    System.out.println(Greeting.greet("media-driver"));
    System.out.println("java: " + Runtime.version());
  }
}
