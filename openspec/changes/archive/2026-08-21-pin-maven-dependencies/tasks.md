## 1. Pin

- [x] 1.1 Run `bazel run @maven//:pin` to generate the lock file
- [x] 1.2 Reference the lock file from `MODULE.bazel`
- [x] 1.3 Confirm no dependency version changed as a result

## 2. Verification

- [x] 2.1 `bazel test //...` passes against the pinned resolution
- [x] 2.2 Confirm drift is detected: alter a declared artifact and see the build object
- [x] 2.3 `scripts/lint.sh` exits zero

## 3. Close the debt

- [x] 3.1 Delete `todo/pin-maven-dependencies.md`
