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

import static com.palantir.javapoet.TestUtil.findFirst;
import static javax.lang.model.util.ElementFilter.fieldsIn;
import static javax.lang.model.util.ElementFilter.methodsIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.testing.compile.CompilationRule;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@SuppressWarnings({"ClassCanBeStatic", "StrictUnusedVariable"})
public class ParameterSpecTest {
    @Rule
    public final CompilationRule compilation = new CompilationRule();

    private Elements elements;

    @Before
    public void before() {
        elements = compilation.getElements();
    }

    private TypeElement getElement(Class<?> clazz) {
        return elements.getTypeElement(clazz.getCanonicalName());
    }

    /**
     * Performs round-trip check that {@code parameterSpec.toBuilder().build()} is identical to the
     * original {@code parameterSpec}.
     */
    private static void checkToBuilderRoundtrip(ParameterSpec parameterSpec) {
        String originalMethodString = toMethodString(parameterSpec);
        String originalToString = parameterSpec.toString();
        int originalHashCode = parameterSpec.hashCode();

        ParameterSpec roundtripParameterSpec = parameterSpec.toBuilder().build();
        assertThat(toMethodString(roundtripParameterSpec)).isEqualTo(originalMethodString);
        assertThat(roundtripParameterSpec.toString()).isEqualTo(originalToString);
        assertThat(roundtripParameterSpec.hashCode()).isEqualTo(originalHashCode);
        assertThat(roundtripParameterSpec).isEqualTo(parameterSpec);
    }

    private static String toMethodString(ParameterSpec parameterSpec) {
        // Add parameter to MethodSpec because at least parameter Javadoc is only emitted when part of a method
        return MethodSpec.methodBuilder("test").addParameter(parameterSpec).build().toString();
    }

    @Test
    public void equalsAndHashCode() {
        ParameterSpec a = ParameterSpec.builder(int.class, "foo").build();
        ParameterSpec b = ParameterSpec.builder(int.class, "foo").build();
        assertThat(a.equals(b)).isTrue();
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).isEqualTo(b.toString());
        a = ParameterSpec.builder(int.class, "i").addModifiers(Modifier.STATIC).build();
        b = ParameterSpec.builder(int.class, "i").addModifiers(Modifier.STATIC).build();
        assertThat(a.equals(b)).isTrue();
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).isEqualTo(b.toString());

        checkToBuilderRoundtrip(a);
    }

    @Test
    public void receiverParameterInstanceMethod() {
        ParameterSpec parameterSpec = ParameterSpec.builder(int.class, "this").build();
        assertThat(parameterSpec.name()).isEqualTo("this");
        checkToBuilderRoundtrip(parameterSpec);
    }

    @Test
    public void receiverParameterNestedClass() {
        ParameterSpec parameterSpec = ParameterSpec.builder(int.class, "Foo.this").build();
        assertThat(parameterSpec.name()).isEqualTo("Foo.this");
        checkToBuilderRoundtrip(parameterSpec);
    }

    @Test
    public void keywordName() {
        assertThatThrownBy(() -> ParameterSpec.builder(int.class, "super"))
                .isInstanceOf(Exception.class)
                .hasMessage("not a valid name: super");
    }

    @Test
    public void nullAnnotationsAddition() {
        assertThatThrownBy(() -> ParameterSpec.builder(int.class, "foo").addAnnotations(null))
                .isInstanceOf(Exception.class)
                .hasMessage("annotationSpecs == null");
    }

    final class VariableElementFieldClass {
        String name;
    }

    @Test
    public void fieldVariableElement() {
        TypeElement classElement = getElement(VariableElementFieldClass.class);
        List<VariableElement> methods = fieldsIn(elements.getAllMembers(classElement));
        VariableElement element = findFirst(methods, "name");

        assertThatThrownBy(() -> ParameterSpec.get(element))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("element is not a parameter");
    }

    final class VariableElementParameterClass {
        public void foo(@Nullable final String bar) {}
    }

    @Test
    public void parameterVariableElement() {
        TypeElement classElement = getElement(VariableElementParameterClass.class);
        List<ExecutableElement> methods = methodsIn(elements.getAllMembers(classElement));
        ExecutableElement element = findFirst(methods, "foo");
        VariableElement parameterElement = element.getParameters().get(0);

        ParameterSpec parameterSpec = ParameterSpec.get(parameterElement);
        assertThat(parameterSpec.toString()).isEqualTo("final java.lang.String bar");
        checkToBuilderRoundtrip(parameterSpec);
    }

    @Test
    public void addNonFinalModifier() {
        List<Modifier> modifiers = new ArrayList<>();
        modifiers.add(Modifier.FINAL);
        modifiers.add(Modifier.PUBLIC);

        assertThatThrownBy(() -> ParameterSpec.builder(int.class, "foo").addModifiers(modifiers))
                .isInstanceOf(Exception.class)
                .hasMessage("unexpected parameter modifier: public");
    }

    @Test
    public void parameterJavadoc() {
        ParameterSpec parameterSpec = ParameterSpec.builder(int.class, "i").addJavadoc("My param").build();
        assertThat(toMethodString(parameterSpec))
                .isEqualTo(
                        """
                        /**
                         * @param i My param
                         */
                        void test(int i) {
                        }
                        """);
        checkToBuilderRoundtrip(parameterSpec);
    }
}
