"""JUnit 5 test macro.

Bazel's native `java_test` drives the JUnit 4 runner. JUnit 5 needs the platform console
launcher as the entry point instead, so every test target repeats the same wiring. This macro
holds it in one place.
"""

load("@rules_java//java:defs.bzl", "java_test")

JUNIT5_DEPS = [
    "@maven//:org_junit_jupiter_junit_jupiter_api",
    "@maven//:org_junit_jupiter_junit_jupiter_engine",
    "@maven//:org_junit_jupiter_junit_jupiter_params",
    "@maven//:org_junit_platform_junit_platform_console",
    "@maven//:org_junit_platform_junit_platform_launcher",
    "@maven//:org_junit_platform_junit_platform_reporting",
]

def java_junit5_test(name, srcs, test_package, deps = [], **kwargs):
    """Runs JUnit 5 tests through the platform console launcher.

    Args:
      name: target name.
      srcs: test sources.
      test_package: Java package to scan for tests, e.g. io.joeyang.oms.core.
      deps: non-JUnit dependencies; the JUnit 5 set is appended automatically.
      **kwargs: forwarded to java_test.
    """
    java_test(
        name = name,
        srcs = srcs,
        jvm_flags = ["--enable-native-access=ALL-UNNAMED"],
        use_testrunner = False,
        main_class = "org.junit.platform.console.ConsoleLauncher",
        args = [
            "execute",
            "--select-package=" + test_package,
            "--fail-if-no-tests",
            "--details=summary",
            "--disable-ansi-colors",
            "--disable-banner",
        ],
        deps = deps + JUNIT5_DEPS,
        **kwargs
    )
