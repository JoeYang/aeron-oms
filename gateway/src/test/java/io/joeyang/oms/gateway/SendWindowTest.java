package io.joeyang.oms.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The window is the invariant that makes pipelining safe: at most N outstanding, FIFO ack-to-send
 * matching over the single totally-ordered cluster session, and loud failure on any accounting
 * violation — never a silent mis-match.
 */
final class SendWindowTest {

  @Test
  void windowBelowOneIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> new SendWindow(0));
  }

  @Test
  void hasRoomUntilTheWindowFills() {
    final SendWindow window = new SendWindow(2);

    assertTrue(window.hasRoom());
    window.onSent(100L);
    assertTrue(window.hasRoom());
    window.onSent(200L);
    assertFalse(window.hasRoom());
  }

  @Test
  void sendBeyondTheWindowThrows() {
    final SendWindow window = new SendWindow(1);
    window.onSent(1L);

    assertThrows(IllegalStateException.class, () -> window.onSent(2L));
  }

  @Test
  void acksMatchSendsFifoAndReturnTheRtt() {
    final SendWindow window = new SendWindow(2);
    window.onSent(100L);
    window.onSent(250L);

    assertEquals(50L, window.onAck(150L), "first ack answers the first send");
    assertEquals(150L, window.onAck(400L), "second ack answers the second send");
  }

  @Test
  void ackFreesRoom() {
    final SendWindow window = new SendWindow(1);
    window.onSent(1L);
    assertFalse(window.hasRoom());

    window.onAck(2L);

    assertTrue(window.hasRoom());
    assertEquals(0, window.outstanding());
  }

  @Test
  void ackWithoutAnOutstandingSendThrows() {
    final SendWindow window = new SendWindow(4);

    assertThrows(IllegalStateException.class, () -> window.onAck(1L));
  }

  @Test
  void countersTrackSendsAndAcks() {
    final SendWindow window = new SendWindow(3);
    window.onSent(1L);
    window.onSent(2L);
    window.onAck(3L);

    assertEquals(2, window.sent());
    assertEquals(1, window.acked());
    assertEquals(1, window.outstanding());
  }

  @Test
  void ringReusesSlotsPastTheWindowBoundary() {
    final SendWindow window = new SendWindow(2);
    for (long i = 0; i < 10; i++) {
      window.onSent(i * 100);
      assertEquals(7L, window.onAck(i * 100 + 7), "rtt survives ring wrap at send " + i);
    }
    assertEquals(10, window.sent());
    assertEquals(10, window.acked());
  }
}
