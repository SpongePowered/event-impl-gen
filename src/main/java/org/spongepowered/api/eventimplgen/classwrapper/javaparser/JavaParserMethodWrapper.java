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
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ModifierSet;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.type.Type;
import com.google.common.collect.Lists;
import org.spongepowered.api.eventgencore.annotation.AbsoluteSortPosition;
import org.spongepowered.api.eventgencore.classwrapper.ClassWrapper;
import org.spongepowered.api.eventgencore.classwrapper.MethodWrapper;
import org.spongepowered.api.eventimplgen.WorkingSource;

import java.util.List;

public class JavaParserMethodWrapper implements MethodWrapper<Type, MethodDeclaration> {

    private final WorkingSource source;
    private final PackageDeclaration _package;
    private final List<ImportDeclaration> imports;
    private final MethodDeclaration method;

    public JavaParserMethodWrapper(WorkingSource source, PackageDeclaration _package, List<ImportDeclaration> imports, MethodDeclaration method) {
        this.source = source;
        this._package = _package;
        this.imports = imports;
        this.method = method;
    }

    @Override
    public String getName() {
        return this.method.getName();
    }

    @Override
    public boolean isPublic() {
        final int modifiers = this.method.getModifiers();
        return ModifierSet.isPublic(modifiers) || !(ModifierSet.isProtected(modifiers) || ModifierSet.isPrivate(modifiers));
    }

    @Override
    public ClassWrapper<Type, MethodDeclaration> getReturnType() {
        return new JavaParserClassWrapper(this.source, this._package, this.imports, this.method.getType());
    }

    @Override
    public List<ClassWrapper<Type, MethodDeclaration>> getParameterTypes() {
        final List<ClassWrapper<Type, MethodDeclaration>> parameters = Lists.newArrayList();
        for (Parameter klass : this.method.getParameters()) {
            parameters.add(new JavaParserClassWrapper(source, this._package, this.imports, klass.getType()));
        }
        return parameters;
    }

    @Override
    public MethodDeclaration getActualMethod() {
        return this.method;
    }

    @Override
    public int getAbsoluteSortPosition() {
        for (AnnotationExpr annotation : this.method.getAnnotations()) {
            if (AbsoluteSortPosition.class.getSimpleName().equals(annotation.getName().toStringWithoutComments())) {
                final Expression value;
                if (annotation instanceof SingleMemberAnnotationExpr) {
                    value = ((SingleMemberAnnotationExpr) annotation).getMemberValue();
                } else {
                    value = ((NormalAnnotationExpr) annotation).getPairs().get(0).getValue();
                }
                return Integer.parseInt(((IntegerLiteralExpr) value).getValue());
            }
        }
        return -1;
    }

    @Override
    public ClassWrapper<Type, MethodDeclaration> getEnclosingClass() {
        return new JavaParserClassWrapper(this.source, (ClassOrInterfaceDeclaration) this.method.getParentNode());
    }

    @Override
    public String toString() {
        return this.method.toStringWithoutComments();
    }
}
