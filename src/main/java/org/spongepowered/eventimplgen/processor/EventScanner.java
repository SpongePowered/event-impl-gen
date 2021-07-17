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

import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.util.annotation.eventgen.FactoryMethod;
import org.spongepowered.api.util.annotation.eventgen.internal.GeneratedEvent;
import org.spongepowered.eventimplgen.AnnotationUtils;
import org.spongepowered.eventimplgen.eventgencore.PropertySearchStrategy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.QualifiedNameable;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

public class EventScanner {

    private final Set<String> inclusiveAnnotations;
    private final Set<String> exclusiveAnnotations;
    private final boolean debugMode;
    private final Types types;
    private final Elements elements;
    private final Messager messager;
    private final PropertySearchStrategy strategy;
    private final RootNode packages = new RootNode();

    @Inject
    EventScanner(
        final EventGenOptions options,
        final Types types,
        final Elements elemests,
        final Messager messager,
        final PropertySearchStrategy strategy
    ) {
        this.inclusiveAnnotations = options.inclusiveAnnotations();
        this.exclusiveAnnotations = options.exclusiveAnnotations();
        this.debugMode = options.debug();
        this.types = types;
        this.elements = elemests;
        this.messager = messager;
        this.strategy = strategy;
    }

    boolean scanRound(
        final RoundEnvironment environment, final PropertyConsumer consumer,
        final Set<? extends TypeElement> annotations
    ) {
        if (!environment.getRootElements().isEmpty()) {
            // populate package tree
            this.packages.populate(environment, this.elements);
        }
        boolean failed = false;
        final Queue<OriginatedElement> elements = new ArrayDeque<>();
        for (final String inclusiveAnnotation : this.inclusiveAnnotations) {
            final TypeElement element = this.elements.getTypeElement(inclusiveAnnotation);
            if (element == null) {
                this.messager.printMessage(Diagnostic.Kind.ERROR, "Unable to resolve an annotation for specified inclusive annotation " + inclusiveAnnotation);
                failed = true;
            } else {
                if (annotations.contains(element)) {
                    for (final Element el : environment.getElementsAnnotatedWith(element)) {
                        elements.add(OriginatedElement.root(el));
                    }
                }
            }
        }

        if (failed) {
            return false;
        }

        /*
         * Scan elements for the appropriate annotations.
         *
         * We only need to detect indirectly annotated elements here --
         * subpackages, inner classes, etc.
         *
         * The search stops once we find an excluded annotation.
         *
         * Package state needs to be maintained across rounds -- enumerate all
         * packages from the root elements of the round, add that to a store.
         */
        final Set<Element> seen = new HashSet<>();
        OriginatedElement pointer;
        while ((pointer = elements.poll()) != null) {
            final Element active = pointer.element;
            if (!seen.add(active)) {
                continue;
            }
            if (this.debugMode) {
                this.messager.printMessage(Diagnostic.Kind.NOTE, "Testing for events " + (active instanceof QualifiedNameable ? ((QualifiedNameable) active).getQualifiedName() : active.getSimpleName()));
            }

            if (active.getKind() == ElementKind.PACKAGE) {
                // add classes to consider
                final OriginatedElement finalPointer = pointer;
                active.getEnclosedElements().stream()
                    .filter(el -> el.getKind().isInterface() || el.getKind().isClass())
                    .forEach(el -> elements.add(new OriginatedElement(el, finalPointer)));
                // then add subpackages, if they aren't annotated with an excluded element
                final PackageNode node = this.packages.get(((PackageElement) active).getQualifiedName().toString());

                if (node == null) {
                    this.messager.printMessage(Diagnostic.Kind.WARNING, "Unable to query package metadata", active);
                    continue;
                }

                node.childPackages()
                    .filter(pkg -> !this.hasExclusiveAnnotation(pkg))
                    .forEach(pkg -> elements.add(new OriginatedElement(pkg, finalPointer)));
            } else if (active.getKind().isInterface()) {
                final TypeElement event = (TypeElement) active;
                if (!this.hasExclusiveAnnotation(event)) {
                    for (final Element el : event.getEnclosedElements()) {
                        if (el.getKind().isClass() || el.getKind().isInterface()) {
                            elements.add(new OriginatedElement(el, pointer));
                        }
                    }
                    if (!this.isNonTransitivelyExcluded(event)) {
                        if (this.debugMode) {
                            this.messager.printMessage(Diagnostic.Kind.NOTE, "Generating for event " + event.getSimpleName());
                        }
                        final Set<Element> extraOriginating;
                        if (pointer.parent == null) {
                            extraOriginating = Collections.emptySet();
                        } else {
                            extraOriginating = new HashSet<>();
                            OriginatedElement collector = pointer.parent;
                            do {
                                extraOriginating.add(collector.element);
                            } while ((collector = collector.parent) != null);
                        }
                        consumer.propertyFound(event, this.strategy.findProperties(event), extraOriginating);
                        consumer.forwardedMethods(this.findForwardedMethods(event));
                    }
                }
            } else if (active.getKind() == ElementKind.CLASS
                && active.getAnnotation(GeneratedEvent.class) != null) {
                continue; // these implementation classes are indirectly annotated, but because we generated them we can ignore them.
            } else if (active.getKind() != ElementKind.ENUM) { // implicitly exclude enums, they are commonly declared nested in event interfaces
                this.messager.printMessage(Diagnostic.Kind.ERROR, "This element (" + active.getKind() + " " + active.getSimpleName() + ")  was "
                    + "annotated directly or transitively, but it is not a package or interface", active);
                failed = true;
            }
        }

        return !failed;
    }

    public boolean hasExclusiveAnnotation(final Element candidate) {
        return AnnotationUtils.containsAnnotation(candidate, this.exclusiveAnnotations);
    }

    public boolean isNonTransitivelyExcluded(final TypeElement candidate) {
        if (!ElementFilter.typesIn(candidate.getEnclosedElements()).isEmpty()) {
            // no explicit inclusion annotation applied
            for (final AnnotationMirror anno : candidate.getAnnotationMirrors()) {
                if (this.inclusiveAnnotations.contains(this.elements.getBinaryName((TypeElement) anno.getAnnotationType().asElement()).toString())) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private List<ExecutableElement> findForwardedMethods(final TypeElement event) {
        final List<ExecutableElement> methods = new ArrayList<>();
        for (final ExecutableElement method : ElementFilter.methodsIn(event.getEnclosedElements())) {
            if (method.getAnnotation(FactoryMethod.class) != null) {
                boolean failed = false;
                if (!method.getModifiers().contains(Modifier.STATIC) || method.getModifiers().contains(Modifier.PRIVATE)) {
                    this.messager.printMessage(Diagnostic.Kind.ERROR, "Methods annotated with @FactoryMethod must be public and static", method);
                    failed = true;
                }
                if (!this.types.isAssignable(method.getReturnType(), event.asType())) {
                    this.messager.printMessage(Diagnostic.Kind.ERROR, "Methods annotated by @FactoryMethod must return their owning type.", method);
                    failed = true;
                }

                if (failed) {
                    methods.add(method);
                }
            }
        }
        return methods;
    }

    static class PackageNode {

        protected static final Pattern DOT_SPLIT = Pattern.compile("\\.");

        @Nullable PackageElement self; // null at root

        protected final Map<String, PackageNode> knownChildren = new HashMap<>();

        Stream<PackageElement> childPackages() {
            return this.knownChildren.values().stream()
                .map(node -> node.self)
                .filter(Objects::nonNull);
        }

        @Override
        public String toString() {
            return this.self == null ? "<unknown>" : this.self.getQualifiedName().toString();
        }
    }

    static class RootNode extends PackageNode {

        void populate(final RoundEnvironment env, final Elements elements) {
            final Set<String> seen = new HashSet<>();
            for (final TypeElement type : ElementFilter.typesIn(env.getRootElements())) {
                final PackageElement pkg = elements.getPackageOf(type);
                final String pkgname = pkg.getQualifiedName().toString();
                if (seen.add(pkgname)) {
                    this.set(pkgname, pkg);
                }
            }
        }

        void set(final String name, final PackageElement element) {
            this.get(name, true).self = element;
        }


        PackageNode get(final String packageName) {
            return this.get(packageName, false);
        }

        PackageNode get(final String packageName, final boolean create) {
            final String[] elements = PackageNode.DOT_SPLIT.split(packageName, -1);
            PackageNode pointer = this;
            for (final String element : elements) {
                if (create) {
                    pointer = pointer.knownChildren.computeIfAbsent(element, $ -> new PackageNode());
                } else {
                    pointer = pointer.knownChildren.get(element);
                    if (pointer == null) {
                        return null;
                    }
                }
            }
            return pointer;
        }

        @Override
        public String toString() {
            return "<root>";
        }
    }

    static class OriginatedElement {
        final Element element;
        final @Nullable OriginatedElement parent;

        static OriginatedElement root(final Element element) {
            return new OriginatedElement(element, null);
        }

        OriginatedElement(final Element element, final @Nullable OriginatedElement parent) {
            this.element = element;
            this.parent = parent;
        }
    }

}
