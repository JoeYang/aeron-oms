package io.joeyang.oms.gateway;

import io.aeron.cluster.client.AeronCluster;
import io.aeron.cluster.client.EgressListener;
import io.joeyang.oms.core.time.Clock;
import java.io.PrintStream;

/** One send-and-await-echo streaming mode; the gateway picks exactly one at startup. */
interface RoundTrip extends EgressListener {

  /**
   * Streams {@code count} messages, awaiting each echo.
   *
   * @param cluster connected cluster client
   * @param clock outbound stamp source
   * @param count messages to send
   * @param intervalMs pause between messages
   * @param out per-message report destination
   * @throws InterruptedException if interrupted while pacing
   */
  void run(AeronCluster cluster, Clock clock, int count, long intervalMs, PrintStream out)
      throws InterruptedException;
}
