# Security (strict)

## Baseline

- No hardcoded secrets — read from environment variables or a secrets manager, never from a
  committed file
- Validate all input at system boundaries: SBE frames, FIX messages, admin commands, config,
  venue sessions
- No insecure deserialization — decode into typed flyweights with explicit length and bounds
  checks; never deserialize arbitrary Java objects from the wire
- Least privilege for service accounts, venue sessions, and filesystem permissions on the
  journal and snapshots
- Never log credentials, session tokens, account identifiers, or full order payloads at INFO
- No dependencies with known CVEs; flag unmaintained packages before adding them

## Cryptography

- Standard libraries only — `javax.crypto`, JSSE, or BouncyCastle. Never implement crypto.
- TLS on every venue and admin connection, with peer certificate verification. No trust-all
  `TrustManager`, no disabled hostname verification, not even in test configuration that
  could be copied forward.
- Private keys and venue certificates never enter the repo. Load them from config-supplied
  paths and check file permissions at startup.
- Approved primitives: AES-256-GCM, RSA-2048+ or ECDSA P-256+, SHA-256 or better; argon2 or
  bcrypt for any password material. No MD5, SHA-1, ECB mode, or static IVs.
- Credentials expire and rotate — no indefinite-lifetime tokens

## Audit

- Every state-changing command is journaled *before* it is applied. The Aeron journal is the
  audit record: append-only, never edited in place.
- Each audit entry carries enough to reconstruct who submitted what and when: sequenced
  position, ingress timestamp, originating session, and command identity
- The full order lifecycle (new, amend, cancel, fill, reject) must be reconstructable from
  the journal for any point in time
- Journal and snapshot retention is a deliberate, recorded decision — never an accident of
  available disk
- Journals and snapshots are sensitive at rest: restricted file permissions, and encrypted
  if they leave the host
