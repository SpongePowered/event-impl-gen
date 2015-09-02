/*
 * Spoon - http://spoon.gforge.inria.fr/
 * Copyright (C) 2006 INRIA Futurs <renaud.pawlak@inria.fr>
 *
 * This software is governed by the CeCILL-C License under French law and
 * abiding by the rules of distribution of free software. You can use, modify
 * and/or redistribute the software under the terms of the CeCILL-C license as
 * circulated by CEA, CNRS and INRIA at http://www.cecill.info.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the CeCILL-C License for more details.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL-C license and that you accept its terms.
 */

package spoon.fixed.reflect.visitor;

import spoon.reflect.code.CtCatchVariable;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtAnnotationType;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtPackageReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.visitor.ImportScanner;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A scanner that calculates the imports for a given model.
 */
public class ImportScannerImpl extends CtScanner implements ImportScanner {

    private Map<String, CtTypeReference<?>> imports = new TreeMap<String, CtTypeReference<?>>();

    @Override
    public <T> void visitCtFieldAccess(CtFieldAccess<T> f) {
        enter(f);
        scan(f.getVariable());
        // scan(fieldAccess.getType());
        scan(f.getAnnotations());
        scanReferences(f.getTypeCasts());
        scan(f.getVariable());
        scan(f.getTarget());
        exit(f);
    }

    @Override
    public <T> void visitCtFieldReference(CtFieldReference<T> reference) {
        enterReference(reference);
        scan(reference.getDeclaringType());
        // scan(reference.getType());
        exitReference(reference);
    }

    @Override
    public <T> void visitCtExecutableReference(
        CtExecutableReference<T> reference) {
        enterReference(reference);
        if (reference.getDeclaringType() != null
            && reference.getDeclaringType().getDeclaringType() == null) {
            addImport(reference.getDeclaringType());
        }
        scanReferences(reference.getActualTypeArguments());
        exitReference(reference);
    }

    @Override
    public <T> void visitCtInvocation(CtInvocation<T> invocation) {
        // For a ctinvocation, we don't have to import declaring type
        scan(invocation.getTarget());
    }

    @Override
    public <T> void visitCtTypeReference(CtTypeReference<T> reference) {
        if (!(reference instanceof CtArrayTypeReference)) {
            if (reference.getDeclaringType() == null) {
                addImport(reference);
            } else {
                addImport(reference.getDeclaringType());
            }
        }
        super.visitCtTypeReference(reference);

    }

    @Override
    public <A extends Annotation> void visitCtAnnotationType(
        CtAnnotationType<A> annotationType) {
        addImport(annotationType.getReference());
        super.visitCtAnnotationType(annotationType);
    }

    @Override
    public <T extends Enum<?>> void visitCtEnum(CtEnum<T> ctEnum) {
        addImport(ctEnum.getReference());
        super.visitCtEnum(ctEnum);
    }

    @Override
    public <T> void visitCtInterface(CtInterface<T> intrface) {
        addImport(intrface.getReference());
        for (CtType<?> t : intrface.getNestedTypes()) {
            addImport(t.getReference());
        }
        super.visitCtInterface(intrface);
    }

    @Override
    public <T> void visitCtClass(CtClass<T> ctClass) {
        addImport(ctClass.getReference());
        for (CtType<?> t : ctClass.getNestedTypes()) {
            addImport(t.getReference());
        }
        super.visitCtClass(ctClass);
    }

    @Override
    public <T> void visitCtCatchVariable(CtCatchVariable<T> catchVariable) {
        for (CtTypeReference<?> type : catchVariable.getMultiTypes()) {
            addImport(type);
        }
        super.visitCtCatchVariable(catchVariable);
    }

    @Override
    public Collection<CtTypeReference<?>> computeImports(
        CtType<?> simpleType) {
        addImport(simpleType.getReference());
        scan(simpleType);
        return getImports(simpleType);
    }

    @Override
    public void computeImports(CtElement element) {
        scan(element);
    }

    @Override
    public boolean isImported(CtTypeReference<?> ref) {
        if (imports.containsKey(ref.getSimpleName())) {
            CtTypeReference<?> exist = imports.get(ref.getSimpleName());
            if (exist.getQualifiedName().equals(ref.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets imports in imports Map for the key simpleType given.
     *
     * @param simpleType The type
     * @return Collection of {@link CtTypeReference}
     */
    private Collection<CtTypeReference<?>> getImports(
        CtType<?> simpleType) {
        if (imports.isEmpty()) {
            return Collections.emptyList();
        }
        CtPackageReference pack = (imports.get(simpleType.getSimpleName())).getPackage();
        List<CtTypeReference<?>> refs = new ArrayList<CtTypeReference<?>>();
        for (CtTypeReference<?> ref : imports.values()) {
            // ignore root package type
            if (ref.getPackage() != null) {
                // ignore java.lang package
                if (!ref.getPackage().getSimpleName().equals("java.lang")) {
                    // ignore type in same package
                    if (!ref.getPackage().getSimpleName()
                        .equals(pack.getSimpleName())) {
                        refs.add(ref);
                    }
                }
            }
        }
        Collections.sort(refs, TypeRefNameComparator.INSTANCE);
        return Collections.unmodifiableCollection(refs);
    }

    /**
     * Adds a type to the imports.
     */
    private boolean addImport(CtTypeReference<?> ref) {
        // ignore non-top-level type
        if (ref.getDeclaringType() != null) {
            return false;
        }
        if (imports.containsKey(ref.getSimpleName())) {
            return isImported(ref);
        }
        imports.put(ref.getSimpleName(), ref);
        return true;
    }

    private static class TypeRefNameComparator implements Comparator<CtTypeReference<?>> {

        private static final Comparator<CtTypeReference<?>> INSTANCE = new TypeRefNameComparator();

        @Override
        public int compare(CtTypeReference<?> o1, CtTypeReference<?> o2) {
            return o1.getQualifiedName().compareTo(o2.getQualifiedName());
        }
    }
}