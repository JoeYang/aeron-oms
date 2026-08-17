package io.joeyang.oms.cluster.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ServiceIdentityTest {

  @Test
  @DisplayName("qualifies the service name with the cluster id")
  void qualifiesName() {
    assertEquals("oms-service-0", ServiceIdentity.qualifiedName(0));
    assertEquals("oms-service-3", ServiceIdentity.qualifiedName(3));
  }

  @Test
  @DisplayName("rejects a negative cluster id")
  void rejectsNegative() {
    assertThrows(IllegalArgumentException.class, () -> ServiceIdentity.qualifiedName(-1));
  }
}
