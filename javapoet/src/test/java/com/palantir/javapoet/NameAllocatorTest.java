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

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public final class NameAllocatorTest {

    @Test
    public void usage() {
        NameAllocator nameAllocator = new NameAllocator();
        assertThat(nameAllocator.newName("foo", 1)).isEqualTo("foo");
        assertThat(nameAllocator.newName("bar", 2)).isEqualTo("bar");
        assertThat(nameAllocator.get(1)).isEqualTo("foo");
        assertThat(nameAllocator.get(2)).isEqualTo("bar");
    }

    @Test
    public void nameCollision() {
        NameAllocator nameAllocator = new NameAllocator();
        assertThat(nameAllocator.newName("foo")).isEqualTo("foo");
        assertThat(nameAllocator.newName("foo")).isEqualTo("foo_");
        assertThat(nameAllocator.newName("foo")).isEqualTo("foo__");
    }

    @Test
    public void nameCollisionWithTag() {
        NameAllocator nameAllocator = new NameAllocator();
        assertThat(nameAllocator.newName("foo", 1)).isEqualTo("foo");
        assertThat(nameAllocator.newName("foo", 2)).isEqualTo("foo_");
        assertThat(nameAllocator.newName("foo", 3)).isEqualTo("foo__");
        assertThat(nameAllocator.get(1)).isEqualTo("foo");
        assertThat(nameAllocator.get(2)).isEqualTo("foo_");
        assertThat(nameAllocator.get(3)).isEqualTo("foo__");
    }

    @ParameterizedTest
    @MethodSource
    public void characterMapping(String suggestion, String expected) {
        NameAllocator nameAllocator = new NameAllocator();
        assertThat(nameAllocator.newName(suggestion)).isEqualTo(expected);
    }

    private static List<Arguments> characterMapping() {
        return List.of(
                Arguments.of("ab", "ab"),
                Arguments.of("1ab", "_1ab"),
                Arguments.of("&ab", "_ab"),
                Arguments.of("\uD83C\uDF7Aab", "_ab"),
                Arguments.of("\u200Bab", "_ab"),
                Arguments.of("a1b", "a1b"),
                Arguments.of("a-1", "a_1"),
                Arguments.of("a-b", "a_b"),
                Arguments.of("a\uD83C\uDF7Ab", "a_b"),
                Arguments.of("a\u200Bb", "ab"),
                Arguments.of("ab\u200B", "ab"),
                Arguments.of("\u200B1ab", "_1ab"),
                Arguments.of("\u200B\u200B", "__"),
                // U+E0001 requires a surrogate pair, exercising code-point iteration.
                Arguments.of("a\uDB40\uDC01b", "ab"));
    }

    @Test
    public void javaKeyword() {
        NameAllocator nameAllocator = new NameAllocator();
        assertThat(nameAllocator.newName("public", 1)).isEqualTo("public_");
        assertThat(nameAllocator.get(1)).isEqualTo("public_");
    }

    @Test
    public void nameCollisionAfterCharacterMapping() {
        NameAllocator nameAllocator = new NameAllocator();
        assertThat(nameAllocator.newName("f\u200Boo")).isEqualTo("foo");
        assertThat(nameAllocator.newName("foo")).isEqualTo("foo_");
        assertThat(nameAllocator.newName("foo\u200B")).isEqualTo("foo__");
        assertThat(nameAllocator.newName("f\uDB40\uDC01oo")).isEqualTo("foo___");
    }

    @Test
    public void tagReuseForbidden() {
        NameAllocator nameAllocator = new NameAllocator();
        nameAllocator.newName("foo", 1);
        assertThatThrownBy(() -> nameAllocator.newName("bar", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tag 1 cannot be used for both 'foo' and 'bar'");
    }

    @Test
    public void useBeforeAllocateForbidden() {
        NameAllocator nameAllocator = new NameAllocator();
        assertThatThrownBy(() -> nameAllocator.get(1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown tag: 1");
    }

    @Test
    public void cloneUsage() {
        NameAllocator outterAllocator = new NameAllocator();
        outterAllocator.newName("foo", 1);

        NameAllocator innerAllocator1 = outterAllocator.clone();
        assertThat(innerAllocator1.newName("bar", 2)).isEqualTo("bar");
        assertThat(innerAllocator1.newName("foo", 3)).isEqualTo("foo_");

        NameAllocator innerAllocator2 = outterAllocator.clone();
        assertThat(innerAllocator2.newName("foo", 2)).isEqualTo("foo_");
        assertThat(innerAllocator2.newName("bar", 3)).isEqualTo("bar");
    }
}
