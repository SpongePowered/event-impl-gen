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

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotableNode;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.ModifierSet;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.body.VariableDeclaratorId;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.LiteralExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import org.gradle.api.DefaultTask;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.spongepowered.api.eventgencore.AccessorFirstStrategy;
import org.spongepowered.api.eventgencore.Property;
import org.spongepowered.api.eventgencore.PropertySearchStrategy;
import org.spongepowered.api.eventgencore.annotation.PropertySettings;
import org.spongepowered.api.eventgencore.annotation.codecheck.CompareTo;
import org.spongepowered.api.eventgencore.annotation.codecheck.FactoryCodeCheck;
import org.spongepowered.api.eventgencore.classwrapper.MethodWrapper;
import org.spongepowered.api.eventimplgen.classwrapper.javaparser.JavaParserClassWrapper;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;

public class EventImplGenTask extends DefaultTask {

    private EventImplGenExtension extension;
    private WorkingSource workingSource;

    @TaskAction
    public void task() throws Exception {
        // Configure AST generator
        extension = getProject().getExtensions().getByType(EventImplGenExtension.class);
        Preconditions.checkState(!extension.eventImplCreateMethod.isEmpty(), "Extension property eventImplCreateMethod isn't defined");
        final SourceSet sourceSet =
            getProject().getConvention().getPlugin(JavaPluginConvention.class).getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        workingSource = new WorkingSource();
        for (File sourceFile : sourceSet.getAllJava().getFiles()) {
            workingSource.add(sourceFile);
        }
        // Generate properties from source
        final PropertySearchStrategy<Type, MethodDeclaration> searchStrategy = new AccessorFirstStrategy<>();
        final Map<String, Collection<? extends Property<Type, MethodDeclaration>>> foundProperties = new TreeMap<>();
        for (Map.Entry<String, ClassOrInterfaceDeclaration> entry : workingSource.getClasses().entrySet()) {
            final String qualifiedName = entry.getKey();
            if (!extension.isIncluded(qualifiedName)) {
                continue;
            }
            final ClassOrInterfaceDeclaration event = entry.getValue();
            final Collection<? extends Property<Type, MethodDeclaration>> eventProps =
                searchStrategy.findProperties(new JavaParserClassWrapper(workingSource, event));
            foundProperties.put(qualifiedName, eventProps);
        }

        // System.out.println(new JavaParserClassWrapper(workingSource, workingSource.getClasses().get("org.spongepowered.api.event.entity.ai"
        //     + ".AITaskEvent.Add")).getMethods());

        // Modify factory AST: recreate methods
        final ClassOrInterfaceDeclaration factoryClass = workingSource.getClasses().get(extension.outputFactory);
        factoryClass.getMembers().clear();
        for (Map.Entry<String, Collection<? extends Property<Type, MethodDeclaration>>> entry : foundProperties.entrySet()) {
            final String qualifiedName = entry.getKey();
            final ClassOrInterfaceDeclaration event = workingSource.getClasses().get(qualifiedName);
            final MethodDeclaration method = new MethodDeclaration(ModifierSet.PUBLIC | ModifierSet.STATIC, new ClassOrInterfaceType(event.getName()),
                "create" + generateName(event, (s1, s2) -> s1 + s2));
            final Map<MethodDeclaration, Parameter> parameterMap = generateMethodParameters(entry.getValue());
            method.setParameters(new ArrayList<>(parameterMap.values()));
            method.setBody(generateMethodBody(extension.eventImplCreateMethod, event, qualifiedName, parameterMap));
            method.setJavaDoc(generateDocComment(event, qualifiedName, method.getParameters()));
            factoryClass.getMembers().add(method);
        }

        System.out.println(factoryClass);
    }

    private Map<MethodDeclaration, Parameter> generateMethodParameters(Collection<? extends Property<Type, MethodDeclaration>> properties) {
        final Map<MethodDeclaration, Parameter> parameters = new LinkedHashMap<>();
        properties = new PropertySorter(extension.sortPriorityPrefix, extension.groupingPrefixes).sortProperties(properties);
        for (Property<Type, MethodDeclaration> property : properties) {
            if (shouldAdd(property)) {
                parameters.put(property.getMostSpecificMethod(),
                    new Parameter(property.getMostSpecificType(), new VariableDeclaratorId(property.getName())));
            }
        }
        return parameters;
    }

    private BlockStmt generateMethodBody(String eventImplCreationMethod, ClassOrInterfaceDeclaration event, String qualifiedName,
        Map<MethodDeclaration, Parameter> parameters) {
        final List<Statement> statements = new ArrayList<>();
        // Preconditions.checkArgument(param1 == param2, String.format("Error message", targetEntity, goal.getOwner()));
        //generatePreconditionsCheck(event, qualifiedName, statements, parameters);
        // Map<String, Object> values = Maps.newHashMap();
        final MethodCallExpr mapsNewHashMap = new MethodCallExpr(new NameExpr(Maps.class.getSimpleName()), "newHashMap");
        final ClassOrInterfaceType mapStringObject = new ClassOrInterfaceType(Map.class.getSimpleName());
        mapStringObject.setTypeArgs(Arrays.asList(new ClassOrInterfaceType(String.class.getSimpleName()),
            new ClassOrInterfaceType(Object.class.getSimpleName())));
        statements.add(new ExpressionStmt(new VariableDeclarationExpr(mapStringObject,
            Collections.singletonList(new VariableDeclarator(new VariableDeclaratorId("values"), mapsNewHashMap)))));
        // values.put("param1", param1); values.put("param2", param2); ...
        for (Parameter parameter : parameters.values()) {
            final String parameterName = parameter.getId().getName();
            statements.add(new ExpressionStmt(new MethodCallExpr(new NameExpr("values"), "put",
                Arrays.asList(new StringLiteralExpr(parameterName), new NameExpr(parameterName)))));
        }
        // return SpongeEventFactoryUtils.createEventImpl(Event.class, values);
        final int classMethodSeparator = eventImplCreationMethod.lastIndexOf('.');
        final String createClassName = eventImplCreationMethod.substring(0, classMethodSeparator);
        final String createMethodName = eventImplCreationMethod.substring(classMethodSeparator + 1);
        statements.add(new ReturnStmt(new MethodCallExpr(new NameExpr(createClassName), createMethodName,
            Arrays.asList(new ClassExpr(new ClassOrInterfaceType(event.getName())), new NameExpr("values")))));
        return new BlockStmt(statements);
    }

    private static JavadocComment generateDocComment(ClassOrInterfaceDeclaration event, String qualifiedName, List<Parameter> parameters) {
        final StringBuilder comment = new StringBuilder();
        comment.append("AUTOMATICALLY GENERATED, DO NOT EDIT.\n");
        comment.append("Creates a new instance of\n");
        comment.append("{@link ").append(qualifiedName).append("}.\n");
        comment.append('\n');
        for (Parameter parameter : parameters) {
            final String name = parameter.getId().getName();
            comment.append("@param ").append(name).append(" The ").append(camelCaseToWords(name)).append('\n');
        }
        comment.append("@return A new ").append(generateName(event, (s1, s2) -> camelCaseToWords(s1) + ' ' + camelCaseToWords(s2)));
        return new JavadocComment(comment.toString());
    }

    private static String generateName(ClassOrInterfaceDeclaration declaration, BiFunction<String, String, String> combiner) {
        ClassOrInterfaceDeclaration child = declaration;
        Node parent = child.getParentNode();
        String name = child.getName();
        while (parent instanceof ClassOrInterfaceDeclaration) {
            child = (ClassOrInterfaceDeclaration) parent;
            parent = child.getParentNode();
            name = combiner.apply(child.getName(), name);
        }
        return name;
    }

    private void generatePreconditionsCheck(ClassOrInterfaceDeclaration event, String qualifiedName, List<Statement> statements,
        Map<MethodDeclaration, Parameter> parameters) {

        final AnnotationExpr check = getAnnotation(event, FactoryCodeCheck.class);
        if (check == null) {
            return;
        }

        final StringLiteralExpr valueExpression = getAnnotationValue(check, FactoryCodeCheck.class, "value");
        final String value = valueExpression.getValue();
        final Map<MethodDeclaration, AnnotationExpr> annotations = findCompareAnnotations(value, event);
        if (annotations.size() != 2) {
            throw new IllegalStateException(String.format("@FactoryCodeCheck annotation is present on interface %s, but a @CompareTo annotation"
                + " pair was not found! Instead, the following annotated methods were found: %s", qualifiedName, annotations));
        }

        final List<Map.Entry<MethodDeclaration, AnnotationExpr>> sortedAnnotations = new ArrayList<>(annotations.entrySet());
        sortedAnnotations.sort((c1, c2) -> getPositionValue(c1.getValue()) - getPositionValue(c2.getValue()));

        final MethodDeclaration method1 = sortedAnnotations.get(0).getKey();
        final AnnotationExpr annotation1 = sortedAnnotations.get(0).getValue();
        final Parameter param1 = parameters.get(method1);

        final MethodDeclaration method2 = sortedAnnotations.get(1).getKey();
        final AnnotationExpr annotation2 = sortedAnnotations.get(1).getValue();
        final Parameter param2 = parameters.get(method2);
        System.out.println(method1 + "\n" + method2 + "\n" + parameters);

        final StringLiteralExpr errorMessageExpression = getAnnotationValue(check, FactoryCodeCheck.class, "errorMessage");
        final MethodCallExpr stringFormat = new MethodCallExpr(new NameExpr(String.class.getSimpleName()), "format",
            Arrays.asList(errorMessageExpression, new NameExpr(param1.getId().getName()), new NameExpr(param2.getId().getName())));

        final BinaryExpr equalityCheck = new BinaryExpr(getExpression(method1, annotation1, param1), getExpression(method2, annotation2, param2),
            BinaryExpr.Operator.equals);

        final MethodCallExpr preconditionsCheckArgument = new MethodCallExpr(new NameExpr(Preconditions.class.getSimpleName()), "checkArgument",
            Arrays.asList(equalityCheck, stringFormat));
        statements.add(new ExpressionStmt(preconditionsCheckArgument));
    }

    private static int getPositionValue(AnnotationExpr annotation) {
        final IntegerLiteralExpr position = getAnnotationValue(annotation, CompareTo.class, "position");
        return Integer.parseInt(position.getValue());
    }

    private static Expression getExpression(MethodDeclaration method, AnnotationExpr compareTo, Parameter parameter) {
        final StringLiteralExpr valueExpression = getAnnotationValue(compareTo, CompareTo.class, "method");
        final String value = valueExpression.getValue();
        if (value.isEmpty()) {
            return new NameExpr(parameter.getId().getName());
        }
        final ClassOrInterfaceDeclaration classDeclaration = (ClassOrInterfaceDeclaration) method.getParentNode();
        MethodDeclaration referencedMethod = null;
        for (BodyDeclaration declaration : classDeclaration.getMembers()) {
            if (!(declaration instanceof MethodDeclaration)) {
                continue;
            }
            final MethodDeclaration methodDeclaration = (MethodDeclaration) declaration;
            if (methodDeclaration.getName().equals(value)) {
                referencedMethod = methodDeclaration;
            }
        }
        if (referencedMethod == null) {
            throw new IllegalStateException(
                String.format("Unable to find method %s on type %s", value, classDeclaration.getName()));
        }
        return new MethodCallExpr(new NameExpr(parameter.getId().getName()), value);
    }

    private Map<MethodDeclaration, AnnotationExpr> findCompareAnnotations(String name, ClassOrInterfaceDeclaration event) {
        Map<MethodDeclaration, AnnotationExpr> annotations = new HashMap<>();
        for (MethodWrapper<Type, MethodDeclaration> wrapper : new JavaParserClassWrapper(workingSource, event).getMethods()) {
            final MethodDeclaration method = wrapper.getActualMethod();
            final AnnotationExpr annotation = getAnnotation(method, CompareTo.class);
            if (annotation != null) {
                final StringLiteralExpr value = getAnnotationValue(annotation, CompareTo.class, "value");
                if (value.getValue().equals(name)) {
                    annotations.put(method, annotation);
                }
            }
        }
        return annotations;
    }

    private static boolean shouldAdd(Property<Type, MethodDeclaration> property) {
        if (!property.isMostSpecificType()) {
            return false;
        }
        final AnnotationExpr annotation = getAnnotation(property.getAccessor(), PropertySettings.class);
        if (annotation == null) {
            return true;
        }
        final BooleanLiteralExpr value = getAnnotationValue(annotation, PropertySettings.class, "requiredParameter");
        return value == null || value.getValue();
    }

    @SuppressWarnings("unchecked")
    private static <T extends LiteralExpr> T getAnnotationValue(AnnotationExpr annotation, Class<? extends Annotation> annotationClass, String key) {
        if (annotation instanceof SingleMemberAnnotationExpr) {
            return (T) ((SingleMemberAnnotationExpr) annotation).getMemberValue();
        } else if (annotation instanceof NormalAnnotationExpr) {
            for (MemberValuePair pair : ((NormalAnnotationExpr) annotation).getPairs()) {
                if (key.equals(pair.getName())) {
                    return (T) pair.getValue();
                }
            }
        }
        try {
            final Object value = annotationClass.getMethod(key).getDefaultValue();
            if (value == null) {
                throw new RuntimeException("No default value for " + key);
            }
            final LiteralExpr literal;
            if (value instanceof Integer) {
                literal = new IntegerLiteralExpr(value.toString());
            } else if (value instanceof Boolean) {
                literal = new BooleanLiteralExpr((Boolean) value);
            } else {
                literal = new StringLiteralExpr(value.toString());
            }
            return (T) literal;
        } catch (Exception exception) {
            throw new IllegalArgumentException("No value for " + key + " in " + annotationClass.getCanonicalName());
        }
    }

    private static AnnotationExpr getAnnotation(AnnotableNode holder, Class<? extends Annotation> annotation) {
        for (AnnotationExpr expression : holder.getAnnotations()) {
            if (annotation.getSimpleName().equals(expression.getName().toStringWithoutComments())) {
                return expression;
            }
        }
        return null;
    }

    private static String camelCaseToWords(String camelCase) {
        final StringBuilder words = new StringBuilder();
        int nextUppercase = camelCase.length();
        for (int i = 1; i < nextUppercase; i++) {
            if (Character.isUpperCase(camelCase.charAt(i))) {
                nextUppercase = i;
                break;
            }
        }
        words.append(Character.toLowerCase(camelCase.charAt(0)));
        words.append(camelCase.substring(1, nextUppercase));
        if (nextUppercase < camelCase.length()) {
            words.append(' ');
            words.append(camelCaseToWords(camelCase.substring(nextUppercase)));
        }
        return words.toString();
    }

}
