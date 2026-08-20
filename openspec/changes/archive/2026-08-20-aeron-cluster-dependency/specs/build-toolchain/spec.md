## ADDED Requirements

### Requirement: Internal API export flag present

Binary and test targets SHALL run with the `java.base` internal package that Agrona requires
exported to unnamed modules. Agrona accesses `jdk.internal.misc.Unsafe`, which JDK 25 does
not export by default, so without this flag any code path that constructs a buffer throws
`IllegalAccessError`.

#### Scenario: Flag is applied to executable targets

- **WHEN** a `java_binary` or `java_test` target is executed by Bazel
- **THEN** the JVM runs with `--add-exports java.base/jdk.internal.misc=ALL-UNNAMED`

#### Scenario: The flag is declared once for the whole build

- **WHEN** a reader looks for the option
- **THEN** it is declared in the build configuration that applies to every target, not
  repeated in individual BUILD files

#### Scenario: Only the required package is exported

- **WHEN** the declared JVM options are inspected
- **THEN** no additional `--add-exports` or `--add-opens` option is present for Aeron,
  because Agrona is the only dependency that reaches into JDK internals
