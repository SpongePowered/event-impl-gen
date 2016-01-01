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
package org.spongepowered.api.eventimplgen.classwrapper.javaparser;

import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.google.common.collect.Lists;
import org.spongepowered.api.eventgencore.annotation.ImplementedBy;
import org.spongepowered.api.eventgencore.classwrapper.ClassWrapper;
import org.spongepowered.api.eventgencore.classwrapper.MethodWrapper;
import org.spongepowered.api.eventimplgen.WorkingSource;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

public class JavaParserClassWrapper implements ClassWrapper<Type, MethodDeclaration> {

    private final WorkingSource source;
    private final Type clazz;
    private final ClassOrInterfaceDeclaration declaration;
    private final List<ImportDeclaration> imports;

    public JavaParserClassWrapper(WorkingSource source, List<ImportDeclaration> imports, Type clazz) {
        this.source = source;
        this.clazz = clazz;
        this.declaration = clazz instanceof ClassOrInterfaceType ? source.getDeclaration((ClassOrInterfaceType) clazz, imports) : null;
        this.imports = imports;
    }

    public JavaParserClassWrapper(WorkingSource source, ClassOrInterfaceDeclaration declaration) {
        this.source = source;
        this.clazz = new ClassOrInterfaceType(declaration.getName());
        this.declaration = declaration;
        this.imports = WorkingSource.getImports(declaration);
    }

    @Override
    public String getName() {
        return this.source.getFullyQualifiedName(this.clazz, this.imports);
    }

    @Override
    public List<MethodWrapper<Type, MethodDeclaration>> getMethods() {
        final List<MethodWrapper<Type, MethodDeclaration>> methods = Lists.newArrayList();
        if (this.declaration != null) {
            for (BodyDeclaration member : this.declaration.getMembers()) {
                if (member instanceof MethodDeclaration) {
                    methods.add(new JavaParserMethodWrapper(this.source, this.imports, (MethodDeclaration) member));
                }
            }
        }
        return methods;
    }

    @Override
    public boolean isSubtypeOf(ClassWrapper<Type, MethodDeclaration> other) {
        return this.source.getParentNames(this.declaration).contains(other.getName());
    }

    @Override
    public List<ClassWrapper<Type, MethodDeclaration>> getInterfaces() {
        final List<ClassWrapper<Type, MethodDeclaration>> interfaces = Lists.newArrayList();
        if (this.declaration != null) {
            for (ClassOrInterfaceType klass : (this.declaration.isInterface() ? this.declaration.getExtends() : this.declaration.getImplements())) {
                interfaces.add(new JavaParserClassWrapper(this.source, this.imports, klass));
            }
        }
        return interfaces;
    }

    @Override
    public ClassWrapper<Type, MethodDeclaration> getSuperclass() {
        if (this.declaration != null && !this.declaration.isInterface()) {
            if (!this.declaration.getExtends().isEmpty()) {
                return new JavaParserClassWrapper(this.source, this.imports, this.declaration.getExtends().get(0));
            }
        }
        return null;
    }

    @Override
    public Type getActualClass() {
        return this.clazz;
    }

    @Override
    public boolean isPrimitive(Class<?> other) {
        return this.clazz instanceof PrimitiveType && ((PrimitiveType) this.clazz).getType().name().toLowerCase().equals(other.getCanonicalName());
    }

    @Override
    public ClassWrapper<Type, MethodDeclaration> getBaseClass() {
        if (this.declaration == null) {
            return null;
        }

        final Queue<ClassOrInterfaceDeclaration> queue = new ArrayDeque<>();
        queue.add(this.declaration);
        ClassOrInterfaceDeclaration scanned;

        while ((scanned = queue.poll()) != null) {
            final List<ImportDeclaration> imports = WorkingSource.getImports(scanned);
            for (AnnotationExpr annotation : scanned.getAnnotations()) {
                if (ImplementedBy.class.getSimpleName().equals(annotation.getName().toStringWithoutComments())) {
                    final Expression value;
                    if (annotation instanceof SingleMemberAnnotationExpr) {
                        value = ((SingleMemberAnnotationExpr) annotation).getMemberValue();
                    } else {
                        value = ((NormalAnnotationExpr) annotation).getPairs().get(0).getValue();
                    }
                    return new JavaParserClassWrapper(this.source, imports, ((ClassExpr) value).getType());
                }
            }
            for (ClassOrInterfaceType implInterface : scanned.getImplements()) {
                queue.offer(this.source.getDeclaration(implInterface, imports));
            }
        }
        return null;

    }
}
