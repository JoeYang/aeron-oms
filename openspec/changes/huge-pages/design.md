# Design — huge pages for the tape mapping

## Context

`TapeWalker.map` maps each 128 MB segment `READ_ONLY` via `FileChannel.map` into an
`UnsafeBuffer`. The kernel's THP mode is `enabled=[madvise]`, `defrag=[madvise]`: huge pages
activate only on advised ranges, which is exactly the hook this change uses. Whether the ext4
page cache will actually back a `MADV_HUGEPAGE`d file mapping with PMD-mapped large folios on
kernel 6.17 is uncertain — file THP support depends on filesystem large-folio support — so the
design treats it as an experiment with a mandatory readout, not an assumption.

## Goals / Non-Goals

**Goals:**
- Advise every segment mapping `MADV_HUGEPAGE`; verify and report the PMD-mapped extent.
- Measure on the isolated layout with the point-(c) protocol; attribute honestly.

**Non-Goals:**
- hugetlbfs reservations or copying the tape into anonymous THP memory. If in-place advice
  yields zero PMD-mapped bytes, that is a *finding*, reported as such; the copy variant is a
  separate decision to bring to the user, not a silent fallback (it changes what is measured —
  anon memory instead of page cache).
- Huge pages for Aeron's own buffers or the live cluster path.

## Decisions

1. **`MemoryAdvice` port in `//core`** (`adviseHugePages(address, length)`), Linux FFM
   implementation with captured errno, mirroring `ThreadAffinity`/`LinuxThreadAffinity`.
   The address comes from `UnsafeBuffer.addressOffset()` — the mapping is page-aligned by
   construction. `madvise` failure throws at map time (before the measured window).
2. **Advice at map time, per segment**, inside `TapeWalker.map`, controlled by an explicit
   parameter threaded from the caller — not a system property — so the behaviour is visible
   in signatures and unit-testable. Default overloads keep existing call sites unchanged.
3. **Verification via `/proc/self/smaps`**: after the measured walk, sum `FilePmdMapped` for
   the segment-file mappings and print `huge-pages: <n> kB PMD-mapped of <m> kB requested`.
   Zero is a loud, explicit line — the experiment's negative result — never silence. This
   mirrors the pin's read-back rule: an optimisation that cannot be verified must not be
   reported as present.
4. **Measurement gated and comparable**: `isolation.sh check` must pass; identical prefault,
   discard, 3-run protocol; compare against core-isolation point (c).

5. **tmpfs hosting** (decided with the user 2026-08-25, after the ext4 result). The in-place
   experiment returned `0 kB PMD-mapped`: ext4 file THP needs `CONFIG_READ_ONLY_THP_FOR_FS`,
   which this kernel does not set, and that also forecloses the planned `MADV_COLLAPSE`
   escalation. The chosen path hosts the extracted tape on a tmpfs mount with `huge=always`:
   the mapping code, the walk, and the `--huge` flag are unchanged — only the hosting
   filesystem differs, and shmem THP is the mature path on this kernel. Verification extends
   to `ShmemPmdMapped`, which is where smaps accounts tmpfs huge mappings. Because tmpfs
   hosting and page size change together, the measurement takes a tmpfs-without-huge control
   point so the page-size effect is isolated, keeping the single-variable discipline.

## Risks / Trade-offs

- [ext4 may not produce file THP on this kernel] → the smaps readout says so definitively;
  the initiative then reports a negative result and the copy-variant decision goes to the
  user with real data.
- [khugepaged/defrag stalls during the walk] → `defrag=[madvise]` means direct compaction
  may run at fault time on advised ranges; visible in the run's own latency numbers, which
  is the point of measuring.
- [Two nanoTime reads per apply unchanged] → comparison with point (c) is like-for-like.

## Open Questions

None blocking; the file-THP question is answered by the verification line, not by research.
