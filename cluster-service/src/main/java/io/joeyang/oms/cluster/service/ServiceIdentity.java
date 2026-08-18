package io.joeyang.oms.cluster.service;

/**
 * Placeholder for the clustered service package.
 *
 * <p>Once the Aeron dependency lands, this package holds the {@code ClusteredService}
 * implementation — the deterministic state machine the consensus module drives. Nothing here may
 * perform I/O or read an ambient clock. See {@code .claude/rules/architecture.md}.
 */
public final class ServiceIdentity {

  private ServiceIdentity() {}

  /**
   * Returns the logical service name registered with the cluster.
   *
   * @param clusterId the cluster this service belongs to; must not be negative
   * @return the qualified service name
   * @throws IllegalArgumentException if {@code clusterId} is negative
   */
  public static String qualifiedName(final int clusterId) {
    if (clusterId < 0) {
      throw new IllegalArgumentException("clusterId must not be negative: " + clusterId);
    }
    return "oms-service-" + clusterId;
  }
}
