package io.joeyang.oms.cluster.node;

import io.aeron.cluster.codecs.ClusterActionRequestDecoder;
import io.aeron.cluster.codecs.NewLeadershipTermEventDecoder;
import io.aeron.cluster.codecs.SessionCloseEventDecoder;
import io.aeron.cluster.codecs.SessionOpenEventDecoder;
import io.aeron.cluster.codecs.TimerEventDecoder;
import io.joeyang.oms.sbe.HeartbeatDecoder;
import io.joeyang.oms.sbe.MessageHeaderDecoder;
import java.io.File;
import java.io.PrintStream;
import java.util.Locale;
import org.agrona.DirectBuffer;

/**
 * Read-only tape viewer: decodes a tape's recorded log via {@link TapeWalker} and prints one line
 * per entry — position, entry kind, sequenced timestamp, and the decoded Heartbeat payload for
 * session messages. Human-readable by default; JSONL (one JSON object per line) for tooling.
 * Unknown payloads and unknown entry kinds print rather than throw — viewing must never be stricter
 * than replaying.
 */
final class TapeCat {

  private TapeCat() {}

  static void print(final File archiveDir, final boolean json, final PrintStream out) {
    final MessageHeaderDecoder appHeader = new MessageHeaderDecoder();
    final HeartbeatDecoder heartbeat = new HeartbeatDecoder();

    TapeWalker.walk(
        archiveDir,
        new TapeWalker.EntryHandler() {
          @Override
          public void onSessionMessage(
              final long logPosition,
              final long timestamp,
              final DirectBuffer buffer,
              final int offset,
              final int length) {
            appHeader.wrap(buffer, offset);
            if (appHeader.schemaId() == HeartbeatDecoder.SCHEMA_ID
                && appHeader.templateId() == HeartbeatDecoder.TEMPLATE_ID) {
              heartbeat.wrap(
                  buffer,
                  offset + appHeader.encodedLength(),
                  appHeader.blockLength(),
                  appHeader.version());
              if (json) {
                out.printf(
                    Locale.ROOT,
                    "{\"position\":%d,\"kind\":\"session-message\",\"timestamp\":%d,"
                        + "\"message\":\"Heartbeat\",\"timestampNanos\":%d}%n",
                    logPosition,
                    timestamp,
                    heartbeat.timestampNanos());
              } else {
                out.printf(
                    Locale.ROOT,
                    "%10d  session-message  t=%d  Heartbeat timestampNanos=%d%n",
                    logPosition,
                    timestamp,
                    heartbeat.timestampNanos());
              }
            } else if (json) {
              out.printf(
                  Locale.ROOT,
                  "{\"position\":%d,\"kind\":\"session-message\",\"timestamp\":%d,"
                      + "\"message\":\"unknown\",\"schemaId\":%d,\"templateId\":%d}%n",
                  logPosition,
                  timestamp,
                  appHeader.schemaId(),
                  appHeader.templateId());
            } else {
              out.printf(
                  Locale.ROOT,
                  "%10d  session-message  t=%d  unknown schemaId=%d templateId=%d%n",
                  logPosition,
                  timestamp,
                  appHeader.schemaId(),
                  appHeader.templateId());
            }
          }

          @Override
          public void onOtherEntry(final long logPosition, final int templateId) {
            if (json) {
              out.printf(
                  Locale.ROOT,
                  "{\"position\":%d,\"kind\":\"%s\"}%n",
                  logPosition,
                  kindName(templateId));
            } else {
              out.printf(Locale.ROOT, "%10d  %s%n", logPosition, kindName(templateId));
            }
          }
        });
  }

  static String kindName(final int templateId) {
    if (templateId == TimerEventDecoder.TEMPLATE_ID) {
      return "timer-event";
    }
    if (templateId == SessionOpenEventDecoder.TEMPLATE_ID) {
      return "session-open";
    }
    if (templateId == SessionCloseEventDecoder.TEMPLATE_ID) {
      return "session-close";
    }
    if (templateId == NewLeadershipTermEventDecoder.TEMPLATE_ID) {
      return "new-leadership-term";
    }
    if (templateId == ClusterActionRequestDecoder.TEMPLATE_ID) {
      return "cluster-action";
    }
    return "template-" + templateId;
  }
}
