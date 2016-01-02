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
package org.spongepowered.api.eventimplgen;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.PackageDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VoidType;
import com.google.common.base.Strings;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 *
 */
public class WorkingSource {

    private final Map<String, ClassOrInterfaceDeclaration> classes = new HashMap<>();

    public void add(File file) {
        try {
            for (TypeDeclaration typeDeclaration : JavaParser.parse(file, null, true).getTypes()) {
                if (typeDeclaration instanceof ClassOrInterfaceDeclaration) {
                    addDeclaration((ClassOrInterfaceDeclaration) typeDeclaration);
                }
            }
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private void addDeclaration(ClassOrInterfaceDeclaration declaration) {
        classes.put(getFullyQualifiedName(declaration), declaration);
        for (BodyDeclaration member : declaration.getMembers()) {
            if (member instanceof ClassOrInterfaceDeclaration) {
                addDeclaration((ClassOrInterfaceDeclaration) member);
            }
        }
    }

    public Map<String, ClassOrInterfaceDeclaration> getClasses() {
        return classes;
    }

    public ClassOrInterfaceDeclaration getDeclaration(ClassOrInterfaceType classType, PackageDeclaration _package, List<ImportDeclaration> imports) {
        return classes.get(getFullyQualifiedName(classType, _package, imports));
    }

    public String getFullyQualifiedName(Type type, PackageDeclaration _package, List<ImportDeclaration> imports) {
        if (type instanceof VoidType) {
            return "void";
        } else if (type instanceof PrimitiveType) {
            return ((PrimitiveType) type).getType().name().toLowerCase();
        } else if (type instanceof ReferenceType) {
            final ReferenceType referenceType = (ReferenceType) type;
            return getFullyQualifiedName(referenceType.getType(), _package, imports) + Strings.repeat("[]", referenceType.getArrayCount());
        } else if (type instanceof ClassOrInterfaceType) {
            ClassOrInterfaceType classType = (ClassOrInterfaceType) type;
            String name = classType.getName();
            while (classType.getScope() != null) {
                classType = classType.getScope();
                name = classType.getName() + '.' + name;
            }
            return getFullyQualifiedName(name, _package, imports);
        }
        throw new UnsupportedOperationException("Unrecognized type: " + type);
    }

    private String getFullyQualifiedName(String name, PackageDeclaration _package, List<ImportDeclaration> imports) {
        for (ImportDeclaration _import : imports) {
            final String importedName = _import.getName().toStringWithoutComments();
            final int index = importedName.lastIndexOf(name);
            if (index < 0) {
                continue;
            }
            // Imported name must end with the type name, but at a separator, not in the middle of an identifier
            if ((index == 0 || importedName.charAt(index - 1) == '.') && index + name.length() == importedName.length()) {
                return importedName;
            }
        }
        return _package.getName().toStringWithoutComments() + '.' + name;
    }

    public Set<String> getParentNames(ClassOrInterfaceDeclaration declaration) {
        if (declaration == null) {
            return Collections.emptySet();
        }

        final Set<String> parentNames = new HashSet<>();

        final Queue<ClassOrInterfaceDeclaration> nextParents = new ArrayDeque<>();
        final Queue<Class<?>> nextClasses = new ArrayDeque<>();
        nextParents.add(declaration);

        while (!nextParents.isEmpty()) {
            declaration = nextParents.poll();

            if (!parentNames.add(getFullyQualifiedName(declaration))) {
                continue;
            }

            final PackageDeclaration _package = getPackage(declaration);
            final List<ImportDeclaration> imports = getImports(declaration);

            final ArrayList<ClassOrInterfaceType> directParents = new ArrayList<>(declaration.getImplements());
            directParents.addAll(declaration.getExtends());

            for (ClassOrInterfaceType directParent : directParents) {
                final String qualifiedName = getFullyQualifiedName(directParent, _package, imports);
                final ClassOrInterfaceDeclaration parentDeclaration = classes.get(qualifiedName);
                if (parentDeclaration != null) {
                    nextParents.add(parentDeclaration);
                } else {
                    final Class<?> _class = tryGetClass(qualifiedName);
                    if (_class != null) {
                        nextClasses.add(_class);
                    }
                }
            }
        }

        getParentNames(nextClasses, parentNames);

        parentNames.add(Object.class.getCanonicalName());

        return parentNames;
    }

    private static void getParentNames(Queue<Class<?>> nextParents, Set<String> parentNames) {
        while (!nextParents.isEmpty()) {
            final Class<?> _class = nextParents.poll();

            if (!parentNames.add(_class.getCanonicalName())) {
                continue;
            }

            final Class<?> superclass = _class.getSuperclass();
            if (superclass != null) {
                nextParents.add(superclass);
            }
            Collections.addAll(nextParents, _class.getInterfaces());
        }
    }

    private static Class<?> tryGetClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException exception) {
            return null;
        }
    }

    public static String getFullyQualifiedName(ClassOrInterfaceDeclaration declaration) {
        ClassOrInterfaceDeclaration child = declaration;
        Node parent = child.getParentNode();
        String name = child.getName();
        while (parent instanceof ClassOrInterfaceDeclaration) {
            child = (ClassOrInterfaceDeclaration) parent;
            parent = child.getParentNode();
            name = child.getName() + '.' + name;
        }
        return ((CompilationUnit) parent).getPackage().getName().toStringWithoutComments() + '.' + name;
    }

    public static PackageDeclaration getPackage(ClassOrInterfaceDeclaration declaration) {
        ClassOrInterfaceDeclaration child = declaration;
        Node parent = child.getParentNode();
        while (parent instanceof ClassOrInterfaceDeclaration) {
            child = (ClassOrInterfaceDeclaration) parent;
            parent = child.getParentNode();
        }
        return ((CompilationUnit) parent).getPackage();
    }

    public static List<ImportDeclaration> getImports(ClassOrInterfaceDeclaration declaration) {
        return ((CompilationUnit) getTopLevelDeclaration(declaration).getParentNode()).getImports();
    }

    public static ClassOrInterfaceDeclaration getTopLevelDeclaration(ClassOrInterfaceDeclaration declaration) {
        ClassOrInterfaceDeclaration child = declaration;
        Node parent = child.getParentNode();
        while (parent instanceof ClassOrInterfaceDeclaration) {
            child = (ClassOrInterfaceDeclaration) parent;
            parent = child.getParentNode();
        }
        return child;
    }
}
