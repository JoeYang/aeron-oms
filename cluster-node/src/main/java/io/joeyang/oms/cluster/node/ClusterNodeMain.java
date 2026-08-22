package io.joeyang.oms.cluster.node;

import io.joeyang.oms.cluster.service.OmsClusteredService;
import io.joeyang.oms.core.Greeting;

/**
 * Entry point for the cluster node process.
 *
 * <p>This process will host the Aeron {@code ConsensusModule} and {@code
 * ClusteredServiceContainer}. The consensus module is what sequences ingress — this project does
 * not write a sequencer.
 */
public final class ClusterNodeMain {

  private ClusterNodeMain() {}

  /**
   * Process entry point.
   *
   * @param args command-line arguments; currently unused
   */
  public static void main(final String[] args) {
    System.out.println(Greeting.greet("cluster-node"));
    // Constructing the service proves the visibility edge; hosting it arrives with the
    // single-node launcher (mvp-cluster-roundtrip, PR 2).
    System.out.println("service: " + new OmsClusteredService().getClass().getSimpleName());
    System.out.println("java: " + Runtime.version());
  }
}
