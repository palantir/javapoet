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

import com.google.common.collect.ImmutableMap;
import com.google.testing.compile.CompilationRule;
import java.io.IOException;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EventListener;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public final class TypeSpecTest {
    private final String tacosPackage = "com.palantir.tacos";
    private static final String donutsPackage = "com.palantir.donuts";

    @Rule
    public final CompilationRule compilation = new CompilationRule();

    private TypeElement getElement(Class<?> clazz) {
        return compilation.getElements().getTypeElement(clazz.getCanonicalName());
    }

    /**
     * Performs round-trip check that {@code typeSpec.toBuilder().build()} is identical to the
     * original {@code typeSpec}.
     */
    private static void checkToBuilderRoundtrip(TypeSpec typeSpec) {
        String originalToString = typeSpec.toString();
        int originalHashCode = typeSpec.hashCode();

        TypeSpec roundtripTypeSpec = typeSpec.toBuilder().build();
        assertThat(roundtripTypeSpec.toString()).isEqualTo(originalToString);
        assertThat(roundtripTypeSpec.hashCode()).isEqualTo(originalHashCode);
        assertThat(roundtripTypeSpec).isEqualTo(typeSpec);
    }


    @Test
    public void basic() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addMethod(MethodSpec.methodBuilder("toString")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                        .returns(String.class)
                        .addCode("return $S;\n", "taco")
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.Override;
                        import java.lang.String;

                        class Taco {
                          @Override
                          public final String toString() {
                            return "taco";
                          }
                        }
                        """);
        assertThat(typeSpec.hashCode()).isEqualTo(472949424); // update expected number if source changes
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void interestingTypes() {
        TypeName listOfAny =
                ParameterizedTypeName.get(ClassName.get(List.class), WildcardTypeName.subtypeOf(Object.class));
        TypeName listOfExtends =
                ParameterizedTypeName.get(ClassName.get(List.class), WildcardTypeName.subtypeOf(Serializable.class));
        TypeName listOfSuper =
                ParameterizedTypeName.get(ClassName.get(List.class), WildcardTypeName.supertypeOf(String.class));
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addField(listOfAny, "extendsObject")
                .addField(listOfExtends, "extendsSerializable")
                .addField(listOfSuper, "superString")
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.io.Serializable;
                        import java.lang.String;
                        import java.util.List;

                        class Taco {
                          List<?> extendsObject;

                          List<? extends Serializable> extendsSerializable;

                          List<? super String> superString;
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void anonymousInnerClass() {
        ClassName foo = ClassName.get(tacosPackage, "Foo");
        ClassName bar = ClassName.get(tacosPackage, "Bar");
        ClassName thingThang = ClassName.get(tacosPackage, "Thing", "Thang");
        TypeName thingThangOfFooBar = ParameterizedTypeName.get(thingThang, foo, bar);
        ClassName thung = ClassName.get(tacosPackage, "Thung");
        ClassName simpleThung = ClassName.get(tacosPackage, "SimpleThung");
        TypeName thungOfSuperBar = ParameterizedTypeName.get(thung, WildcardTypeName.supertypeOf(bar));
        TypeName thungOfSuperFoo = ParameterizedTypeName.get(thung, WildcardTypeName.supertypeOf(foo));
        TypeName simpleThungOfBar = ParameterizedTypeName.get(simpleThung, bar);

        ParameterSpec thungParameter = ParameterSpec.builder(thungOfSuperFoo, "thung")
                .addModifiers(Modifier.FINAL)
                .build();
        TypeSpec aSimpleThung = TypeSpec.anonymousClassBuilder(CodeBlock.of("$N", thungParameter))
                .superclass(simpleThungOfBar)
                .addMethod(MethodSpec.methodBuilder("doSomething")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(bar, "bar")
                        .addCode("/* code snippets */\n")
                        .build())
                .build();
        TypeSpec aThingThang = TypeSpec.anonymousClassBuilder("")
                .superclass(thingThangOfFooBar)
                .addMethod(MethodSpec.methodBuilder("call")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(thungOfSuperBar)
                        .addParameter(thungParameter)
                        .addCode("return $L;\n", aSimpleThung)
                        .build())
                .build();
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addField(FieldSpec.builder(thingThangOfFooBar, "NAME")
                        .addModifiers(Modifier.STATIC, Modifier.FINAL, Modifier.FINAL)
                        .initializer("$L", aThingThang)
                        .build())
                .build();

        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.Override;

                        class Taco {
                          static final Thing.Thang<Foo, Bar> NAME = new Thing.Thang<Foo, Bar>() {
                            @Override
                            public Thung<? super Bar> call(final Thung<? super Foo> thung) {
                              return new SimpleThung<Bar>(thung) {
                                @Override
                                public void doSomething(Bar bar) {
                                  /* code snippets */
                                }
                              };
                            }
                          };
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void annotatedParameters() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Foo")
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(long.class, "id")
                        .addParameter(ParameterSpec.builder(String.class, "one")
                                .addAnnotation(ClassName.get(tacosPackage, "Ping"))
                                .build())
                        .addParameter(ParameterSpec.builder(String.class, "two")
                                .addAnnotation(ClassName.get(tacosPackage, "Ping"))
                                .build())
                        .addParameter(ParameterSpec.builder(String.class, "three")
                                .addAnnotation(AnnotationSpec.builder(ClassName.get(tacosPackage, "Pong"))
                                        .addMember("value", "$S", "pong")
                                        .build())
                                .build())
                        .addParameter(ParameterSpec.builder(String.class, "four")
                                .addAnnotation(ClassName.get(tacosPackage, "Ping"))
                                .build())
                        .addCode("/* code snippets */\n")
                        .build())
                .build();

        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.String;

                        class Foo {
                          public Foo(long id, @Ping String one, @Ping String two, @Pong("pong") String three,
                              @Ping String four) {
                            /* code snippets */
                          }
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    /**
     * We had a bug where annotations were preventing us from doing the right thing when resolving
     * imports. https://github.com/square/javapoet/issues/422
     */
    @Test
    public void annotationsAndJavaLangTypes() {
        ClassName freeRange = ClassName.get("javax.annotation", "FreeRange");
        TypeSpec typeSpec = TypeSpec.classBuilder("EthicalTaco")
                .addField(
                        ClassName.get(String.class)
                                .annotated(AnnotationSpec.builder(freeRange).build()),
                        "meat")
                .build();

        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.String;
                        import javax.annotation.FreeRange;

                        class EthicalTaco {
                          @FreeRange String meat;
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void retrofitStyleInterface() {
        ClassName observable = ClassName.get(tacosPackage, "Observable");
        ClassName fooBar = ClassName.get(tacosPackage, "FooBar");
        ClassName thing = ClassName.get(tacosPackage, "Thing");
        ClassName things = ClassName.get(tacosPackage, "Things");
        ClassName map = ClassName.get("java.util", "Map");
        ClassName string = ClassName.get("java.lang", "String");
        ClassName headers = ClassName.get(tacosPackage, "Headers");
        ClassName post = ClassName.get(tacosPackage, "POST");
        ClassName body = ClassName.get(tacosPackage, "Body");
        ClassName queryMap = ClassName.get(tacosPackage, "QueryMap");
        ClassName header = ClassName.get(tacosPackage, "Header");
        TypeSpec typeSpec = TypeSpec.interfaceBuilder("Service")
                .addMethod(MethodSpec.methodBuilder("fooBar")
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .addAnnotation(AnnotationSpec.builder(headers)
                                .addMember("value", "$S", "Accept: application/json")
                                .addMember("value", "$S", "User-Agent: foobar")
                                .build())
                        .addAnnotation(AnnotationSpec.builder(post)
                                .addMember("value", "$S", "/foo/bar")
                                .build())
                        .returns(ParameterizedTypeName.get(observable, fooBar))
                        .addParameter(ParameterSpec.builder(ParameterizedTypeName.get(things, thing), "things")
                                .addAnnotation(body)
                                .build())
                        .addParameter(ParameterSpec.builder(ParameterizedTypeName.get(map, string, string), "query")
                                .addAnnotation(AnnotationSpec.builder(queryMap)
                                        .addMember("encodeValues", "false")
                                        .build())
                                .build())
                        .addParameter(ParameterSpec.builder(string, "authorization")
                                .addAnnotation(AnnotationSpec.builder(header)
                                        .addMember("value", "$S", "Authorization")
                                        .build())
                                .build())
                        .build())
                .build();

        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.String;
                        import java.util.Map;

                        interface Service {
                          @Headers({
                              "Accept: application/json",
                              "User-Agent: foobar"
                          })
                          @POST("/foo/bar")
                          Observable<FooBar> fooBar(@Body Things<Thing> things,
                              @QueryMap(encodeValues = false) Map<String, String> query,
                              @Header("Authorization") String authorization);
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void annotatedField() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addField(FieldSpec.builder(String.class, "thing", Modifier.PRIVATE, Modifier.FINAL)
                        .addAnnotation(AnnotationSpec.builder(ClassName.get(tacosPackage, "JsonAdapter"))
                                .addMember("value", "$T.class", ClassName.get(tacosPackage, "Foo"))
                                .build())
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.String;

                        class Taco {
                          @JsonAdapter(Foo.class)
                          private final String thing;
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void annotatedClass() {
        ClassName someType = ClassName.get(tacosPackage, "SomeType");
        TypeSpec typeSpec = TypeSpec.classBuilder("Foo")
                .addAnnotation(AnnotationSpec.builder(ClassName.get(tacosPackage, "Something"))
                        .addMember("hi", "$T.$N", someType, "FIELD")
                        .addMember("hey", "$L", 12)
                        .addMember("hello", "$S", "goodbye")
                        .build())
                .addModifiers(Modifier.PUBLIC)
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        @Something(
                            hi = SomeType.FIELD,
                            hey = 12,
                            hello = "goodbye"
                        )
                        public class Foo {
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void addAnnotationDisallowsNull() {
        assertThatThrownBy(() -> TypeSpec.classBuilder("Foo").addAnnotation((AnnotationSpec) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("annotationSpec == null");
        assertThatThrownBy(() -> TypeSpec.classBuilder("Foo").addAnnotation((ClassName) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("type == null");
        assertThatThrownBy(() -> TypeSpec.classBuilder("Foo").addAnnotation((Class<?>) null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("clazz == null");
    }

    @Test
    public void enumWithSubclassing() {
        TypeSpec typeSpec = TypeSpec.enumBuilder("Roshambo")
                .addModifiers(Modifier.PUBLIC)
                .addEnumConstant(
                        "ROCK",
                        TypeSpec.anonymousClassBuilder("")
                                .addJavadoc("Avalanche!\n")
                                .build())
                .addEnumConstant(
                        "PAPER",
                        TypeSpec.anonymousClassBuilder("$S", "flat")
                                .addMethod(MethodSpec.methodBuilder("toString")
                                        .addAnnotation(Override.class)
                                        .addModifiers(Modifier.PUBLIC)
                                        .returns(String.class)
                                        .addCode("return $S;\n", "paper airplane!")
                                        .build())
                                .build())
                .addEnumConstant(
                        "SCISSORS",
                        TypeSpec.anonymousClassBuilder("$S", "peace sign").build())
                .addField(String.class, "handPosition", Modifier.PRIVATE, Modifier.FINAL)
                .addMethod(MethodSpec.constructorBuilder()
                        .addParameter(String.class, "handPosition")
                        .addCode("this.handPosition = handPosition;\n")
                        .build())
                .addMethod(MethodSpec.constructorBuilder()
                        .addCode("this($S);\n", "fist")
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.Override;
                        import java.lang.String;

                        public enum Roshambo {
                          /**
                           * Avalanche!
                           */
                          ROCK,

                          PAPER("flat") {
                            @Override
                            public String toString() {
                              return "paper airplane!";
                            }
                          },

                          SCISSORS("peace sign");

                          private final String handPosition;

                          Roshambo(String handPosition) {
                            this.handPosition = handPosition;
                          }

                          Roshambo() {
                            this("fist");
                          }
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    /** https://github.com/square/javapoet/issues/193 */
    @Test
    public void enumsMayDefineAbstractMethods() {
        TypeSpec typeSpec = TypeSpec.enumBuilder("Tortilla")
                .addModifiers(Modifier.PUBLIC)
                .addEnumConstant(
                        "CORN",
                        TypeSpec.anonymousClassBuilder("")
                                .addMethod(MethodSpec.methodBuilder("fold")
                                        .addAnnotation(Override.class)
                                        .addModifiers(Modifier.PUBLIC)
                                        .build())
                                .build())
                .addMethod(MethodSpec.methodBuilder("fold")
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.Override;

                        public enum Tortilla {
                          CORN {
                            @Override
                            public void fold() {
                            }
                          };

                          public abstract void fold();
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void noEnumConstants() {
        TypeSpec typeSpec = TypeSpec.enumBuilder("Roshambo")
                .addField(String.class, "NO_ENUM", Modifier.STATIC)
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.String;

                        enum Roshambo {
                          ;
                          static String NO_ENUM;
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void onlyEnumsMayHaveEnumConstants() {
        assertThatThrownBy(() -> TypeSpec.classBuilder("Roshambo")
                        .addEnumConstant("ROCK")
                        .build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void enumWithMembersButNoConstructorCall() {
        TypeSpec typeSpec = TypeSpec.enumBuilder("Roshambo")
                .addEnumConstant(
                        "SPOCK",
                        TypeSpec.anonymousClassBuilder("")
                                .addMethod(MethodSpec.methodBuilder("toString")
                                        .addAnnotation(Override.class)
                                        .addModifiers(Modifier.PUBLIC)
                                        .returns(String.class)
                                        .addCode("return $S;\n", "west side")
                                        .build())
                                .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.Override;
                        import java.lang.String;

                        enum Roshambo {
                          SPOCK {
                            @Override
                            public String toString() {
                              return "west side";
                            }
                          }
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    /** https://github.com/square/javapoet/issues/253 */
    @Test
    public void enumWithAnnotatedValues() {
        TypeSpec typeSpec = TypeSpec.enumBuilder("Roshambo")
                .addModifiers(Modifier.PUBLIC)
                .addEnumConstant(
                        "ROCK",
                        TypeSpec.anonymousClassBuilder("")
                                .addAnnotation(Deprecated.class)
                                .build())
                .addEnumConstant("PAPER")
                .addEnumConstant("SCISSORS")
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.Deprecated;

                        public enum Roshambo {
                          @Deprecated
                          ROCK,

                          PAPER,

                          SCISSORS
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void methodThrows() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addModifiers(Modifier.ABSTRACT)
                .addMethod(MethodSpec.methodBuilder("throwOne")
                        .addException(IOException.class)
                        .build())
                .addMethod(MethodSpec.methodBuilder("throwTwo")
                        .addException(IOException.class)
                        .addException(ClassName.get(tacosPackage, "SourCreamException"))
                        .build())
                .addMethod(MethodSpec.methodBuilder("abstractThrow")
                        .addModifiers(Modifier.ABSTRACT)
                        .addException(IOException.class)
                        .build())
                .addMethod(MethodSpec.methodBuilder("nativeThrow")
                        .addModifiers(Modifier.NATIVE)
                        .addException(IOException.class)
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.io.IOException;

                        abstract class Taco {
                          void throwOne() throws IOException {
                          }

                          void throwTwo() throws IOException, SourCreamException {
                          }

                          abstract void abstractThrow() throws IOException;

                          native void nativeThrow() throws IOException;
                        }
                        """);
    }

    @Test
    public void typeVariables() {
        TypeVariableName t = TypeVariableName.get("T");
        TypeVariableName p = TypeVariableName.get("P", Number.class);
        ClassName location = ClassName.get(tacosPackage, "Location");
        TypeSpec typeSpec = TypeSpec.classBuilder("Location")
                .addTypeVariable(t)
                .addTypeVariable(p)
                .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Comparable.class), p))
                .addField(t, "label")
                .addField(p, "x")
                .addField(p, "y")
                .addMethod(MethodSpec.methodBuilder("compareTo")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(int.class)
                        .addParameter(p, "p")
                        .addCode("return 0;\n")
                        .build())
                .addMethod(MethodSpec.methodBuilder("of")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .addTypeVariable(t)
                        .addTypeVariable(p)
                        .returns(ParameterizedTypeName.get(location, t, p))
                        .addParameter(t, "label")
                        .addParameter(p, "x")
                        .addParameter(p, "y")
                        .addCode("throw new $T($S);\n", UnsupportedOperationException.class, "TODO")
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.Comparable;
                        import java.lang.Number;
                        import java.lang.Override;
                        import java.lang.UnsupportedOperationException;

                        class Location<T, P extends Number> implements Comparable<P> {
                          T label;

                          P x;

                          P y;

                          @Override
                          public int compareTo(P p) {
                            return 0;
                          }

                          public static <T, P extends Number> Location<T, P> of(T label, P x, P y) {
                            throw new UnsupportedOperationException("TODO");
                          }
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void typeVariableWithBounds() {
        AnnotationSpec a =
                AnnotationSpec.builder(ClassName.get("com.palantir.tacos", "A")).build();
        TypeVariableName p = TypeVariableName.get("P", Number.class);
        TypeVariableName q =
                (TypeVariableName) TypeVariableName.get("Q", Number.class).annotated(a);
        TypeSpec typeSpec = TypeSpec.classBuilder("Location")
                .addTypeVariable(p.withBounds(Comparable.class))
                .addTypeVariable(q.withBounds(Comparable.class))
                .addField(p, "x")
                .addField(q, "y")
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.Comparable;
                        import java.lang.Number;

                        class Location<P extends Number & Comparable, @A Q extends Number & Comparable> {
                          P x;

                          @A Q y;
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void classSealed() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco").addModifiers(Modifier.SEALED).build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        sealed class Taco {
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void classNonSealed() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco").addModifiers(Modifier.NON_SEALED).build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        non-sealed class Taco {
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void classImplementsExtendsPermits() {
        ClassName taco = ClassName.get(tacosPackage, "Taco");
        ClassName food = ClassName.get("com.palantir.tacos", "Food");
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addModifiers(Modifier.ABSTRACT, Modifier.SEALED)
                .superclass(ParameterizedTypeName.get(ClassName.get(AbstractSet.class), food))
                .addSuperinterface(Serializable.class)
                .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Comparable.class), taco))
                .addPermittedSubclass(ClassName.bestGuess("com.palantir.tacos.BeefTaco"))
                .addPermittedSubclass(ClassName.bestGuess("com.palantir.tacos.ChickenTaco"))
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.io.Serializable;
                        import java.lang.Comparable;
                        import java.util.AbstractSet;

                        abstract sealed class Taco extends AbstractSet<Food> implements Serializable, Comparable<Taco> \
                        permits BeefTaco, ChickenTaco {
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void classImplementsNestedClass() {
        ClassName outer = ClassName.get(tacosPackage, "Outer");
        ClassName inner = outer.nestedClass("Inner");
        ClassName callable = ClassName.get(Callable.class);
        TypeSpec typeSpec = TypeSpec.classBuilder("Outer")
                .superclass(ParameterizedTypeName.get(callable, inner))
                .addType(TypeSpec.classBuilder("Inner")
                        .addModifiers(Modifier.STATIC)
                        .build())
                .build();

        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.util.concurrent.Callable;

                        class Outer extends Callable<Outer.Inner> {
                          static class Inner {
                          }
                        }
                        """);
    }

    @Test
    public void enumImplements() {
        TypeSpec typeSpec = TypeSpec.enumBuilder("Food")
                .addSuperinterface(Serializable.class)
                .addSuperinterface(Cloneable.class)
                .addEnumConstant("LEAN_GROUND_BEEF")
                .addEnumConstant("SHREDDED_CHEESE")
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.io.Serializable;
                        import java.lang.Cloneable;

                        enum Food implements Serializable, Cloneable {
                          LEAN_GROUND_BEEF,

                          SHREDDED_CHEESE
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void interfaceSealed() {
        TypeSpec typeSpec = TypeSpec.interfaceBuilder("Taco").addModifiers(Modifier.SEALED).build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        sealed interface Taco {
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void interfaceNonSealed() {
        TypeSpec typeSpec = TypeSpec.interfaceBuilder("Taco")
                .addModifiers(Modifier.NON_SEALED)
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        non-sealed interface Taco {
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void interfaceExtendsPermits() {
        ClassName taco = ClassName.get(tacosPackage, "Taco");
        TypeSpec typeSpec = TypeSpec.interfaceBuilder("Taco")
                .addModifiers(Modifier.SEALED)
                .addSuperinterface(Serializable.class)
                .addSuperinterface(ParameterizedTypeName.get(ClassName.get(Comparable.class), taco))
                .addPermittedSubclass(ClassName.bestGuess("com.palantir.tacos.BeefTaco"))
                .addPermittedSubclass(ClassName.bestGuess("com.palantir.tacos.ChickenTaco"))
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.io.Serializable;
                        import java.lang.Comparable;

                        sealed interface Taco extends Serializable, Comparable<Taco> permits BeefTaco, ChickenTaco {
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void recordOneField() {
        TypeSpec typeSpec = TypeSpec.recordBuilder("Taco")
                .recordConstructor(MethodSpec.constructorBuilder()
                        .addParameter(
                                ParameterSpec.builder(String.class, "name").build())
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.String;

                        record Taco(String name) {
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void recordTwoFields() {
        TypeSpec typeSpec = TypeSpec.recordBuilder("Taco")
                .recordConstructor(MethodSpec.constructorBuilder()
                        .addParameter(
                                ParameterSpec.builder(String.class, "name").build())
                        .addParameter(
                                ParameterSpec.builder(Integer.class, "size").build())
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.Integer;
                        import java.lang.String;

                        record Taco(String name, Integer size) {
                        }
                        """);
    }

    @Test
    public void recordWithVarArgs() {
        TypeSpec typeSpec = TypeSpec.recordBuilder("Taco")
                .recordConstructor(MethodSpec.constructorBuilder()
                        .addParameter(
                                ParameterSpec.builder(String.class, "name").build())
                        .addParameter(ParameterSpec.builder(ArrayTypeName.of(ClassName.get(String.class)), "names")
                                .build())
                        .varargs()
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.String;

                        record Taco(String name, String... names) {
                        }
                        """);
    }

    @Test
    public void recordWithJavadoc() {
        TypeSpec typeSpec = TypeSpec.recordBuilder("Taco")
                .recordConstructor(MethodSpec.constructorBuilder()
                        .addParameter(ParameterSpec.builder(String.class, "id")
                                .addJavadoc("Id of the taco.")
                                .build())
                        .build())
                .addJavadoc("A taco class that stores the id of a taco.")
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.String;

                        /**
                         * A taco class that stores the id of a taco.
                         * @param id Id of the taco.
                         */
                        record Taco(String id) {
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void recordWithAnnotationOnParam() {
        TypeSpec typeSpec = TypeSpec.recordBuilder("Taco")
                .recordConstructor(MethodSpec.constructorBuilder()
                        .addParameter(ParameterSpec.builder(String.class, "id")
                                .addAnnotation(Deprecated.class)
                                .build())
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.Deprecated;
                        import java.lang.String;

                        record Taco(@Deprecated String id) {
                        }
                        """);
    }

    @Test
    public void recordNoField() {
        TypeSpec typeSpec = TypeSpec.recordBuilder("Taco").build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        record Taco() {
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void nestedClasses() {
        ClassName taco = ClassName.get(tacosPackage, "Combo", "Taco");
        ClassName topping = ClassName.get(tacosPackage, "Combo", "Taco", "Topping");
        ClassName chips = ClassName.get(tacosPackage, "Combo", "Chips");
        ClassName sauce = ClassName.get(tacosPackage, "Combo", "Sauce");
        TypeSpec typeSpec = TypeSpec.classBuilder("Combo")
                .addField(taco, "taco")
                .addField(chips, "chips")
                .addType(TypeSpec.classBuilder(taco.simpleName())
                        .addModifiers(Modifier.STATIC)
                        .addField(ParameterizedTypeName.get(ClassName.get(List.class), topping), "toppings")
                        .addField(sauce, "sauce")
                        .addType(TypeSpec.enumBuilder(topping.simpleName())
                                .addEnumConstant("SHREDDED_CHEESE")
                                .addEnumConstant("LEAN_GROUND_BEEF")
                                .build())
                        .build())
                .addType(TypeSpec.classBuilder(chips.simpleName())
                        .addModifiers(Modifier.STATIC)
                        .addField(topping, "topping")
                        .addField(sauce, "dippingSauce")
                        .build())
                .addType(TypeSpec.enumBuilder(sauce.simpleName())
                        .addEnumConstant("SOUR_CREAM")
                        .addEnumConstant("SALSA")
                        .addEnumConstant("QUESO")
                        .addEnumConstant("MILD")
                        .addEnumConstant("FIRE")
                        .build())
                .build();

        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.util.List;

                        class Combo {
                          Taco taco;

                          Chips chips;

                          static class Taco {
                            List<Topping> toppings;

                            Sauce sauce;

                            enum Topping {
                              SHREDDED_CHEESE,

                              LEAN_GROUND_BEEF
                            }
                          }

                          static class Chips {
                            Taco.Topping topping;

                            Sauce dippingSauce;
                          }

                          enum Sauce {
                            SOUR_CREAM,

                            SALSA,

                            QUESO,

                            MILD,

                            FIRE
                          }
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void annotation() {
        TypeSpec typeSpec = TypeSpec.annotationBuilder("MyAnnotation")
                .addModifiers(Modifier.PUBLIC)
                .addMethod(MethodSpec.methodBuilder("test")
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .defaultValue("$L", 0)
                        .returns(int.class)
                        .build())
                .build();

        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        public @interface MyAnnotation {
                          int test() default 0;
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void innerAnnotationInAnnotationDeclaration() {
        TypeSpec typeSpec = TypeSpec.annotationBuilder("Bar")
                .addMethod(MethodSpec.methodBuilder("value")
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .defaultValue("@$T", Deprecated.class)
                        .returns(Deprecated.class)
                        .build())
                .build();

        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.Deprecated;

                        @interface Bar {
                          Deprecated value() default @Deprecated;
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void annotationWithFields() {
        FieldSpec field = FieldSpec.builder(int.class, "FOO")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$L", 101)
                .build();

        TypeSpec typeSpec = TypeSpec.annotationBuilder("Anno").addField(field).build();

        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        @interface Anno {
                          int FOO = 101;
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void classCannotHaveDefaultValueForMethod() {
        assertThatThrownBy(() -> TypeSpec.classBuilder("Tacos")
                        .addMethod(MethodSpec.methodBuilder("test")
                                .addModifiers(Modifier.PUBLIC)
                                .defaultValue("0")
                                .returns(int.class)
                                .build())
                        .build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void classCannotHaveDefaultMethods() {
        assertThatThrownBy(() -> TypeSpec.classBuilder("Tacos")
                        .addMethod(MethodSpec.methodBuilder("test")
                                .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                                .returns(int.class)
                                .addCode(CodeBlock.builder()
                                        .addStatement("return 0")
                                        .build())
                                .build())
                        .build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void interfaceStaticMethods() {
        TypeSpec typeSpec = TypeSpec.interfaceBuilder("Tacos")
                .addMethod(MethodSpec.methodBuilder("test")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .returns(int.class)
                        .addCode(CodeBlock.builder().addStatement("return 0").build())
                        .build())
                .build();

        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        interface Tacos {
                          static int test() {
                            return 0;
                          }
                        }
                        """);
    }

    @Test
    public void interfaceDefaultMethods() {
        TypeSpec typeSpec = TypeSpec.interfaceBuilder("Tacos")
                .addMethod(MethodSpec.methodBuilder("test")
                        .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                        .returns(int.class)
                        .addCode(CodeBlock.builder().addStatement("return 0").build())
                        .build())
                .build();

        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        interface Tacos {
                          default int test() {
                            return 0;
                          }
                        }
                        """);
    }

    @Test
    public void invalidInterfacePrivateMethods() {
        assertThatThrownBy(() -> TypeSpec.interfaceBuilder("Tacos")
                        .addMethod(MethodSpec.methodBuilder("test")
                                .addModifiers(Modifier.PRIVATE, Modifier.DEFAULT)
                                .returns(int.class)
                                .addCode(CodeBlock.builder()
                                        .addStatement("return 0")
                                        .build())
                                .build())
                        .build())
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> TypeSpec.interfaceBuilder("Tacos")
                        .addMethod(MethodSpec.methodBuilder("test")
                                .addModifiers(Modifier.PRIVATE, Modifier.ABSTRACT)
                                .returns(int.class)
                                .build())
                        .build())
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> TypeSpec.interfaceBuilder("Tacos")
                        .addMethod(MethodSpec.methodBuilder("test")
                                .addModifiers(Modifier.PRIVATE, Modifier.PUBLIC)
                                .returns(int.class)
                                .addCode(CodeBlock.builder()
                                        .addStatement("return 0")
                                        .build())
                                .build())
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void interfacePrivateMethods() {
        TypeSpec typeSpec = TypeSpec.interfaceBuilder("Tacos")
                .addMethod(MethodSpec.methodBuilder("test")
                        .addModifiers(Modifier.PRIVATE)
                        .returns(int.class)
                        .addCode(CodeBlock.builder().addStatement("return 0").build())
                        .build())
                .build();

        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        interface Tacos {
                          private int test() {
                            return 0;
                          }
                        }
                        """);

        typeSpec = TypeSpec.interfaceBuilder("Tacos")
                .addMethod(MethodSpec.methodBuilder("test")
                        .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                        .returns(int.class)
                        .addCode(CodeBlock.builder().addStatement("return 0").build())
                        .build())
                .build();

        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        interface Tacos {
                          private static int test() {
                            return 0;
                          }
                        }
                        """);
    }

    @Test
    public void referencedAndDeclaredSimpleNamesConflict() {
        FieldSpec internalTop = FieldSpec.builder(ClassName.get(tacosPackage, "Top"), "internalTop")
                .build();
        FieldSpec internalBottom = FieldSpec.builder(
                        ClassName.get(tacosPackage, "Top", "Middle", "Bottom"), "internalBottom")
                .build();
        FieldSpec externalTop = FieldSpec.builder(ClassName.get(donutsPackage, "Top"), "externalTop")
                .build();
        FieldSpec externalBottom = FieldSpec.builder(ClassName.get(donutsPackage, "Bottom"), "externalBottom")
                .build();
        TypeSpec typeSpec = TypeSpec.classBuilder("Top")
                .addField(internalTop)
                .addField(internalBottom)
                .addField(externalTop)
                .addField(externalBottom)
                .addType(TypeSpec.classBuilder("Middle")
                        .addField(internalTop)
                        .addField(internalBottom)
                        .addField(externalTop)
                        .addField(externalBottom)
                        .addType(TypeSpec.classBuilder("Bottom")
                                .addField(internalTop)
                                .addField(internalBottom)
                                .addField(externalTop)
                                .addField(externalBottom)
                                .build())
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import com.palantir.donuts.Bottom;

                        class Top {
                          Top internalTop;

                          Middle.Bottom internalBottom;

                          com.palantir.donuts.Top externalTop;

                          Bottom externalBottom;

                          class Middle {
                            Top internalTop;

                            Bottom internalBottom;

                            com.palantir.donuts.Top externalTop;

                            com.palantir.donuts.Bottom externalBottom;

                            class Bottom {
                              Top internalTop;

                              Bottom internalBottom;

                              com.palantir.donuts.Top externalTop;

                              com.palantir.donuts.Bottom externalBottom;
                            }
                          }
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void simpleNamesConflictInThisAndOtherPackage() {
        FieldSpec internalOther = FieldSpec.builder(ClassName.get(tacosPackage, "Other"), "internalOther")
                .build();
        FieldSpec externalOther = FieldSpec.builder(ClassName.get(donutsPackage, "Other"), "externalOther")
                .build();
        TypeSpec typeSpec = TypeSpec.classBuilder("Gen")
                .addField(internalOther)
                .addField(externalOther)
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        class Gen {
                          Other internalOther;

                          com.palantir.donuts.Other externalOther;
                        }
                        """);
    }

    @Test
    public void simpleNameConflictsWithTypeVariable() {
        ClassName inPackage = ClassName.get("com.palantir.tacos", "InPackage");
        ClassName otherType = ClassName.get("com.other", "OtherType");
        ClassName methodInPackage = ClassName.get("com.palantir.tacos", "MethodInPackage");
        ClassName methodOtherType = ClassName.get("com.other", "MethodOtherType");
        TypeSpec typeSpec = TypeSpec.classBuilder("Gen")
                .addTypeVariable(TypeVariableName.get("InPackage"))
                .addTypeVariable(TypeVariableName.get("OtherType"))
                .addField(FieldSpec.builder(inPackage, "inPackage").build())
                .addField(FieldSpec.builder(otherType, "otherType").build())
                .addMethod(MethodSpec.methodBuilder("withTypeVariables")
                        .addTypeVariable(TypeVariableName.get("MethodInPackage"))
                        .addTypeVariable(TypeVariableName.get("MethodOtherType"))
                        .addStatement("$T inPackage = null", methodInPackage)
                        .addStatement("$T otherType = null", methodOtherType)
                        .build())
                .addMethod(MethodSpec.methodBuilder("withoutTypeVariables")
                        .addStatement("$T inPackage = null", methodInPackage)
                        .addStatement("$T otherType = null", methodOtherType)
                        .build())
                .addMethod(MethodSpec.methodBuilder("againWithTypeVariables")
                        .addTypeVariable(TypeVariableName.get("MethodInPackage"))
                        .addTypeVariable(TypeVariableName.get("MethodOtherType"))
                        .addStatement("$T inPackage = null", methodInPackage)
                        .addStatement("$T otherType = null", methodOtherType)
                        .build())
                // https://github.com/square/javapoet/pull/657#discussion_r205514292
                .addMethod(MethodSpec.methodBuilder("masksEnclosingTypeVariable")
                        .addTypeVariable(TypeVariableName.get("InPackage"))
                        .build())
                .addMethod(MethodSpec.methodBuilder("hasSimpleNameThatWasPreviouslyMasked")
                        .addStatement("$T inPackage = null", inPackage)
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import com.other.MethodOtherType;

                        class Gen<InPackage, OtherType> {
                          com.palantir.tacos.InPackage inPackage;

                          com.other.OtherType otherType;

                          <MethodInPackage, MethodOtherType> void withTypeVariables() {
                            com.palantir.tacos.MethodInPackage inPackage = null;
                            com.other.MethodOtherType otherType = null;
                          }

                          void withoutTypeVariables() {
                            MethodInPackage inPackage = null;
                            MethodOtherType otherType = null;
                          }

                          <MethodInPackage, MethodOtherType> void againWithTypeVariables() {
                            com.palantir.tacos.MethodInPackage inPackage = null;
                            com.other.MethodOtherType otherType = null;
                          }

                          <InPackage> void masksEnclosingTypeVariable() {
                          }

                          void hasSimpleNameThatWasPreviouslyMasked() {
                            com.palantir.tacos.InPackage inPackage = null;
                          }
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void originatingElementsIncludesThoseOfNestedTypes() {
        Element outerElement = Mockito.mock(Element.class);
        Element innerElement = Mockito.mock(Element.class);
        TypeSpec outer = TypeSpec.classBuilder("Outer")
                .addOriginatingElement(outerElement)
                .addType(TypeSpec.classBuilder("Inner")
                        .addOriginatingElement(innerElement)
                        .build())
                .build();
        assertThat(outer.originatingElements()).containsExactly(outerElement, innerElement);
        checkToBuilderRoundtrip(outer);
    }

    @Test
    public void intersectionType() {
        TypeVariableName typeVariable = TypeVariableName.get("T", Comparator.class, Serializable.class);
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addMethod(MethodSpec.methodBuilder("getComparator")
                        .addTypeVariable(typeVariable)
                        .returns(typeVariable)
                        .addCode("return null;\n")
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.io.Serializable;
                        import java.util.Comparator;

                        class Taco {
                          <T extends Comparator & Serializable> T getComparator() {
                            return null;
                          }
                        }
                        """);
    }

    @Test
    public void arrayType() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco").addField(int[].class, "ints").build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        class Taco {
                          int[] ints;
                        }
                        """);
    }

    @Test
    public void javadoc() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addJavadoc("A hard or soft tortilla, loosely folded and filled with whatever\n")
                .addJavadoc("{@link $T random} tex-mex stuff we could find in the pantry\n", Random.class)
                .addJavadoc(CodeBlock.of("and some {@link $T} cheese.\n", String.class))
                .addField(FieldSpec.builder(boolean.class, "soft")
                        .addJavadoc("True for a soft flour tortilla; false for a crunchy corn tortilla.\n")
                        .build())
                .addMethod(MethodSpec.methodBuilder("refold")
                        .addJavadoc(
                                """
                                Folds the back of this taco to reduce sauce leakage.

                                <p>For {@link $T#KOREAN}, the front may also be folded.
                                """,
                                Locale.class)
                        .addParameter(Locale.class, "locale")
                        .build())
                .build();
        // Mentioning a type in Javadoc will not cause an import to be added (java.util.Random here),
        // but the short name will be used if it's already imported (java.util.Locale here).
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.String;
                        import java.util.Locale;
                        import java.util.Random;

                        /**
                         * A hard or soft tortilla, loosely folded and filled with whatever
                         * {@link Random random} tex-mex stuff we could find in the pantry
                         * and some {@link String} cheese.
                         */
                        class Taco {
                          /**
                           * True for a soft flour tortilla; false for a crunchy corn tortilla.
                           */
                          boolean soft;

                          /**
                           * Folds the back of this taco to reduce sauce leakage.
                           *
                           * <p>For {@link Locale#KOREAN}, the front may also be folded.
                           */
                          void refold(Locale locale) {
                          }
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void annotationsInAnnotations() {
        ClassName beef = ClassName.get(tacosPackage, "Beef");
        ClassName chicken = ClassName.get(tacosPackage, "Chicken");
        ClassName option = ClassName.get(tacosPackage, "Option");
        ClassName mealDeal = ClassName.get(tacosPackage, "MealDeal");
        TypeSpec typeSpec = TypeSpec.classBuilder("Menu")
                .addAnnotation(AnnotationSpec.builder(mealDeal)
                        .addMember("price", "$L", 500)
                        .addMember(
                                "options",
                                "$L",
                                AnnotationSpec.builder(option)
                                        .addMember("name", "$S", "taco")
                                        .addMember("meat", "$T.class", beef)
                                        .build())
                        .addMember(
                                "options",
                                "$L",
                                AnnotationSpec.builder(option)
                                        .addMember("name", "$S", "quesadilla")
                                        .addMember("meat", "$T.class", chicken)
                                        .build())
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        @MealDeal(
                            price = 500,
                            options = {
                                @Option(name = "taco", meat = Beef.class),
                                @Option(name = "quesadilla", meat = Chicken.class)
                            }
                        )
                        class Menu {
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void varargs() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taqueria")
                .addMethod(MethodSpec.methodBuilder("prepare")
                        .addParameter(int.class, "workers")
                        .addParameter(Runnable[].class, "jobs")
                        .varargs()
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.Runnable;

                        class Taqueria {
                          void prepare(int workers, Runnable... jobs) {
                          }
                        }
                        """);
    }

    @Test
    public void codeBlocks() {
        CodeBlock ifBlock = CodeBlock.builder()
                .beginControlFlow("if (!a.equals(b))")
                .addStatement("return i")
                .endControlFlow()
                .build();
        CodeBlock methodBody = CodeBlock.builder()
                .addStatement("$T size = $T.min(listA.size(), listB.size())", int.class, Math.class)
                .beginControlFlow("for ($T i = 0; i < size; i++)", int.class)
                .addStatement("$T $N = $N.get(i)", String.class, "a", "listA")
                .addStatement("$T $N = $N.get(i)", String.class, "b", "listB")
                .add("$L", ifBlock)
                .endControlFlow()
                .addStatement("return size")
                .build();
        CodeBlock fieldBlock = CodeBlock.builder()
                .add("$>$>")
                .add("\n$T.<$T, $T>builder()$>$>", ImmutableMap.class, String.class, String.class)
                .add("\n.add($S, $S)", '\'', "&#39;")
                .add("\n.add($S, $S)", '&', "&amp;")
                .add("\n.add($S, $S)", '<', "&lt;")
                .add("\n.add($S, $S)", '>', "&gt;")
                .add("\n.build()$<$<")
                .add("$<$<")
                .build();
        FieldSpec escapeHtml = FieldSpec.builder(
                        ParameterizedTypeName.get(Map.class, String.class, String.class), "ESCAPE_HTML")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer(fieldBlock)
                .build();
        TypeSpec typeSpec = TypeSpec.classBuilder("Util")
                .addField(escapeHtml)
                .addMethod(MethodSpec.methodBuilder("commonPrefixLength")
                        .returns(int.class)
                        .addParameter(ParameterizedTypeName.get(List.class, String.class), "listA")
                        .addParameter(ParameterizedTypeName.get(List.class, String.class), "listB")
                        .addCode(methodBody)
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import com.google.common.collect.ImmutableMap;
                        import java.lang.Math;
                        import java.lang.String;
                        import java.util.List;
                        import java.util.Map;

                        class Util {
                          private static final Map<String, String> ESCAPE_HTML =\s
                              ImmutableMap.<String, String>builder()
                                  .add("'", "&#39;")
                                  .add("&", "&amp;")
                                  .add("<", "&lt;")
                                  .add(">", "&gt;")
                                  .build();

                          int commonPrefixLength(List<String> listA, List<String> listB) {
                            int size = Math.min(listA.size(), listB.size());
                            for (int i = 0; i < size; i++) {
                              String a = listA.get(i);
                              String b = listB.get(i);
                              if (!a.equals(b)) {
                                return i;
                              }
                            }
                            return size;
                          }
                        }
                        """);
    }

    @Test
    public void indexedElseIf() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addMethod(MethodSpec.methodBuilder("choices")
                        .beginControlFlow("if ($1L != null || $1L == $2L)", "taco", "otherTaco")
                        .addStatement("$T.out.println($S)", System.class, "only one taco? NOO!")
                        .nextControlFlow("else if ($1L.$3L && $2L.$3L)", "taco", "otherTaco", "isSupreme()")
                        .addStatement("$T.out.println($S)", System.class, "taco heaven")
                        .endControlFlow()
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.System;

                        class Taco {
                          void choices() {
                            if (taco != null || taco == otherTaco) {
                              System.out.println("only one taco? NOO!");
                            } else if (taco.isSupreme() && otherTaco.isSupreme()) {
                              System.out.println("taco heaven");
                            }
                          }
                        }
                        """);
    }

    @Test
    public void elseIf() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addMethod(MethodSpec.methodBuilder("choices")
                        .beginControlFlow("if (5 < 4) ")
                        .addStatement("$T.out.println($S)", System.class, "wat")
                        .nextControlFlow("else if (5 < 6)")
                        .addStatement("$T.out.println($S)", System.class, "hello")
                        .endControlFlow()
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.System;

                        class Taco {
                          void choices() {
                            if (5 < 4)  {
                              System.out.println("wat");
                            } else if (5 < 6) {
                              System.out.println("hello");
                            }
                          }
                        }
                        """);
    }

    @Test
    public void doWhile() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addMethod(MethodSpec.methodBuilder("loopForever")
                        .beginControlFlow("do")
                        .addStatement("$T.out.println($S)", System.class, "hello")
                        .endControlFlow("while (5 < 6)")
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.System;

                        class Taco {
                          void loopForever() {
                            do {
                              System.out.println("hello");
                            } while (5 < 6);
                          }
                        }
                        """);
    }

    @Test
    public void inlineIndent() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addMethod(MethodSpec.methodBuilder("inlineIndent")
                        .addCode("if (3 < 4) {\n$>$T.out.println($S);\n$<}\n", System.class, "hello")
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.System;

                        class Taco {
                          void inlineIndent() {
                            if (3 < 4) {
                              System.out.println("hello");
                            }
                          }
                        }
                        """);
    }

    @Test
    public void defaultModifiersForInterfaceMembers() {
        TypeSpec typeSpec = TypeSpec.interfaceBuilder("Taco")
                .addField(FieldSpec.builder(String.class, "SHELL")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                        .initializer("$S", "crunchy corn")
                        .build())
                .addMethod(MethodSpec.methodBuilder("fold")
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .build())
                .addType(TypeSpec.classBuilder("Topping")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.String;

                        interface Taco {
                          String SHELL = "crunchy corn";

                          void fold();

                          class Topping {
                          }
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void defaultModifiersForMemberInterfacesAndEnums() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addType(TypeSpec.classBuilder("Meat")
                        .addModifiers(Modifier.STATIC)
                        .build())
                .addType(TypeSpec.interfaceBuilder("Tortilla")
                        .addModifiers(Modifier.STATIC)
                        .build())
                .addType(TypeSpec.enumBuilder("Topping")
                        .addModifiers(Modifier.STATIC)
                        .addEnumConstant("SALSA")
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        class Taco {
                          static class Meat {
                          }

                          interface Tortilla {
                          }

                          enum Topping {
                            SALSA
                          }
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void membersOrdering() {
        // Hand out names in reverse-alphabetical order to defend against unexpected sorting.
        TypeSpec typeSpec = TypeSpec.classBuilder("Members")
                .addType(TypeSpec.classBuilder("Z").build())
                .addType(TypeSpec.classBuilder("Y").build())
                .addField(String.class, "X", Modifier.STATIC)
                .addField(String.class, "W")
                .addField(String.class, "V", Modifier.STATIC)
                .addField(String.class, "U")
                .addMethod(MethodSpec.methodBuilder("T")
                        .addModifiers(Modifier.STATIC)
                        .build())
                .addMethod(MethodSpec.methodBuilder("S").build())
                .addMethod(MethodSpec.methodBuilder("R")
                        .addModifiers(Modifier.STATIC)
                        .build())
                .addMethod(MethodSpec.methodBuilder("Q").build())
                .addMethod(MethodSpec.constructorBuilder()
                        .addParameter(int.class, "p")
                        .build())
                .addMethod(MethodSpec.constructorBuilder()
                        .addParameter(long.class, "o")
                        .build())
                .build();
        // Static fields, instance fields, constructors, methods, classes.
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.String;

                        class Members {
                          static String X;

                          static String V;

                          String W;

                          String U;

                          Members(int p) {
                          }

                          Members(long o) {
                          }

                          static void T() {
                          }

                          void S() {
                          }

                          static void R() {
                          }

                          void Q() {
                          }

                          class Z {
                          }

                          class Y {
                          }
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void nativeMethods() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addMethod(MethodSpec.methodBuilder("nativeInt")
                        .addModifiers(Modifier.NATIVE)
                        .returns(int.class)
                        .build())
                // GWT JSNI
                .addMethod(MethodSpec.methodBuilder("alert")
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.NATIVE)
                        .addParameter(String.class, "msg")
                        .addCode(CodeBlock.builder()
                                .add(" /*-{\n")
                                .indent()
                                .addStatement("$$wnd.alert(msg)")
                                .unindent()
                                .add("}-*/")
                                .build())
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.String;

                        class Taco {
                          native int nativeInt();

                          public static native void alert(String msg) /*-{
                            $wnd.alert(msg);
                          }-*/;
                        }
                        """);
    }

    @Test
    public void nullStringLiteral() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addField(FieldSpec.builder(String.class, "NULL")
                        .initializer("$S", (Object) null)
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.String;

                        class Taco {
                          String NULL = null;
                        }
                        """);
    }

    @Test
    public void annotationToString() {
        AnnotationSpec annotation = AnnotationSpec.builder(SuppressWarnings.class)
                .addMember("value", "$S", "unused")
                .build();
        assertThat(annotation.toString()).isEqualTo("@java.lang.SuppressWarnings(\"unused\")");
    }

    @Test
    public void codeBlockToString() {
        CodeBlock codeBlock = CodeBlock.builder()
                .addStatement("$T $N = $S.substring(0, 3)", String.class, "s", "taco")
                .build();
        assertThat(codeBlock.toString()).isEqualTo("java.lang.String s = \"taco\".substring(0, 3);\n");
    }

    @Test
    public void codeBlockAddStatementOfCodeBlockToString() {
        CodeBlock contents = CodeBlock.of("$T $N = $S.substring(0, 3)", String.class, "s", "taco");
        CodeBlock statement = CodeBlock.builder().addStatement(contents).build();
        assertThat(statement.toString()).isEqualTo("java.lang.String s = \"taco\".substring(0, 3);\n");
    }

    @Test
    public void fieldToString() {
        FieldSpec field = FieldSpec.builder(String.class, "s", Modifier.FINAL)
                .initializer("$S.substring(0, 3)", "taco")
                .build();
        assertThat(field.toString()).isEqualTo("final java.lang.String s = \"taco\".substring(0, 3);\n");
    }

    @Test
    public void methodToString() {
        MethodSpec method = MethodSpec.methodBuilder("toString")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return $S", "taco")
                .build();
        assertThat(method.toString())
                .isEqualTo(
                        """
                        @java.lang.Override
                        public java.lang.String toString() {
                          return "taco";
                        }
                        """);
    }

    @Test
    public void constructorToString() {
        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ClassName.get(tacosPackage, "Taco"), "taco")
                .addStatement("this.$N = $N", "taco", "taco")
                .build();
        assertThat(constructor.toString())
                .isEqualTo(
                        """
                        public Constructor(com.palantir.tacos.Taco taco) {
                          this.taco = taco;
                        }
                        """);
    }

    @Test
    public void parameterToString() {
        ParameterSpec parameter = ParameterSpec.builder(ClassName.get(tacosPackage, "Taco"), "taco")
                .addModifiers(Modifier.FINAL)
                .addAnnotation(ClassName.get("javax.annotation", "Nullable"))
                .build();
        assertThat(parameter.toString()).isEqualTo("@javax.annotation.Nullable final com.palantir.tacos.Taco taco");
    }

    @Test
    public void classToString() {
        TypeSpec type = TypeSpec.classBuilder("Taco").build();
        assertThat(type.toString())
                .isEqualTo(
                        """
                        class Taco {
                        }
                        """);
    }

    @Test
    public void anonymousClassToString() {
        TypeSpec type = TypeSpec.anonymousClassBuilder("")
                .addSuperinterface(Runnable.class)
                .addMethod(MethodSpec.methodBuilder("run")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .build())
                .build();
        assertThat(type.toString())
                .isEqualTo(
                        """
                        new java.lang.Runnable() {
                          @java.lang.Override
                          public void run() {
                          }
                        }""");
    }

    @Test
    public void interfaceClassToString() {
        TypeSpec type = TypeSpec.interfaceBuilder("Taco").build();
        assertThat(type.toString())
                .isEqualTo(
                        """
                        interface Taco {
                        }
                        """);
    }

    @Test
    public void annotationDeclarationToString() {
        TypeSpec type = TypeSpec.annotationBuilder("Taco").build();
        assertThat(type.toString())
                .isEqualTo(
                        """
                        @interface Taco {
                        }
                        """);
    }

    private String toString(TypeSpec typeSpec) {
        return JavaFile.builder(tacosPackage, typeSpec).build().toString();
    }

    @Test
    public void multilineStatement() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addMethod(MethodSpec.methodBuilder("toString")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(String.class)
                        .addStatement("return $S\n+ $S\n+ $S\n+ $S\n+ $S", "Taco(", "beef,", "lettuce,", "cheese", ")")
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.Override;
                        import java.lang.String;

                        class Taco {
                          @Override
                          public String toString() {
                            return "Taco("
                                + "beef,"
                                + "lettuce,"
                                + "cheese"
                                + ")";
                          }
                        }
                        """);
    }

    @Test
    public void multilineStatementWithAnonymousClass() {
        TypeName stringComparator = ParameterizedTypeName.get(Comparator.class, String.class);
        TypeName listOfString = ParameterizedTypeName.get(List.class, String.class);
        TypeSpec prefixComparator = TypeSpec.anonymousClassBuilder("")
                .addSuperinterface(stringComparator)
                .addMethod(MethodSpec.methodBuilder("compare")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(int.class)
                        .addParameter(String.class, "a")
                        .addParameter(String.class, "b")
                        .addStatement("return a.substring(0, length)\n" + ".compareTo(b.substring(0, length))")
                        .build())
                .build();
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addMethod(MethodSpec.methodBuilder("comparePrefix")
                        .returns(stringComparator)
                        .addParameter(int.class, "length", Modifier.FINAL)
                        .addStatement("return $L", prefixComparator)
                        .build())
                .addMethod(MethodSpec.methodBuilder("sortPrefix")
                        .addParameter(listOfString, "list")
                        .addParameter(int.class, "length", Modifier.FINAL)
                        .addStatement("$T.sort(\nlist,\n$L)", Collections.class, prefixComparator)
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.Override;
                        import java.lang.String;
                        import java.util.Collections;
                        import java.util.Comparator;
                        import java.util.List;

                        class Taco {
                          Comparator<String> comparePrefix(final int length) {
                            return new Comparator<String>() {
                              @Override
                              public int compare(String a, String b) {
                                return a.substring(0, length)
                                    .compareTo(b.substring(0, length));
                              }
                            };
                          }

                          void sortPrefix(List<String> list, final int length) {
                            Collections.sort(
                                list,
                                new Comparator<String>() {
                                  @Override
                                  public int compare(String a, String b) {
                                    return a.substring(0, length)
                                        .compareTo(b.substring(0, length));
                                  }
                                });
                          }
                        }
                        """);
    }

    @Test
    public void multilineStrings() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addField(FieldSpec.builder(String.class, "toppings")
                        .initializer("$S", "shell\nbeef\nlettuce\ncheese\n")
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.String;

                        class Taco {
                          String toppings = "shell\\n"
                              + "beef\\n"
                              + "lettuce\\n"
                              + "cheese\\n";
                        }
                        """);
    }

    @Test
    public void doubleFieldInitialization() {
        assertThatThrownBy(() -> FieldSpec.builder(String.class, "listA")
                        .initializer("foo")
                        .initializer("bar")
                        .build())
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> FieldSpec.builder(String.class, "listA")
                        .initializer(CodeBlock.builder().add("foo").build())
                        .initializer(CodeBlock.builder().add("bar").build())
                        .build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void nullAnnotationsAddition() {
        assertThatThrownBy(() -> TypeSpec.classBuilder("Taco").addAnnotations(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("annotationSpecs == null");
    }

    @Test
    public void multipleAnnotationAddition() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addAnnotations(Arrays.asList(
                        AnnotationSpec.builder(SuppressWarnings.class)
                                .addMember("value", "$S", "unchecked")
                                .build(),
                        AnnotationSpec.builder(Deprecated.class).build()))
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.Deprecated;
                        import java.lang.SuppressWarnings;

                        @SuppressWarnings("unchecked")
                        @Deprecated
                        class Taco {
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void nullFieldsAddition() {
        assertThatThrownBy(() -> TypeSpec.classBuilder("Taco").addFields(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("fieldSpecs == null");
    }

    @Test
    public void multipleFieldAddition() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addFields(Arrays.asList(
                        FieldSpec.builder(int.class, "ANSWER", Modifier.STATIC, Modifier.FINAL)
                                .build(),
                        FieldSpec.builder(BigDecimal.class, "price", Modifier.PRIVATE)
                                .build()))
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.math.BigDecimal;

                        class Taco {
                          static final int ANSWER;

                          private BigDecimal price;
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void nullMethodsAddition() {
        assertThatThrownBy(() -> TypeSpec.classBuilder("Taco").addMethods(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("methodSpecs == null");
    }

    @Test
    public void multipleMethodAddition() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addMethods(Arrays.asList(
                        MethodSpec.methodBuilder("getAnswer")
                                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                                .returns(int.class)
                                .addStatement("return $L", 42)
                                .build(),
                        MethodSpec.methodBuilder("getRandomQuantity")
                                .addModifiers(Modifier.PUBLIC)
                                .returns(int.class)
                                .addJavadoc("chosen by fair dice roll ;)")
                                .addStatement("return $L", 4)
                                .build()))
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        class Taco {
                          public static int getAnswer() {
                            return 42;
                          }

                          /**
                           * chosen by fair dice roll ;)
                           */
                          public int getRandomQuantity() {
                            return 4;
                          }
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void nullSuperinterfacesAddition() {
        assertThatThrownBy(() -> TypeSpec.classBuilder("Taco").addSuperinterfaces(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("superinterfaces == null");
    }

    @Test
    public void nullSingleSuperinterfaceAddition() {
        assertThatThrownBy(() -> TypeSpec.classBuilder("Taco").addSuperinterface((TypeName) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("superinterface == null");
    }

    @Test
    public void nullInSuperinterfaceIterableAddition() {
        List<TypeName> superinterfaces = new ArrayList<>();
        superinterfaces.add(TypeName.get(List.class));
        superinterfaces.add(null);

        assertThatThrownBy(() -> TypeSpec.classBuilder("Taco").addSuperinterfaces(superinterfaces))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("superinterface == null");
    }

    @Test
    public void multipleSuperinterfaceAddition() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addSuperinterfaces(Arrays.asList(TypeName.get(Serializable.class), TypeName.get(EventListener.class)))
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.io.Serializable;
                        import java.util.EventListener;

                        class Taco implements Serializable, EventListener {
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void nullPermittedSubclassesAddition() {
        assertThatThrownBy(() -> TypeSpec.classBuilder("Taco").addPermittedSubclasses(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("permittedSubclasses == null");
    }

    @Test
    public void nullPermittedSubclassAddition() {
        assertThatThrownBy(() -> TypeSpec.classBuilder("Taco").addPermittedSubclass((TypeName) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("permittedSubclass == null");
    }

    @Test
    public void nullInPermittedSubclassIterableAddition() {
        List<TypeName> permittedSublclasses = new ArrayList<>();
        permittedSublclasses.add(TypeName.get(List.class));
        permittedSublclasses.add(null);

        assertThatThrownBy(() -> TypeSpec.classBuilder("Taco").addPermittedSubclasses(permittedSublclasses))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("permittedSubclass == null");
    }

    @Test
    public void multiplePermittedSubclassesAddition() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addModifiers(Modifier.SEALED)
                .addPermittedSubclasses(Arrays.asList(
                        ClassName.bestGuess("com.palantir.tacos.BeefTaco"),
                        ClassName.bestGuess("com.palantir.tacos.ChickenTaco")))
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        sealed class Taco permits BeefTaco, ChickenTaco {
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void nullModifiersAddition() {
        assertThatThrownBy(() -> TypeSpec.classBuilder("Taco")
                        .addModifiers((Modifier) null)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("modifiers contain null");
    }

    @Test
    public void nullTypeVariablesAddition() {
        assertThatThrownBy(() -> TypeSpec.classBuilder("Taco").addTypeVariables(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("typeVariables == null");
    }

    @Test
    public void multipleTypeVariableAddition() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Location")
                .addTypeVariables(Arrays.asList(TypeVariableName.get("T"), TypeVariableName.get("P", Number.class)))
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.Number;

                        class Location<T, P extends Number> {
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void nullTypesAddition() {
        assertThatThrownBy(() -> TypeSpec.classBuilder("Taco").addTypes(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("typeSpecs == null");
    }

    @Test
    public void multipleTypeAddition() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addTypes(Arrays.asList(
                        TypeSpec.classBuilder("Topping").build(),
                        TypeSpec.classBuilder("Sauce").build()))
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        class Taco {
                          class Topping {
                          }

                          class Sauce {
                          }
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void tryCatch() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addMethod(MethodSpec.methodBuilder("addTopping")
                        .addParameter(ClassName.get("com.palantir.tacos", "Topping"), "topping")
                        .beginControlFlow("try")
                        .addCode("/* do something tricky with the topping */\n")
                        .nextControlFlow("catch ($T e)", ClassName.get("com.palantir.tacos", "IllegalToppingException"))
                        .endControlFlow()
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        class Taco {
                          void addTopping(Topping topping) {
                            try {
                              /* do something tricky with the topping */
                            } catch (IllegalToppingException e) {
                            }
                          }
                        }
                        """);
    }

    @Test
    public void ifElse() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addMethod(MethodSpec.methodBuilder("isDelicious")
                        .addParameter(TypeName.INT, "count")
                        .returns(TypeName.BOOLEAN)
                        .beginControlFlow("if (count > 0)")
                        .addStatement("return true")
                        .nextControlFlow("else")
                        .addStatement("return false")
                        .endControlFlow()
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        class Taco {
                          boolean isDelicious(int count) {
                            if (count > 0) {
                              return true;
                            } else {
                              return false;
                            }
                          }
                        }
                        """);
    }

    @Test
    public void literalFromAnything() {
        Object value = new Object() {
            @Override
            public String toString() {
                return "foo";
            }
        };
        assertThat(CodeBlock.of("$L", value).toString()).isEqualTo("foo");
    }

    @Test
    public void nameFromCharSequence() {
        assertThat(CodeBlock.of("$N", "text").toString()).isEqualTo("text");
    }

    @Test
    public void nameFromField() {
        FieldSpec field = FieldSpec.builder(String.class, "field").build();
        assertThat(CodeBlock.of("$N", field).toString()).isEqualTo("field");
    }

    @Test
    public void nameFromParameter() {
        ParameterSpec parameter =
                ParameterSpec.builder(String.class, "parameter").build();
        assertThat(CodeBlock.of("$N", parameter).toString()).isEqualTo("parameter");
    }

    @Test
    public void nameFromMethod() {
        MethodSpec method = MethodSpec.methodBuilder("method")
                .addModifiers(Modifier.ABSTRACT)
                .returns(String.class)
                .build();
        assertThat(CodeBlock.of("$N", method).toString()).isEqualTo("method");
    }

    @Test
    public void nameFromType() {
        TypeSpec type = TypeSpec.classBuilder("Type").build();
        assertThat(CodeBlock.of("$N", type).toString()).isEqualTo("Type");
    }

    @Test
    public void nameFromUnsupportedType() {
        assertThatThrownBy(() -> CodeBlock.builder().add("$N", String.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expected name but was " + String.class);
    }

    @Test
    public void stringFromAnything() {
        Object value = new Object() {
            @Override
            public String toString() {
                return "foo";
            }
        };
        assertThat(CodeBlock.of("$S", value).toString()).isEqualTo("\"foo\"");
    }

    @Test
    public void stringFromNull() {
        assertThat(CodeBlock.of("$S", new Object[] {null}).toString()).isEqualTo("null");
    }

    @Test
    public void typeFromTypeName() {
        TypeName typeName = TypeName.get(String.class);
        assertThat(CodeBlock.of("$T", typeName).toString()).isEqualTo("java.lang.String");
    }

    @Test
    public void typeFromTypeMirror() {
        TypeMirror mirror = getElement(String.class).asType();
        assertThat(CodeBlock.of("$T", mirror).toString()).isEqualTo("java.lang.String");
    }

    @Test
    public void typeFromTypeElement() {
        TypeElement element = getElement(String.class);
        assertThat(CodeBlock.of("$T", element).toString()).isEqualTo("java.lang.String");
    }

    @Test
    public void typeFromReflectType() {
        assertThat(CodeBlock.of("$T", String.class).toString()).isEqualTo("java.lang.String");
    }

    @Test
    public void typeFromUnsupportedType() {
        assertThatThrownBy(() -> CodeBlock.builder().add("$T", "java.lang.String"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("expected type but was java.lang.String");
    }

    @Test
    public void tooFewArguments() {
        assertThatThrownBy(() -> CodeBlock.builder().add("$S"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("index 1 for '$S' not in range (received 0 arguments)");
    }

    @Test
    public void unusedArgumentsRelative() {
        assertThatThrownBy(() -> CodeBlock.builder().add("$L $L", "a", "b", "c"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unused arguments: expected 2, received 3");
    }

    @Test
    public void unusedArgumentsIndexed() {
        assertThatThrownBy(() -> CodeBlock.builder().add("$1L $2L", "a", "b", "c"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unused argument: $3");
        assertThatThrownBy(() -> CodeBlock.builder().add("$1L $1L $1L", "a", "b", "c"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unused arguments: $2, $3");
        assertThatThrownBy(() -> CodeBlock.builder().add("$3L $1L $3L $1L $3L", "a", "b", "c", "d"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unused arguments: $2, $4");
    }

    @Test
    public void superClassOnlyValidForClasses() {
        assertThatThrownBy(() -> TypeSpec.annotationBuilder("A").superclass(ClassName.get(Object.class)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TypeSpec.enumBuilder("E").superclass(ClassName.get(Object.class)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TypeSpec.interfaceBuilder("I").superclass(ClassName.get(Object.class)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void permittedSubclassOnlyValidForClassesAndInterfaces() {
        assertThatThrownBy(() -> TypeSpec.annotationBuilder("A").addPermittedSubclass(ClassName.get(Object.class)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TypeSpec.enumBuilder("E").addPermittedSubclass(ClassName.get(Object.class)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void invalidSuperClass() {
        assertThatThrownBy(() -> TypeSpec.classBuilder("foo")
                        .superclass(ClassName.get(List.class))
                        .superclass(ClassName.get(Map.class)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TypeSpec.classBuilder("foo").superclass(TypeName.INT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void staticCodeBlock() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addField(String.class, "foo", Modifier.PRIVATE)
                .addField(String.class, "FOO", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .addStaticBlock(
                        CodeBlock.builder().addStatement("FOO = $S", "FOO").build())
                .addMethod(MethodSpec.methodBuilder("toString")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(String.class)
                        .addCode("return FOO;\n")
                        .build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.Override;
                        import java.lang.String;

                        class Taco {
                          private static final String FOO;

                          static {
                            FOO = "FOO";
                          }

                          private String foo;

                          @Override
                          public String toString() {
                            return FOO;
                          }
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void initializerBlockInRightPlace() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addField(String.class, "foo", Modifier.PRIVATE)
                .addField(String.class, "FOO", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .addStaticBlock(
                        CodeBlock.builder().addStatement("FOO = $S", "FOO").build())
                .addMethod(MethodSpec.constructorBuilder().build())
                .addMethod(MethodSpec.methodBuilder("toString")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(String.class)
                        .addCode("return FOO;\n")
                        .build())
                .addInitializerBlock(
                        CodeBlock.builder().addStatement("foo = $S", "FOO").build())
                .build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.Override;
                        import java.lang.String;

                        class Taco {
                          private static final String FOO;

                          static {
                            FOO = "FOO";
                          }

                          private String foo;

                          {
                            foo = "FOO";
                          }

                          Taco() {
                          }

                          @Override
                          public String toString() {
                            return FOO;
                          }
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void initializersToBuilder() {
        // Tests if toBuilder() contains correct static and instance initializers
        Element originatingElement = getElement(TypeSpecTest.class);
        TypeSpec taco = TypeSpec.classBuilder("Taco")
                .addField(String.class, "foo", Modifier.PRIVATE)
                .addField(String.class, "FOO", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .addStaticBlock(
                        CodeBlock.builder().addStatement("FOO = $S", "FOO").build())
                .addMethod(MethodSpec.constructorBuilder().build())
                .addMethod(MethodSpec.methodBuilder("toString")
                        .addAnnotation(Override.class)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(String.class)
                        .addCode("return FOO;\n")
                        .build())
                .addInitializerBlock(
                        CodeBlock.builder().addStatement("foo = $S", "FOO").build())
                .addOriginatingElement(originatingElement)
                .alwaysQualify("com.example.AlwaysQualified")
                .build();

        TypeSpec recreatedTaco = taco.toBuilder().build();
        assertThat(toString(taco)).isEqualTo(toString(recreatedTaco));
        assertThat(taco.originatingElements()).containsExactlyElementsOf(recreatedTaco.originatingElements());
        assertThat(taco.alwaysQualifiedNames()).containsExactlyElementsOf(recreatedTaco.alwaysQualifiedNames());

        TypeSpec initializersAdded = taco.toBuilder()
                .addInitializerBlock(CodeBlock.builder()
                        .addStatement("foo = $S", "instanceFoo")
                        .build())
                .addStaticBlock(CodeBlock.builder()
                        .addStatement("FOO = $S", "staticFoo")
                        .build())
                .build();

        assertThat(toString(initializersAdded))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.Override;
                        import java.lang.String;

                        class Taco {
                          private static final String FOO;

                          static {
                            FOO = "FOO";
                          }
                          static {
                            FOO = "staticFoo";
                          }

                          private String foo;

                          {
                            foo = "FOO";
                          }
                          {
                            foo = "instanceFoo";
                          }

                          Taco() {
                          }

                          @Override
                          public String toString() {
                            return FOO;
                          }
                        }
                        """);
        checkToBuilderRoundtrip(initializersAdded);
    }

    @Test
    public void initializerBlockUnsupportedExceptionOnInterface() {
        TypeSpec.Builder interfaceBuilder = TypeSpec.interfaceBuilder("Taco");
        assertThatThrownBy(() ->
                        interfaceBuilder.addInitializerBlock(CodeBlock.builder().build()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void initializerBlockUnsupportedExceptionOnAnnotation() {
        TypeSpec.Builder annotationBuilder = TypeSpec.annotationBuilder("Taco");
        assertThatThrownBy(() -> annotationBuilder.addInitializerBlock(
                        CodeBlock.builder().build()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void lineWrapping() {
        MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder("call");
        methodBuilder.addCode("$[call(");
        for (int i = 0; i < 32; i++) {
            methodBuilder.addParameter(String.class, "s" + i);
            methodBuilder.addCode(i > 0 ? ",$W$S" : "$S", i);
        }
        methodBuilder.addCode(");$]\n");

        TypeSpec typeSpec = TypeSpec.classBuilder("Taco").addMethod(methodBuilder.build()).build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        import java.lang.String;

                        class Taco {
                          void call(String s0, String s1, String s2, String s3, String s4, String s5, String s6, \
                        String s7,
                              String s8, String s9, String s10, String s11, String s12, String s13, String s14, \
                        String s15,
                              String s16, String s17, String s18, String s19, String s20, String s21, String s22,
                              String s23, String s24, String s25, String s26, String s27, String s28, String s29,
                              String s30, String s31) {
                            call("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", \
                        "16",
                                "17", "18", "19", "20", "21", "22", "23", "24", "25", "26", "27", "28", "29", "30", \
                        "31");
                          }
                        }
                        """);
    }

    @Test
    public void lineWrappingWithZeroWidthSpace() {
        MethodSpec method = MethodSpec.methodBuilder("call")
                .addCode("$[iAmSickOfWaitingInLine($Z")
                .addCode("it, has, been, far, too, long, of, a, wait, and, i, would, like, to, eat, ")
                .addCode("this, is, a, run, on, sentence")
                .addCode(");$]\n")
                .build();

        TypeSpec typeSpec = TypeSpec.classBuilder("Taco").addMethod(method).build();
        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        class Taco {
                          void call() {
                            iAmSickOfWaitingInLine(
                                it, has, been, far, too, long, of, a, wait, and, i, would, like, to, eat, this, is, a, \
                        run, on, sentence);
                          }
                        }
                        """);
    }

    @Test
    public void equalsAndHashCode() {
        TypeSpec a = TypeSpec.interfaceBuilder("taco").build();
        TypeSpec b = TypeSpec.interfaceBuilder("taco").build();
        assertThat(a.equals(b)).isTrue();
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        a = TypeSpec.classBuilder("taco").build();
        b = TypeSpec.classBuilder("taco").build();
        assertThat(a.equals(b)).isTrue();
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        a = TypeSpec.enumBuilder("taco").addEnumConstant("SALSA").build();
        b = TypeSpec.enumBuilder("taco").addEnumConstant("SALSA").build();
        assertThat(a.equals(b)).isTrue();
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        a = TypeSpec.annotationBuilder("taco").build();
        b = TypeSpec.annotationBuilder("taco").build();
        assertThat(a.equals(b)).isTrue();
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    public void classNameFactories() {
        ClassName className = ClassName.get("com.example", "Example");
        assertThat(TypeSpec.classBuilder(className).build().name()).isEqualTo("Example");
        assertThat(TypeSpec.interfaceBuilder(className).build().name()).isEqualTo("Example");
        assertThat(TypeSpec.enumBuilder(className).addEnumConstant("A").build().name())
                .isEqualTo("Example");
        assertThat(TypeSpec.annotationBuilder(className).build().name()).isEqualTo("Example");
    }

    @Test
    public void javadocWithTrailingLineDoesNotAddAnother() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addJavadoc("Some doc with a newline\n")
                .build();

        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        /**
                         * Some doc with a newline
                         */
                        class Taco {
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }

    @Test
    public void javadocEnsuresTrailingLine() {
        TypeSpec typeSpec = TypeSpec.classBuilder("Taco")
                .addJavadoc("Some doc with a newline")
                .build();

        assertThat(toString(typeSpec))
                .isEqualTo(
                        """
                        package com.palantir.tacos;

                        /**
                         * Some doc with a newline
                         */
                        class Taco {
                        }
                        """);
        checkToBuilderRoundtrip(typeSpec);
    }
}
