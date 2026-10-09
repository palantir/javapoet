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
                Arguments.of("-ab", "_ab"),
                Arguments.of("\ud83c\udf7aab", "_ab"),
                Arguments.of("\u0000ab", "_ab"),
                Arguments.of("a\udb40\udc01b", "ab"),
                Arguments.of("a1b", "a1b"),
                Arguments.of("a-b", "a_b"),
                Arguments.of("a\ud83c\udf7ab", "a_b"),
                Arguments.of("a\u0000b", "ab"),
                Arguments.of("a\udb40\udc01b", "ab"));
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
