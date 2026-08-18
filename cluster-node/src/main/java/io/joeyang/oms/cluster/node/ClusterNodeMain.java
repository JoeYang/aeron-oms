package io.joeyang.oms.cluster.node;

import io.joeyang.oms.cluster.service.ServiceIdentity;
import io.joeyang.oms.core.Greeting;

/**
 * Entry point for the cluster node process.
 *
 * <p>This process will host the Aeron {@code ConsensusModule} and {@code
 * ClusteredServiceContainer}. The consensus module is what sequences ingress — this project
 * does not write a sequencer.
 */
public final class ClusterNodeMain {

  private ClusterNodeMain() {}

  public static void main(final String[] args) {
    System.out.println(Greeting.greet("cluster-node"));
    System.out.println("service: " + ServiceIdentity.qualifiedName(0));
    System.out.println("java: " + Runtime.version());
  }
}
