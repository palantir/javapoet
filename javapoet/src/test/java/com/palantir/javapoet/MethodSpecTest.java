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

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.testing.compile.CompilationSubject.assertThat;
import static com.google.testing.compile.Compiler.javac;
import static com.palantir.javapoet.TestUtil.findFirst;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationRule;
import java.io.Closeable;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@SuppressWarnings({"ClassCanBeStatic", "TypeParameterUnusedInFormals", "StrictUnusedVariable", "UnusedMethod"})
public final class MethodSpecTest {
    @Rule
    public final CompilationRule compilation = new CompilationRule();

    private Elements elements;
    private Types types;

    @Before
    public void before() {
        elements = compilation.getElements();
        types = compilation.getTypes();
    }

    private TypeElement getElement(Class<?> clazz) {
        return elements.getTypeElement(clazz.getCanonicalName());
    }

    /**
     * Method spec created by a test method; used by {@link #checkToBuilderRoundtrip()}.
     *
     * <p>{@code null} if a test method does not create a (valid) method spec, or if no round-trip check
     * should be performed on it.
     */
    private MethodSpec methodSpec = null;

    /**
     * Performs round-trip check that {@code methodSpec.toBuilder().build()} is identical to the
     * original {@code methodSpec}.
     */
    @After
    public void checkToBuilderRoundtrip() {
        if (methodSpec == null) {
            return;
        }

        String originalToString = methodSpec.toString();
        int originalHashCode = methodSpec.hashCode();

        MethodSpec roundtripMethodSpec = methodSpec.toBuilder().build();
        assertThat(roundtripMethodSpec.toString()).isEqualTo(originalToString);
        assertThat(roundtripMethodSpec.hashCode()).isEqualTo(originalHashCode);
        assertThat(roundtripMethodSpec).isEqualTo(methodSpec);
    }

    @Test
    public void nullAnnotationsAddition() {
        assertThatThrownBy(() -> MethodSpec.methodBuilder("doSomething").addAnnotations(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("annotationSpecs == null");
    }

    @Test
    public void nullTypeVariablesAddition() {
        assertThatThrownBy(() -> MethodSpec.methodBuilder("doSomething").addTypeVariables(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("typeVariables == null");
    }

    @Test
    public void nullParametersAddition() {
        assertThatThrownBy(() -> MethodSpec.methodBuilder("doSomething").addParameters(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("parameterSpecs == null");
    }

    @Test
    public void nullExceptionsAddition() {
        assertThatThrownBy(() -> MethodSpec.methodBuilder("doSomething").addExceptions(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("exceptions == null");
    }

    @Target(ElementType.PARAMETER)
    @interface Nullable {}

    abstract static class Everything {
        @Deprecated
        protected abstract <T extends Runnable & Closeable> Runnable everything(
                @Nullable String thing, List<? extends T> things) throws IOException, SecurityException;
    }

    abstract static class Generics {
        <T, R, V extends Throwable> T run(R param) throws V {
            return null;
        }
    }

    abstract static class HasAnnotation {
        @Override
        public abstract String toString();
    }

    interface Throws<R extends RuntimeException> {
        void fail() throws R;
    }

    interface ExtendsOthers extends Callable<Integer>, Comparable<ExtendsOthers>, Throws<IllegalStateException> {}

    interface ExtendsIterableWithDefaultMethods extends Iterable<Object> {}

    final class FinalClass {
        void method() {}
    }

    abstract static class InvalidOverrideMethods {
        final void finalMethod() {}

        private void privateMethod() {}

        static void staticMethod() {}
    }

    @Test
    public void overrideEverything() {
        TypeElement classElement = getElement(Everything.class);
        ExecutableElement methodElement = getOnlyElement(methodsIn(classElement.getEnclosedElements()));
        methodSpec = MethodSpec.overriding(methodElement).build();
        assertThat(methodSpec.toString())
                .isEqualTo(
                        """
                        @java.lang.Override
                        protected <T extends java.lang.Runnable & java.io.Closeable> java.lang.Runnable everything(
                            java.lang.String thing, java.util.List<? extends T> things) throws java.io.IOException,
                            java.lang.SecurityException {
                        }
                        """);
    }

    @Test
    public void overrideGenerics() {
        TypeElement classElement = getElement(Generics.class);
        ExecutableElement methodElement = getOnlyElement(methodsIn(classElement.getEnclosedElements()));
        methodSpec =
                MethodSpec.overriding(methodElement).addStatement("return null").build();
        assertThat(methodSpec.toString())
                .isEqualTo(
                        """
                        @java.lang.Override
                        <T, R, V extends java.lang.Throwable> T run(R param) throws V {
                          return null;
                        }
                        """);
    }

    @Test
    public void overrideDoesNotCopyOverrideAnnotation() {
        TypeElement classElement = getElement(HasAnnotation.class);
        ExecutableElement exec = getOnlyElement(methodsIn(classElement.getEnclosedElements()));
        methodSpec = MethodSpec.overriding(exec).build();
        assertThat(methodSpec.toString())
                .isEqualTo(
                        """
                        @java.lang.Override
                        public java.lang.String toString() {
                        }
                        """);
    }

    @Test
    public void overrideDoesNotCopyDefaultModifier() {
        TypeElement classElement = getElement(ExtendsIterableWithDefaultMethods.class);
        DeclaredType classType = (DeclaredType) classElement.asType();
        List<ExecutableElement> methods = methodsIn(elements.getAllMembers(classElement));
        ExecutableElement exec = findFirst(methods, "spliterator");
        methodSpec = MethodSpec.overriding(exec, classType, types).build();
        assertThat(methodSpec.toString())
                .isEqualTo(
                        """
                        @java.lang.Override
                        public java.util.Spliterator<java.lang.Object> spliterator() {
                        }
                        """);
    }

    @Test
    public void overrideExtendsOthersWorksWithActualTypeParameters() {
        TypeElement classElement = getElement(ExtendsOthers.class);
        DeclaredType classType = (DeclaredType) classElement.asType();
        List<ExecutableElement> methods = methodsIn(elements.getAllMembers(classElement));
        ExecutableElement exec = findFirst(methods, "call");
        methodSpec = MethodSpec.overriding(exec, classType, types).build();
        assertThat(methodSpec.toString())
                .isEqualTo(
                        """
                        @java.lang.Override
                        public java.lang.Integer call() throws java.lang.Exception {
                        }
                        """);
        exec = findFirst(methods, "compareTo");
        methodSpec = MethodSpec.overriding(exec, classType, types).build();
        assertThat(methodSpec.toString())
                .isEqualTo(
                        """
                        @java.lang.Override
                        public int compareTo(com.palantir.javapoet.MethodSpecTest.ExtendsOthers arg0) {
                        }
                        """);
        exec = findFirst(methods, "fail");
        methodSpec = MethodSpec.overriding(exec, classType, types).build();
        assertThat(methodSpec.toString())
                .isEqualTo(
                        """
                        @java.lang.Override
                        public void fail() throws java.lang.IllegalStateException {
                        }
                        """);
    }

    @Test
    public void overrideFinalClassMethod() {
        TypeElement classElement = getElement(FinalClass.class);
        List<ExecutableElement> methods = methodsIn(elements.getAllMembers(classElement));
        assertThatThrownBy(() -> MethodSpec.overriding(findFirst(methods, "method")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot override method on final class com.palantir.javapoet.MethodSpecTest.FinalClass");
    }

    @Test
    public void overrideInvalidModifiers() {
        TypeElement classElement = getElement(InvalidOverrideMethods.class);
        List<ExecutableElement> methods = methodsIn(elements.getAllMembers(classElement));
        assertThatThrownBy(() -> MethodSpec.overriding(findFirst(methods, "finalMethod")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cannot override method with modifiers: [final]");
        assertThatThrownBy(() -> MethodSpec.overriding(findFirst(methods, "privateMethod")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cannot override method with modifiers: [private]");
        assertThatThrownBy(() -> MethodSpec.overriding(findFirst(methods, "staticMethod")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cannot override method with modifiers: [static]");
    }

    abstract static class AbstractClassWithPrivateAnnotation {

        private @interface PrivateAnnotation {}

        abstract void foo(@PrivateAnnotation String bar);
    }

    @Test
    public void overrideDoesNotCopyParameterAnnotations() {
        TypeElement abstractTypeElement = getElement(AbstractClassWithPrivateAnnotation.class);
        ExecutableElement fooElement = ElementFilter.methodsIn(abstractTypeElement.getEnclosedElements())
                .get(0);
        ClassName implClassName = ClassName.get("com.palantir.javapoet", "Impl");
        TypeSpec type = TypeSpec.classBuilder(implClassName)
                .superclass(abstractTypeElement.asType())
                .addMethod(MethodSpec.overriding(fooElement).build())
                .build();
        JavaFileObject jfo =
                JavaFile.builder("com.palantir.javapoet", type).build().toJavaFileObject();
        Compilation compilation = javac().compile(jfo);
        assertThat(compilation).succeeded();
    }

    @Test
    public void equalsAndHashCode() {
        MethodSpec a = MethodSpec.constructorBuilder().build();
        MethodSpec b = MethodSpec.constructorBuilder().build();
        assertThat(a.equals(b)).isTrue();
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        a = MethodSpec.methodBuilder("taco").build();
        b = MethodSpec.methodBuilder("taco").build();
        assertThat(a.equals(b)).isTrue();
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        TypeElement classElement = getElement(Everything.class);
        ExecutableElement methodElement = getOnlyElement(methodsIn(classElement.getEnclosedElements()));
        a = MethodSpec.overriding(methodElement).build();
        b = MethodSpec.overriding(methodElement).build();
        assertThat(a.equals(b)).isTrue();
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    public void withoutParameterJavaDoc() {
        methodSpec = MethodSpec.methodBuilder("getTaco")
                .addModifiers(Modifier.PRIVATE)
                .addParameter(TypeName.DOUBLE, "money")
                .addJavadoc("Gets the best Taco\n")
                .build();
        assertThat(methodSpec.toString())
                .isEqualTo(
                        """
                        /**
                         * Gets the best Taco
                         */
                        private void getTaco(double money) {
                        }
                        """);
    }

    @Test
    public void withParameterJavaDoc() {
        methodSpec = MethodSpec.methodBuilder("getTaco")
                .addParameter(ParameterSpec.builder(TypeName.DOUBLE, "money")
                        .addJavadoc("the amount required to buy the taco.\n")
                        .build())
                .addParameter(ParameterSpec.builder(TypeName.INT, "count")
                        .addJavadoc("the number of Tacos to buy.\n")
                        .build())
                .addJavadoc("Gets the best Taco money can buy.\n")
                .build();
        assertThat(methodSpec.toString())
                .isEqualTo(
                        """
                        /**
                         * Gets the best Taco money can buy.
                         *
                         * @param money the amount required to buy the taco.
                         * @param count the number of Tacos to buy.
                         */
                        void getTaco(double money, int count) {
                        }
                        """);
    }

    @Test
    public void withParameterJavaDocAndWithoutMethodJavadoc() {
        methodSpec = MethodSpec.methodBuilder("getTaco")
                .addParameter(ParameterSpec.builder(TypeName.DOUBLE, "money")
                        .addJavadoc("the amount required to buy the taco.\n")
                        .build())
                .addParameter(ParameterSpec.builder(TypeName.INT, "count")
                        .addJavadoc("the number of Tacos to buy.\n")
                        .build())
                .build();
        assertThat(methodSpec.toString())
                .isEqualTo(
                        """
                        /**
                         * @param money the amount required to buy the taco.
                         * @param count the number of Tacos to buy.
                         */
                        void getTaco(double money, int count) {
                        }
                        """);
    }

    @Test
    public void duplicateExceptionsIgnored() {
        ClassName ioException = ClassName.get(IOException.class);
        ClassName timeoutException = ClassName.get(TimeoutException.class);
        methodSpec = MethodSpec.methodBuilder("duplicateExceptions")
                .addException(ioException)
                .addException(timeoutException)
                .addException(timeoutException)
                .addException(ioException)
                .build();
        assertThat(methodSpec.exceptions()).containsExactlyElementsOf(Arrays.asList(ioException, timeoutException));
        assertThat(methodSpec.toBuilder().addException(ioException).build().exceptions())
                .containsExactlyElementsOf(Arrays.asList(ioException, timeoutException));
    }

    @Test
    public void nullIsNotAValidMethodName() {
        assertThatThrownBy(() -> MethodSpec.methodBuilder(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("name == null");
    }

    @Test
    public void addModifiersVarargsShouldNotBeNull() {
        assertThatThrownBy(() -> MethodSpec.methodBuilder("taco").addModifiers((Modifier[]) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("modifiers == null");
    }

    @Test
    public void modifyMethodName() {
        methodSpec = MethodSpec.methodBuilder("initialMethod").build().toBuilder()
                .setName("revisedMethod")
                .build();

        assertThat(methodSpec.toString())
                .isEqualTo(
                        """
                        void revisedMethod() {
                        }
                        """);
    }

    @Test
    public void ensureTrailingNewline() {
        methodSpec = MethodSpec.methodBuilder("method")
                .addCode("codeWithNoNewline();")
                .build();

        assertThat(methodSpec.toString())
                .isEqualTo(
                        """
                        void method() {
                          codeWithNoNewline();
                        }
                        """);
    }

    /** Ensures that we don't add a duplicate newline if one is already present. */
    @Test
    public void ensureTrailingNewlineWithExistingNewline() {
        methodSpec = MethodSpec.methodBuilder("method")
                .addCode("codeWithNoNewline();\n") // Have a newline already, so ensure we're not adding one
                .build();

        assertThat(methodSpec.toString())
                .isEqualTo(
                        """
                        void method() {
                          codeWithNoNewline();
                        }
                        """);
    }

    @Test
    public void controlFlowWithNamedCodeBlocks() {
        Map<String, Object> m = new HashMap<>();
        m.put("field", "valueField");
        m.put("threshold", "5");

        methodSpec = MethodSpec.methodBuilder("method")
                .beginControlFlow(named("if ($field:N > $threshold:L)", m))
                .nextControlFlow(named("else if ($field:N == $threshold:L)", m))
                .endControlFlow()
                .build();

        assertThat(methodSpec.toString())
                .isEqualTo(
                        """
                        void method() {
                          if (valueField > 5) {
                          } else if (valueField == 5) {
                          }
                        }
                        """);
    }

    @Test
    public void doWhileWithNamedCodeBlocks() {
        Map<String, Object> m = new HashMap<>();
        m.put("field", "valueField");
        m.put("threshold", "5");

        methodSpec = MethodSpec.methodBuilder("method")
                .beginControlFlow("do")
                .addStatement(named("$field:N--", m))
                .endControlFlow(named("while ($field:N > $threshold:L)", m))
                .build();

        assertThat(methodSpec.toString())
                .isEqualTo(
                        """
                        void method() {
                          do {
                            valueField--;
                          } while (valueField > 5);
                        }
                        """);
    }

    private static CodeBlock named(String format, Map<String, ?> args) {
        return CodeBlock.builder().addNamed(format, args).build();
    }
}
