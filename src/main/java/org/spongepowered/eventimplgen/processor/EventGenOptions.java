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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.processing.Messager;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.tools.Diagnostic;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.util.annotation.eventgen.GenerateFactoryMethod;
import org.spongepowered.api.util.annotation.eventgen.NoFactoryMethod;

@Singleton
public class EventGenOptions {
  private static final Pattern COMMA_SPLIT = Pattern.compile(",", Pattern.LITERAL);
  private static final Pattern COLON_SPLIT = Pattern.compile(":", Pattern.LITERAL);

  public static final String GENERATED_EVENT_FACTORY = "eventGenFactory";

  public static final String SORT_PRIORITY_PREFIX = "sortPriorityPrefix"; // default: original
  public static final String GROUPING_PREFIXES = "groupingPrefixes"; // <a>:<b>[,<a>:<b>]* default: from:to

  // these two take fully qualified names to annotations that should include or exclude a certain element from implementation generation
  public static final String INCLUSIVE_ANNOTATIONS = "inclusiveAnnotations"; // default: GenerateFactoryMethod
  public static final String EXCLUSIVE_ANNOTATIONS = "exclusiveAnnotations"; // default: NoFactoryMethod

  private final Messager messager;
  private final Map<String, String> options;

  @Inject
  EventGenOptions(@ProcessorOptions final Map<String, String> options, final Messager messager) {
    this.options = options;
    this.messager = messager;
  }

  public @Nullable String generatedEventFactory() {
    return this.options.get(EventGenOptions.GENERATED_EVENT_FACTORY);
  }

  public @Nullable String sortPriorityPrefix() {
    return this.options.get(EventGenOptions.SORT_PRIORITY_PREFIX);
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
    if (!this.options.containsKey(EventGenOptions.GENERATED_EVENT_FACTORY)) {
      this.messager.printMessage(Diagnostic.Kind.ERROR, "[event-impl-gen]: The " + EventGenOptions.GENERATED_EVENT_FACTORY + " option must be specified to generate a factory.");
      return false;
    }

    return true;
  }
}
