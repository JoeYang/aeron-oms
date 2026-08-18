package io.joeyang.oms.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GreetingTest {

  @Test
  @DisplayName("greets a name")
  void greetsByName() {
    assertEquals("Hello, aeron!", Greeting.greet("aeron"));
  }

  @Test
  @DisplayName("rejects null")
  void rejectsNull() {
    assertThrows(IllegalArgumentException.class, () -> Greeting.greet(null));
  }

  @ParameterizedTest
  @DisplayName("rejects blank input")
  @ValueSource(strings = {"", " ", "\t", "\n"})
  void rejectsBlank(final String candidate) {
    assertThrows(IllegalArgumentException.class, () -> Greeting.greet(candidate));
  }
}
