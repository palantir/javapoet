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

import org.junit.Test;

public class UtilTest {
    @Test
    public void characterLiteral() {
        assertThat(Util.characterLiteralWithoutSingleQuotes('a')).isEqualTo("a");
        assertThat(Util.characterLiteralWithoutSingleQuotes('b')).isEqualTo("b");
        assertThat(Util.characterLiteralWithoutSingleQuotes('c')).isEqualTo("c");
        assertThat(Util.characterLiteralWithoutSingleQuotes('%')).isEqualTo("%");
        // common escapes
        assertThat(Util.characterLiteralWithoutSingleQuotes('\b')).isEqualTo("\\b");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\t')).isEqualTo("\\t");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\n')).isEqualTo("\\n");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\f')).isEqualTo("\\f");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\r')).isEqualTo("\\r");
        assertThat(Util.characterLiteralWithoutSingleQuotes('"')).isEqualTo("\"");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\'')).isEqualTo("\\'");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\\')).isEqualTo("\\\\");
        // octal escapes
        assertThat(Util.characterLiteralWithoutSingleQuotes('\0')).isEqualTo("\\u0000");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\7')).isEqualTo("\\u0007");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\77')).isEqualTo("?");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\177')).isEqualTo("\\u007f");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\277')).isEqualTo("¿");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\377')).isEqualTo("ÿ");
        // unicode escapes
        assertThat(Util.characterLiteralWithoutSingleQuotes('\u0000')).isEqualTo("\\u0000");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\u0001')).isEqualTo("\\u0001");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\u0002')).isEqualTo("\\u0002");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\u20AC')).isEqualTo("€");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\u2603')).isEqualTo("☃");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\u2660')).isEqualTo("♠");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\u2663')).isEqualTo("♣");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\u2665')).isEqualTo("♥");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\u2666')).isEqualTo("♦");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\u2735')).isEqualTo("✵");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\u273A')).isEqualTo("✺");
        assertThat(Util.characterLiteralWithoutSingleQuotes('\uFF0F')).isEqualTo("／");
    }

    @Test
    public void stringLiteral() {
        stringLiteral("abc");
        stringLiteral("♦♥♠♣");
        stringLiteral("€\\t@\\t$", "€\t@\t$", " ");
        stringLiteral("abc();\\n\"\n  + \"def();", "abc();\ndef();", " ");
        stringLiteral("This is \\\"quoted\\\"!", "This is \"quoted\"!", " ");
        stringLiteral("e^{i\\\\pi}+1=0", "e^{i\\pi}+1=0", " ");
    }

    void stringLiteral(String string) {
        stringLiteral(string, string, " ");
    }

    void stringLiteral(String expected, String value, String indent) {
        assertThat(Util.stringLiteralWithDoubleQuotes(value, indent)).isEqualTo("\"" + expected + "\"");
    }
}
