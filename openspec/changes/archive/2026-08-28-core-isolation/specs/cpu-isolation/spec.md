# cpu-isolation

## ADDED Requirements

### Requirement: A thread can pin itself onto one CPU

The affinity port SHALL let the calling thread set its own CPU affinity to a single core,
implemented with the JDK 25 FFM API calling `sched_setaffinity` with `pid=0`. No JNI and no
third-party affinity library.

#### Scenario: Pin from inside the thread's own Runnable

- **WHEN** a platform thread calls the pin operation with a valid online CPU id
- **THEN** `sched_getaffinity` for that thread returns a mask containing exactly that CPU

### Requirement: Pin verification fails fast

After pinning, the implementation SHALL read the affinity mask back and raise an error if it
does not equal the requested single-CPU set. A pin that cannot be verified MUST NOT be
reported as success.

#### Scenario: Pin request that cannot take effect

- **WHEN** the pin operation targets a CPU id that does not exist on the machine
- **THEN** the operation raises an error identifying the requested CPU, and no silent
  fallback occurs

### Requirement: Machine isolation state is checkable by script

`scripts/isolation.sh check` SHALL verify the recorded isolation layout — kernel cmdline
parameters, IRQ affinity off the isolated cores, and governor on them — and exit nonzero
naming each missing layer.

#### Scenario: Check on an unconfigured machine

- **WHEN** the check runs on a machine whose cmdline has no isolation parameters
- **THEN** it exits nonzero and names the kernel layer as missing
