package io.joeyang.oms.gateway;

import io.joeyang.oms.core.Greeting;

/**
 * Entry point for the gateway process.
 *
 * <p>This process will host the {@code AeronCluster} client and the protocol adapters (FIX,
 * SBE). It reaches the state machine only by submitting ordered ingress to the cluster, never
 * by depending on the clustered service directly — the build enforces that.
 */
public final class GatewayMain {

  private GatewayMain() {}

  public static void main(final String[] args) {
    System.out.println(Greeting.greet("gateway"));
    System.out.println("java: " + Runtime.version());
  }
}
