/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.palantir.javapoet;

import static com.palantir.javapoet.Util.checkArgument;
import static com.palantir.javapoet.Util.checkNotNull;
import static com.palantir.javapoet.Util.nonNullList;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;

/** A generated parameter declaration. */
public final class ParameterSpec {
    private final String name;
    private final List<AnnotationSpec> annotations;
    private final Set<Modifier> modifiers;
    private final TypeName type;
    private final CodeBlock javadoc;

    private ParameterSpec(Builder builder) {
        this.name = checkNotNull(builder.name, "name == null");
        this.annotations = Util.immutableList(builder.annotations);
        this.modifiers = Util.immutableSet(builder.modifiers);
        this.type = checkNotNull(builder.type, "type == null");
        this.javadoc = builder.javadoc.build();
    }

    public String name() {
        return name;
    }

    public List<AnnotationSpec> annotations() {
        return annotations;
    }

    public Set<Modifier> modifiers() {
        return modifiers;
    }

    public TypeName type() {
        return type;
    }

    public CodeBlock javadoc() {
        return javadoc;
    }

    void emit(CodeWriter codeWriter, boolean varargs) throws IOException {
        codeWriter.emitAnnotations(annotations, true);
        codeWriter.emitModifiers(modifiers);
        if (varargs) {
            TypeName.asArray(type).emit(codeWriter, true);
        } else {
            type.emit(codeWriter);
        }
        codeWriter.emit(" $L", name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (getClass() != o.getClass()) {
            return false;
        }
        return toString().equals(o.toString());
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder();
        try {
            CodeWriter codeWriter = new CodeWriter(out);
            emit(codeWriter, false);
            return out.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static ParameterSpec get(VariableElement element) {
        checkArgument(element.getKind().equals(ElementKind.PARAMETER), "element is not a parameter");

        TypeName type = TypeName.get(element.asType());
        String name = element.getSimpleName().toString();
        // Copying parameter annotations can be incorrect so we're deliberately not including them.
        // See https://github.com/square/javapoet/issues/482.
        return ParameterSpec.builder(type, name)
                .addModifiers(element.getModifiers())
                .build();
    }

    static List<ParameterSpec> parametersOf(ExecutableElement method) {
        List<ParameterSpec> result = new ArrayList<>();
        for (VariableElement parameter : method.getParameters()) {
            result.add(ParameterSpec.get(parameter));
        }
        return result;
    }

    private static boolean isValidParameterName(String name) {
        checkNotNull(name, "name == null");
        // Allow "this" for explicit receiver parameters
        // See https://docs.oracle.com/javase/specs/jls/se8/html/jls-8.html#jls-8.4.1.
        if (name.endsWith(".this")) {
            return SourceVersion.isIdentifier(name.substring(0, name.length() - ".this".length()));
        }
        return name.equals("this") || SourceVersion.isName(name);
    }

    public static Builder builder(TypeName type, String name, Modifier... modifiers) {
        checkNotNull(type, "type == null");
        checkArgument(isValidParameterName(name), "not a valid name: %s", name);
        return new Builder(type, name).addModifiers(modifiers);
    }

    public static Builder builder(Type type, String name, Modifier... modifiers) {
        return builder(TypeName.get(type), name, modifiers);
    }

    public Builder toBuilder() {
        return toBuilder(type, name);
    }

    Builder toBuilder(TypeName type, String name) {
        Builder builder = new Builder(type, name);
        builder.annotations.addAll(annotations);
        builder.modifiers.addAll(modifiers);
        return builder;
    }

    public static final class Builder {
        private final TypeName type;
        private final String name;
        private final CodeBlock.Builder javadoc = CodeBlock.builder();

        private final List<AnnotationSpec> annotations = new ArrayList<>();
        private final List<Modifier> modifiers = new ArrayList<>();

        private Builder(TypeName type, String name) {
            this.type = type;
            this.name = name;
        }

        public Builder addJavadoc(String format, Object... args) {
            javadoc.add(format, args);
            return this;
        }

        public Builder addJavadoc(CodeBlock block) {
            javadoc.add(block);
            return this;
        }

        public Builder addAnnotations(Iterable<AnnotationSpec> annotationSpecs) {
            checkNotNull(annotationSpecs, "annotationSpecs == null");
            for (AnnotationSpec annotationSpec : annotationSpecs) {
                addAnnotation(annotationSpec);
            }
            return this;
        }

        public Builder addAnnotation(AnnotationSpec annotationSpec) {
            checkNotNull(annotationSpec, "annotationSpec == null");
            this.annotations.add(annotationSpec);
            return this;
        }

        public Builder addAnnotation(ClassName annotation) {
            return addAnnotation(AnnotationSpec.builder(annotation).build());
        }

        public Builder addAnnotation(Class<?> annotation) {
            return addAnnotation(ClassName.get(annotation));
        }

        public Builder addModifiers(Modifier... modifiers) {
            return addModifiers(nonNullList(modifiers, "modifiers"));
        }

        public Builder addModifiers(Iterable<Modifier> modifiers) {
            checkNotNull(modifiers, "modifiers == null");
            for (Modifier modifier : modifiers) {
                checkNotNull(modifier, "modifiers contain null");
                if (!modifier.equals(Modifier.FINAL)) {
                    throw new IllegalStateException("unexpected parameter modifier: " + modifier);
                }
                this.modifiers.add(modifier);
            }
            return this;
        }

        public ParameterSpec build() {
            return new ParameterSpec(this);
        }
    }
}
