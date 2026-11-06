/*
 * Copyright (C) 2016 Square, Inc.
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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import javax.lang.model.element.Modifier;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public final class UnicodeLineBreakTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(chars = {'a', '\n', '\r', '\u000b', '\f', '\u0085', '\u2028', '\u2029', '"', '\\', '☃'})
    void stringLiteralRoundTrip(char character) throws Exception {
        String value = "before" + character + "after";
        JavaFile file = JavaFile.builder(
                        "example",
                        TypeSpec.classBuilder("Generated")
                                .addModifiers(Modifier.PUBLIC)
                                .addMethod(MethodSpec.methodBuilder("value")
                                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                                        .returns(String.class)
                                        .addStatement("return $S", value)
                                        .build())
                                .build())
                .build();
        compile(file);
        try (URLClassLoader loader =
                new URLClassLoader(new URL[] {directory.toUri().toURL()})) {
            assertThat(loader.loadClass("example.Generated").getMethod("value").invoke(null))
                    .isEqualTo(value);
        }
    }

    @Test
    void characterLiteralRoundTrip() throws Exception {
        CharacterValues values = Annotated.class.getAnnotation(CharacterValues.class);
        AnnotationSpec annotation = AnnotationSpec.get(values);
        assertThat(annotation.toString()).contains("'\\u2028'", "'\\u2029'");
        JavaFile file = JavaFile.builder(
                        "example",
                        TypeSpec.classBuilder("Generated")
                                .addModifiers(Modifier.PUBLIC)
                                .addAnnotation(annotation)
                                .build())
                .build();
        compile(file);
        try (URLClassLoader loader =
                new URLClassLoader(new URL[] {directory.toUri().toURL()})) {
            assertThat(loader.loadClass("example.Generated")
                            .getAnnotation(CharacterValues.class)
                            .value())
                    .containsExactly(values.value());
        }
    }

    @ParameterizedTest
    @ValueSource(chars = {'\n', '\r', '\u000b', '\f', '\u0085', '\u2028', '\u2029'})
    void rawCodeAndJavadocKeepLineBreaks(char separator) {
        assertThat(CodeBlock.of("$L", "first" + separator + "second").toString())
                .isEqualTo("first\nsecond");
        assertThat(JavaFile.builder(
                                "example",
                                TypeSpec.classBuilder("Documented")
                                        .addJavadoc("first" + separator + "second\n")
                                        .build())
                        .build()
                        .toString())
                .contains(" * first\n * second\n");
    }

    private void compile(JavaFile file) throws Exception {
        file.writeTo(directory);
        assertThat(ToolProvider.getSystemJavaCompiler()
                        .run(
                                null,
                                null,
                                null,
                                "-d",
                                directory.toString(),
                                "-classpath",
                                CharacterValues.class
                                        .getProtectionDomain()
                                        .getCodeSource()
                                        .getLocation()
                                        .getPath(),
                                directory.resolve("example/Generated.java").toString()))
                .isZero();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface CharacterValues {
        char[] value();
    }

    @CharacterValues({'\u2028', '\u2029'})
    private static final class Annotated {}
}
