package org.spongepowered.eventimplgen.processor;

import org.spongepowered.api.util.annotation.eventgen.ImplementedBy;
import org.spongepowered.eventimplgen.AnnotationUtils;
import org.spongepowered.eventimplgen.eventgencore.Property;
import org.spongepowered.eventimplgen.factory.ClassGenerator;
import org.spongepowered.eventimplgen.factory.ClassGeneratorProvider;
import org.spongepowered.eventimplgen.factory.FactoryInterfaceGenerator;
import org.spongepowered.eventimplgen.factory.NullPolicy;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;

import javax.annotation.processing.Filer;
import javax.inject.Inject;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

public class EventImplWriter implements PropertyConsumer {

    private final Filer filer;
    private final Map<TypeElement, List<Property>> foundProperties;
    private final List<ExecutableElement> forwardedMethods = new ArrayList<>();

    @Inject
    EventImplWriter(final Filer filer, final Elements elements) {
        this.filer = filer;
        this.foundProperties = new TreeMap<>(Comparator.comparing(e -> elements.getBinaryName(e).toString()));
    }

    @Override
    public void propertyFound(
        final TypeElement event, final List<Property> property
    ) {
        this.foundProperties.put(event, property);
    }

    @Override
    public void forwardedMethods(final List<? extends ExecutableElement> elements) {
        this.forwardedMethods.addAll(elements);
    }

    private void dumpClasses() throws IOException {
        final String packageName = this.outputFactory.substring(0, this.outputFactory.lastIndexOf('.'));
        final ClassGeneratorProvider provider = new ClassGeneratorProvider(packageName);

        byte[] clazz = FactoryInterfaceGenerator.createClass(this.outputFactory, this.foundProperties, provider, this.sorter, this.forwardedMethods);
        final int forwardedSize = this.forwardedMethods.size();
        final Element[] originatingElements = new Element[forwardedSize + this.foundProperties.size()];
        System.arraycopy(this.forwardedMethods.toArray(new ExecutableElement[0]), 0, originatingElements, 0, forwardedSize);
        System.arraycopy(this.foundProperties.keySet().toArray(new TypeElement[0]), 0, originatingElements, forwardedSize, this.foundProperties.size());
        try (final OutputStream os = this.filer.createClassFile(this.outputFactory, originatingElements).openOutputStream()) {
            os.write(clazz);
        }
        addClass(destinationDir, this.outputFactory, clazz);

        final ClassGenerator generator = new ClassGenerator();
        generator.setNullPolicy(NullPolicy.NON_NULL_BY_DEFAULT);

        for (final TypeElement event : foundProperties.keySet()) {
            final String name = ClassGenerator.getEventName(event, provider);
            clazz = generator.createClass(event, name, this.getBaseClass(event),
                foundProperties.get(event), this.sorter, FactoryInterfaceGenerator.PLUGINS
            );

            try (final OutputStream os = filer.createClassFile(name, event).openOutputStream()) {
                os.write(clazz);
            }
        }
    }

    private void addClass(final Path destinationDir, final String name, final byte[] clazz) throws IOException {
        final Path classFile = destinationDir.resolve(name.replace('.', File.separatorChar) + ".class");
        Files.write(classFile, clazz, StandardOpenOption.CREATE_NEW);
    }

    private TypeMirror getBaseClass(final TypeElement event, final Types types) {
        AnnotationMirror implementedBy = null;
        int max = Integer.MIN_VALUE;

        final Queue<TypeElement> queue = new ArrayDeque<>();

        queue.add(event);
        TypeElement scannedType;

        while ((scannedType = queue.poll()) != null) {
            final AnnotationMirror anno = AnnotationUtils.getAnnotation(scannedType, ImplementedBy.class);
            final Integer priority = AnnotationUtils.getValue(anno, "priority");
            if (priority != null && priority >= max) {
                implementedBy = anno;
                max = priority;
            }

            for (final TypeMirror implInterface : scannedType.getInterfaces()) {
                final Element element = types.asElement(implInterface);
                if (element == null || !element.getKind().isInterface()) {
                    // todo: error
                    continue;
                }
                queue.offer((TypeElement) element);
            }
        }

        if (implementedBy != null) {
            return AnnotationUtils.getValue(implementedBy, "value");
        }
        return this.elements.getTypeElement("java.lang.Object").asType();
    }
}
