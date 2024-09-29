/*
 * This file is part of Event Implementation Generator, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.eventimplgen.processor;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.eventgen.annotations.GenerateFactoryMethod;
import org.spongepowered.eventgen.annotations.NoFactoryMethod;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.tools.Diagnostic;

@Singleton
public class EventGenOptions {
  static final Pattern COMMA_SPLIT = Pattern.compile(",", Pattern.LITERAL);
  static final Pattern COLON_SPLIT = Pattern.compile(":", Pattern.LITERAL);

  public static final String GENERATED_EVENT_FACTORY = "eventGenFactory";

  public static final String SORT_PRIORITY_PREFIX = "sortPriorityPrefix"; // default: original
  public static final String GROUPING_PREFIXES = "groupingPrefixes"; // <a>:<b>[,<a>:<b>]* default: from:to

  // these two take fully qualified names to annotations that should include or exclude a certain element from implementation generation
  public static final String INCLUSIVE_ANNOTATIONS = "inclusiveAnnotations"; // default: GenerateFactoryMethod
  public static final String EXCLUSIVE_ANNOTATIONS = "exclusiveAnnotations"; // default: NoFactoryMethod

  public static final String DEBUG = "eventGenDebug"; // default: false, whether to print debug logging

  private boolean validated;
  private boolean valid = true;

  private final Messager messager;
  private final Map<String, String> options;

  @Inject
  EventGenOptions(@ProcessorOptions final Map<String, String> options, final Messager messager) {
    this.options = options;
    this.messager = messager;
  }

  public String generatedEventFactory() {
    return Objects.requireNonNull(this.options.get(EventGenOptions.GENERATED_EVENT_FACTORY), "invalid state, factory name not provided");
  }

  public @Nullable String sortPriorityPrefix() {
    return this.options.getOrDefault(EventGenOptions.SORT_PRIORITY_PREFIX, "original");
  }

  public Map<String, String> groupingPrefixes() {
    final @Nullable String input = this.options.get(EventGenOptions.GROUPING_PREFIXES);
    if (input == null || input.isEmpty()) {
      return Collections.singletonMap("from", "to");
    }

    final Map<String, String> prefixes = new HashMap<>();
    for (final String pair : EventGenOptions.COMMA_SPLIT.split(input, -1)) {
      if (pair.isEmpty()) {
        continue;
      }

      final String[] values = EventGenOptions.COLON_SPLIT.split(pair, 2);
      if (values.length != 2) {
        this.messager.printMessage(
            Diagnostic.Kind.WARNING,
            String.format(
                "[event-impl-gen]: Invalid grouping prefix pair '%s' detected. Must be in the form <a>:<b>",
                pair
            )
        );
        prefixes.put(values[0], values[1]);
      }
    }
    return Collections.unmodifiableMap(prefixes);
  }

  public Set<String> inclusiveAnnotations() {
    return this.commaSeparatedSet(EventGenOptions.INCLUSIVE_ANNOTATIONS, GenerateFactoryMethod.class.getCanonicalName());
  }

  public Set<String> exclusiveAnnotations() {
    return this.commaSeparatedSet(EventGenOptions.EXCLUSIVE_ANNOTATIONS, NoFactoryMethod.class.getCanonicalName());
  }

  public boolean debug() {
    return Boolean.parseBoolean(this.options.getOrDefault(EventGenOptions.DEBUG, "false"));
  }

  private Set<String> commaSeparatedSet(final String key, final String defaultValue) {
    final @Nullable String input = this.options.get(key);
    if (input == null) {
      return Collections.singleton(defaultValue);
    }
    return new HashSet<>(Arrays.asList(EventGenOptions.COMMA_SPLIT.split(input, -1)));
  }

  /**
   * Ensure all options are accurate, and return `false` to skip processing if
   * any issues are detected.
   *
   * <p>This option can safely be called multiple times without printing
   * extraneous error messages.</p>
   *
   * @return whether all options are valid
   */
  public boolean validate() {
    if (this.validated) {
      return this.valid;
    }

    boolean valid = true;
    if (!this.options.containsKey(EventGenOptions.GENERATED_EVENT_FACTORY)) {
      this.messager.printMessage(Diagnostic.Kind.ERROR, "[event-impl-gen]: The " + EventGenOptions.GENERATED_EVENT_FACTORY + " option must be specified to generate a factory.");
      valid = false;
    }

    this.valid = valid;
    this.validated = true;
    return valid;
  }
}
