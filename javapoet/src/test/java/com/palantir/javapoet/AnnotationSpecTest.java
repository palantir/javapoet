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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.testing.compile.CompilationRule;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.lang.model.element.TypeElement;
import org.junit.Rule;
import org.junit.Test;

@SuppressWarnings("ClassCanBeStatic")
public final class AnnotationSpecTest {

    @Retention(RetentionPolicy.RUNTIME)
    public @interface AnnotationA {}

    @Inherited
    @Retention(RetentionPolicy.RUNTIME)
    public @interface AnnotationB {}

    @Retention(RetentionPolicy.RUNTIME)
    public @interface AnnotationC {
        String value();
    }

    public enum Breakfast {
        WAFFLES,
        PANCAKES;

        @Override
        public String toString() {
            return name() + " with cherries!";
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface HasDefaultsAnnotation {

        byte a() default 5;

        short b() default 6;

        int c() default 7;

        long d() default 12345678910L;

        float e() default 9.0f;

        double f() default 10.0;

        char[] g() default {0, 0xCAFE, 'z', '€', 'ℕ', '"', '\'', '\t', '\n'};

        boolean h() default true;

        Breakfast i() default Breakfast.WAFFLES;

        AnnotationA j() default @AnnotationA;

        String k() default "maple";

        Class<? extends Annotation> l() default AnnotationB.class;

        int[] m() default {1, 2, 3};

        Breakfast[] n() default {Breakfast.WAFFLES, Breakfast.PANCAKES};

        Breakfast o();

        int p();

        AnnotationC q() default @AnnotationC("foo");

        Class<? extends Number>[] r() default {Byte.class, Short.class, Integer.class, Long.class};
    }

    @HasDefaultsAnnotation(
            o = Breakfast.PANCAKES,
            p = 1701,
            f = 11.1,
            m = {9, 8, 1},
            l = Override.class,
            j = @AnnotationA,
            q = @AnnotationC("bar"),
            r = {Float.class, Double.class})
    public class IsAnnotated {
        // empty
    }

    @Rule
    public final CompilationRule compilation = new CompilationRule();

    @Test
    public void equalsAndHashCode() {
        AnnotationSpec a = AnnotationSpec.builder(AnnotationC.class).build();
        AnnotationSpec b = AnnotationSpec.builder(AnnotationC.class).build();
        assertThat(a.equals(b)).isTrue();
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        a = AnnotationSpec.builder(AnnotationC.class)
                .addMember("value", "$S", "123")
                .build();
        b = AnnotationSpec.builder(AnnotationC.class)
                .addMember("value", "$S", "123")
                .build();
        assertThat(a.equals(b)).isTrue();
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    public void defaultAnnotation() {
        String name = IsAnnotated.class.getCanonicalName();
        TypeElement element = compilation.getElements().getTypeElement(name);
        AnnotationSpec annotation =
                AnnotationSpec.get(element.getAnnotationMirrors().get(0));

        TypeSpec taco = TypeSpec.classBuilder("Taco").addAnnotation(annotation).build();
        assertThat(toString(taco))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import com.palantir.javapoet.AnnotationSpecTest;
                        import java.lang.Double;
                        import java.lang.Float;
                        import java.lang.Override;

                        @AnnotationSpecTest.HasDefaultsAnnotation(
                            o = AnnotationSpecTest.Breakfast.PANCAKES,
                            p = 1701,
                            f = 11.1,
                            m = {
                                9,
                                8,
                                1
                            },
                            l = Override.class,
                            j = @AnnotationSpecTest.AnnotationA,
                            q = @AnnotationSpecTest.AnnotationC("bar"),
                            r = {
                                Float.class,
                                Double.class
                            }
                        )
                        class Taco {
                        }
                        """);
    }

    @Test
    public void defaultAnnotationWithImport() {
        String name = IsAnnotated.class.getCanonicalName();
        TypeElement element = compilation.getElements().getTypeElement(name);
        AnnotationSpec annotation =
                AnnotationSpec.get(element.getAnnotationMirrors().get(0));
        TypeSpec.Builder typeBuilder = TypeSpec.classBuilder(IsAnnotated.class.getSimpleName());
        typeBuilder.addAnnotation(annotation);
        JavaFile file =
                JavaFile.builder("com.palantir.javapoet", typeBuilder.build()).build();
        assertThat(file.toString())
                .isEqualTo(
                        """
                        package com.palantir.javapoet;

                        import java.lang.Double;
                        import java.lang.Float;
                        import java.lang.Override;

                        @AnnotationSpecTest.HasDefaultsAnnotation(
                            o = AnnotationSpecTest.Breakfast.PANCAKES,
                            p = 1701,
                            f = 11.1,
                            m = {
                                9,
                                8,
                                1
                            },
                            l = Override.class,
                            j = @AnnotationSpecTest.AnnotationA,
                            q = @AnnotationSpecTest.AnnotationC("bar"),
                            r = {
                                Float.class,
                                Double.class
                            }
                        )
                        class IsAnnotated {
                        }
                        """);
    }

    @Test
    public void emptyArray() {
        AnnotationSpec.Builder builder = AnnotationSpec.builder(HasDefaultsAnnotation.class);
        builder.addMember("n", "$L", "{}");
        assertThat(builder.build().toString())
                .isEqualTo("@com.palantir.javapoet.AnnotationSpecTest.HasDefaultsAnnotation(" + "n = {}" + ")");
        builder.addMember("m", "$L", "{}");
        assertThat(builder.build().toString())
                .isEqualTo("@com.palantir.javapoet.AnnotationSpecTest.HasDefaultsAnnotation(" + "n = {}, m = {}" + ")");
    }

    @Test
    public void dynamicArrayOfEnumConstants() {
        AnnotationSpec.Builder builder = AnnotationSpec.builder(HasDefaultsAnnotation.class);
        builder.addMember("n", "$T.$L", Breakfast.class, Breakfast.PANCAKES.name());
        assertThat(builder.build().toString())
                .isEqualTo("@com.palantir.javapoet.AnnotationSpecTest.HasDefaultsAnnotation("
                        + "n = com.palantir.javapoet.AnnotationSpecTest.Breakfast.PANCAKES"
                        + ")");

        // builder = AnnotationSpec.builder(HasDefaultsAnnotation.class);
        builder.addMember("n", "$T.$L", Breakfast.class, Breakfast.WAFFLES.name());
        builder.addMember("n", "$T.$L", Breakfast.class, Breakfast.PANCAKES.name());
        assertThat(builder.build().toString())
                .isEqualTo("@com.palantir.javapoet.AnnotationSpecTest.HasDefaultsAnnotation("
                        + "n = {"
                        + "com.palantir.javapoet.AnnotationSpecTest.Breakfast.PANCAKES"
                        + ", com.palantir.javapoet.AnnotationSpecTest.Breakfast.WAFFLES"
                        + ", com.palantir.javapoet.AnnotationSpecTest.Breakfast.PANCAKES"
                        + "})");

        builder = builder.build().toBuilder(); // idempotent
        assertThat(builder.build().toString())
                .isEqualTo("@com.palantir.javapoet.AnnotationSpecTest.HasDefaultsAnnotation("
                        + "n = {"
                        + "com.palantir.javapoet.AnnotationSpecTest.Breakfast.PANCAKES"
                        + ", com.palantir.javapoet.AnnotationSpecTest.Breakfast.WAFFLES"
                        + ", com.palantir.javapoet.AnnotationSpecTest.Breakfast.PANCAKES"
                        + "})");

        builder.addMember("n", "$T.$L", Breakfast.class, Breakfast.WAFFLES.name());
        assertThat(builder.build().toString())
                .isEqualTo("@com.palantir.javapoet.AnnotationSpecTest.HasDefaultsAnnotation("
                        + "n = {"
                        + "com.palantir.javapoet.AnnotationSpecTest.Breakfast.PANCAKES"
                        + ", com.palantir.javapoet.AnnotationSpecTest.Breakfast.WAFFLES"
                        + ", com.palantir.javapoet.AnnotationSpecTest.Breakfast.PANCAKES"
                        + ", com.palantir.javapoet.AnnotationSpecTest.Breakfast.WAFFLES"
                        + "})");
    }

    @Test
    public void defaultAnnotationToBuilder() {
        String name = IsAnnotated.class.getCanonicalName();
        TypeElement element = compilation.getElements().getTypeElement(name);
        AnnotationSpec.Builder builder =
                AnnotationSpec.get(element.getAnnotationMirrors().get(0)).toBuilder();
        builder.addMember("m", "$L", 123);
        assertThat(builder.build().toString())
                .isEqualTo("@com.palantir.javapoet.AnnotationSpecTest.HasDefaultsAnnotation("
                        + "o = com.palantir.javapoet.AnnotationSpecTest.Breakfast.PANCAKES"
                        + ", p = 1701"
                        + ", f = 11.1"
                        + ", m = {9, 8, 1, 123}"
                        + ", l = java.lang.Override.class"
                        + ", j = @com.palantir.javapoet.AnnotationSpecTest.AnnotationA"
                        + ", q = @com.palantir.javapoet.AnnotationSpecTest.AnnotationC(\"bar\")"
                        + ", r = {java.lang.Float.class, java.lang.Double.class}"
                        + ")");
    }

    @Test
    public void reflectAnnotation() {
        HasDefaultsAnnotation annotation = IsAnnotated.class.getAnnotation(HasDefaultsAnnotation.class);
        AnnotationSpec spec = AnnotationSpec.get(annotation);
        TypeSpec taco = TypeSpec.classBuilder("Taco").addAnnotation(spec).build();
        assertThat(toString(taco))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import com.palantir.javapoet.AnnotationSpecTest;
                        import java.lang.Double;
                        import java.lang.Float;
                        import java.lang.Override;

                        @AnnotationSpecTest.HasDefaultsAnnotation(
                            f = 11.1,
                            l = Override.class,
                            m = {
                                9,
                                8,
                                1
                            },
                            o = AnnotationSpecTest.Breakfast.PANCAKES,
                            p = 1701,
                            q = @AnnotationSpecTest.AnnotationC("bar"),
                            r = {
                                Float.class,
                                Double.class
                            }
                        )
                        class Taco {
                        }
                        """);
    }

    @Test
    public void reflectAnnotationWithDefaults() {
        HasDefaultsAnnotation annotation = IsAnnotated.class.getAnnotation(HasDefaultsAnnotation.class);
        AnnotationSpec spec = AnnotationSpec.get(annotation, true);
        TypeSpec taco = TypeSpec.classBuilder("Taco").addAnnotation(spec).build();
        assertThat(toString(taco))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import com.palantir.javapoet.AnnotationSpecTest;
                        import java.lang.Double;
                        import java.lang.Float;
                        import java.lang.Override;

                        @AnnotationSpecTest.HasDefaultsAnnotation(
                            a = 5,
                            b = 6,
                            c = 7,
                            d = 12345678910L,
                            e = 9.0f,
                            f = 11.1,
                            g = {
                                '\\u0000',
                                '쫾',
                                'z',
                                '€',
                                'ℕ',
                                '"',
                                '\\'',
                                '\\t',
                                '\\n'
                            },
                            h = true,
                            i = AnnotationSpecTest.Breakfast.WAFFLES,
                            j = @AnnotationSpecTest.AnnotationA,
                            k = "maple",
                            l = Override.class,
                            m = {
                                9,
                                8,
                                1
                            },
                            n = {
                                AnnotationSpecTest.Breakfast.WAFFLES,
                                AnnotationSpecTest.Breakfast.PANCAKES
                            },
                            o = AnnotationSpecTest.Breakfast.PANCAKES,
                            p = 1701,
                            q = @AnnotationSpecTest.AnnotationC("bar"),
                            r = {
                                Float.class,
                                Double.class
                            }
                        )
                        class Taco {
                        }
                        """);
    }

    @Test
    public void disallowsNullMemberName() {
        AnnotationSpec.Builder builder = AnnotationSpec.builder(HasDefaultsAnnotation.class);
        assertThatThrownBy(() -> builder.addMember(null, "$L", ""))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("name == null");
    }

    @Test
    public void requiresValidMemberName() {
        AnnotationSpec.Builder builder =
                AnnotationSpec.builder(HasDefaultsAnnotation.class).addMember("@", "$L", "");
        assertThatThrownBy(() -> builder.build().toString())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("not a valid name: @");
    }

    private String toString(TypeSpec typeSpec) {
        return JavaFile.builder("com.palantir.tacos", typeSpec).build().toString();
    }
}
