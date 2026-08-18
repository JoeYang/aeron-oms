package io.joeyang.oms.core;

/**
 * Placeholder shared type proving the build wiring end to end.
 *
 * <p>This exists so the toolchain has something real to compile and assert against. It carries no
 * OMS meaning and should be deleted once the first genuine shared type lands.
 */
public final class Greeting {

  private Greeting() {}

  /**
   * Returns a greeting for {@code name}.
   *
   * @param name the subject of the greeting; must not be null or blank
   * @return the greeting text
   * @throws IllegalArgumentException if {@code name} is null or blank
   */
  public static String greet(final String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be null or blank");
    }
    return "Hello, " + name + "!";
  }
}
