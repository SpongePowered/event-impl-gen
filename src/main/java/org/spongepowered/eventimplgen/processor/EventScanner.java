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
import org.spongepowered.eventgen.annotations.FactoryMethod;
import org.spongepowered.eventgen.annotations.internal.GeneratedEvent;
import org.spongepowered.eventimplgen.AnnotationUtils;
import org.spongepowered.eventimplgen.eventgencore.PropertySearchStrategy;

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
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
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

public class EventScanner {

    static final Pattern DOT_SPLIT = Pattern.compile("\\.");

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
        final RoundEnvironment environment,
        final PropertyConsumer consumer,
        final Set<? extends TypeElement> annotations
    ) {
        if (!environment.getRootElements().isEmpty()) {
            // populate package tree
            this.packages.populate(environment);
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

        this.hydrateIncrementalPackageHierarchy(environment, annotations);

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
                final PackageNode node = this.packages.get((PackageElement) active);

                if (node == null) {
                    this.messager.printMessage(Diagnostic.Kind.ERROR, "Unable to query package metadata", active);
                    failed = true;
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
            return !AnnotationUtils.containsAnnotation(candidate, this.inclusiveAnnotations);
        }
        return false;
    }

    /**
     * Hydrate package hierarchy for indirectly annotated events when
     * compiling incrementally.
     *
     * @param environment environment for determining elements
     */
    private void hydrateIncrementalPackageHierarchy(final RoundEnvironment environment, final Set<? extends TypeElement> annotations) {
        final TypeElement generatedEvent = this.elements.getTypeElement(GeneratedEvent.class.getName());
        if (generatedEvent != null && annotations.contains(generatedEvent)) {
            // when compiling incrementally, rebuild scan information based on known generated events
            final TypeMirror object = this.elements.getTypeElement("java.lang.Object").asType();
            final Set<String> seenPackages = new HashSet<>();
            for (final Element existing : environment.getElementsAnnotatedWith(generatedEvent)) {
                final AnnotationMirror mirror = AnnotationUtils.getAnnotation(existing, GeneratedEvent.class);
                final TypeMirror annotatedType = AnnotationUtils.getValue(mirror, "source");
                if (annotatedType == null || this.types.isSameType(annotatedType, object) || annotatedType.getKind() != TypeKind.DECLARED) {
                    continue;
                }

                final TypeElement eventItf = (TypeElement) ((DeclaredType) annotatedType).asElement();
                // we already are re-processing directly annotated events, so this is just to traverse the package hierarchy
                PackageElement packageElement = (PackageElement) EventImplGenProcessor.topLevelType(eventItf).getEnclosingElement();
                String packageName = packageElement.getQualifiedName().toString();
                if (seenPackages.add(packageName)) {
                    this.packages.set(packageName, packageElement);
                    int dotIndex;
                    while ((dotIndex = packageName.lastIndexOf('.')) != -1) {
                        packageName = packageName.substring(0, dotIndex);
                        if (!seenPackages.add(packageName)) {
                            break;
                        }

                        packageElement = this.elements.getPackageElement(packageName);
                        if (packageElement != null) {
                            this.packages.set(packageName, packageElement);
                        }
                    }
                }
            }
        }

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

    class PackageNode {

        @Nullable PackageElement self; // null at root

        protected final Map<String, PackageNode> knownChildren = new HashMap<>();

        Stream<PackageElement> childPackages() {
            return this.knownChildren.entrySet().stream()
                .filter(Objects::nonNull)
                .flatMap(entry -> {
                    final PackageNode childNode = entry.getValue();

                    // If `self` is null, attempt to retrieve it
                    if (childNode.self == null && this.self != null) {
                        // Build the full package name based on the parent's qualified name
                        String packageName = this.self.getQualifiedName().toString() + '.' + entry.getKey();
                        childNode.self = EventScanner.this.elements.getPackageElement(packageName);
                    }

                    // If `self` is still null, the package is empty but may have children
                    if (childNode.self == null) {
                        // Recursively explore the children of this empty package
                        return childNode.childPackages();
                    }

                    // If the package is not empty, return the package along with any nested child packages
                    return Stream.concat(Stream.of(childNode.self), childNode.childPackages());
                })
                .filter(Objects::nonNull);
        }

        @Override
        public String toString() {
            return this.self == null ? "<unknown>" : this.self.getQualifiedName().toString();
        }
    }

    class RootNode extends PackageNode {

        void populate(final RoundEnvironment env) {
            final Set<String> seen = new HashSet<>();
            for (final TypeElement type : ElementFilter.typesIn(env.getRootElements())) {
                final PackageElement pkg = EventScanner.this.elements.getPackageOf(type);
                final String pkgname = pkg.getQualifiedName().toString();
                if (seen.add(pkgname)) {
                    this.set(pkgname, pkg);
                }
            }
        }

        void set(final String name, final PackageElement element) {
            this.get(name, true).self = element;
        }

        @Nullable PackageNode get(final PackageElement element) {
            final @Nullable PackageNode node = this.get(element.getQualifiedName().toString(), false);
            if (node != null) {
                node.self = element;
            }
            return node;
        }

        PackageNode get(final String packageName, final boolean create) {
            final String[] elements = EventScanner.DOT_SPLIT.split(packageName, -1);
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
