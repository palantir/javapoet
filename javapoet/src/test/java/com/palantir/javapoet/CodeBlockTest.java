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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public final class CodeBlockTest {
    @Test
    public void equalsAndHashCode() {
        CodeBlock a = CodeBlock.builder().build();
        CodeBlock b = CodeBlock.builder().build();
        assertThat(a.equals(b)).isTrue();
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        a = CodeBlock.builder().add("$L", "taco").build();
        b = CodeBlock.builder().add("$L", "taco").build();
        assertThat(a.equals(b)).isTrue();
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    public void of() {
        CodeBlock a = CodeBlock.of("$L taco", "delicious");
        assertThat(a.toString()).isEqualTo("delicious taco");
    }

    @Test
    public void isEmpty() {
        assertThat(CodeBlock.builder().isEmpty()).isTrue();
        assertThat(CodeBlock.builder().add("").isEmpty()).isTrue();
        assertThat(CodeBlock.builder().add(" ").isEmpty()).isFalse();
    }

    @Test
    public void indentCannotBeIndexed() {
        assertThatThrownBy(() -> CodeBlock.builder().add("$1>", "taco").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("$$, $>, $<, $[, $], $W, and $Z may not have an index");
    }

    @Test
    public void deindentCannotBeIndexed() {
        assertThatThrownBy(() -> CodeBlock.builder().add("$1<", "taco").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("$$, $>, $<, $[, $], $W, and $Z may not have an index");
    }

    @Test
    public void dollarSignEscapeCannotBeIndexed() {
        assertThatThrownBy(() -> CodeBlock.builder().add("$1$", "taco").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("$$, $>, $<, $[, $], $W, and $Z may not have an index");
    }

    @Test
    public void statementBeginningCannotBeIndexed() {
        assertThatThrownBy(() -> CodeBlock.builder().add("$1[", "taco").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("$$, $>, $<, $[, $], $W, and $Z may not have an index");
    }

    @Test
    public void statementEndingCannotBeIndexed() {
        assertThatThrownBy(() -> CodeBlock.builder().add("$1]", "taco").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("$$, $>, $<, $[, $], $W, and $Z may not have an index");
    }

    @Test
    public void nameFormatCanBeIndexed() {
        CodeBlock block = CodeBlock.builder().add("$1N", "taco").build();
        assertThat(block.toString()).isEqualTo("taco");
    }

    @Test
    public void literalFormatCanBeIndexed() {
        CodeBlock block = CodeBlock.builder().add("$1L", "taco").build();
        assertThat(block.toString()).isEqualTo("taco");
    }

    @Test
    public void stringFormatCanBeIndexed() {
        CodeBlock block = CodeBlock.builder().add("$1S", "taco").build();
        assertThat(block.toString()).isEqualTo("\"taco\"");
    }

    @Test
    public void typeFormatCanBeIndexed() {
        CodeBlock block = CodeBlock.builder().add("$1T", String.class).build();
        assertThat(block.toString()).isEqualTo("java.lang.String");
    }

    @Test
    public void simpleNamedArgument() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("text", "taco");
        CodeBlock block = CodeBlock.builder().addNamed("$text:S", map).build();
        assertThat(block.toString()).isEqualTo("\"taco\"");
    }

    @Test
    public void repeatedNamedArgument() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("text", "tacos");
        CodeBlock block = CodeBlock.builder()
                .addNamed("\"I like \" + $text:S + \". Do you like \" + $text:S + \"?\"", map)
                .build();
        assertThat(block.toString()).isEqualTo("\"I like \" + \"tacos\" + \". Do you like \" + \"tacos\" + \"?\"");
    }

    @Test
    public void namedAndNoArgFormat() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("text", "tacos");
        CodeBlock block =
                CodeBlock.builder().addNamed("$>\n$text:L for $$3.50", map).build();
        assertThat(block.toString()).isEqualTo("\n  tacos for $3.50");
    }

    @Test
    public void missingNamedArgument() {
        Map<String, Object> map = new LinkedHashMap<>();
        assertThatThrownBy(() -> CodeBlock.builder().addNamed("$text:S", map).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Missing named argument for $text");
    }

    @Test
    public void lowerCaseNamed() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("Text", "tacos");
        assertThatThrownBy(() -> CodeBlock.builder().addNamed("$Text:S", map).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("argument 'Text' must start with a lowercase character");
    }

    @Test
    public void multipleNamedArguments() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("pipe", System.class);
        map.put("text", "tacos");

        CodeBlock block = CodeBlock.builder()
                .addNamed("$pipe:T.out.println(\"Let's eat some $text:L\");", map)
                .build();

        assertThat(block.toString()).isEqualTo("java.lang.System.out.println(\"Let's eat some tacos\");");
    }

    @Test
    public void namedNewline() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("clazz", Integer.class);
        CodeBlock block = CodeBlock.builder().addNamed("$clazz:T\n", map).build();
        assertThat(block.toString()).isEqualTo("java.lang.Integer\n");
    }

    @Test
    public void danglingNamed() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("clazz", Integer.class);
        assertThatThrownBy(() -> CodeBlock.builder().addNamed("$clazz:T$", map).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dangling $ at end");
    }

    @Test
    public void indexTooHigh() {
        assertThatThrownBy(() -> CodeBlock.builder().add("$2T", String.class).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("index 2 for '$2T' not in range (received 1 arguments)");
    }

    @Test
    public void indexIsZero() {
        assertThatThrownBy(() -> CodeBlock.builder().add("$0T", String.class).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("index 0 for '$0T' not in range (received 1 arguments)");
    }

    @Test
    public void indexIsNegative() {
        assertThatThrownBy(() -> CodeBlock.builder().add("$-1T", String.class).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid format string: '$-1T'");
    }

    @Test
    public void indexWithoutFormatType() {
        assertThatThrownBy(() -> CodeBlock.builder().add("$1", String.class).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dangling format characters in '$1'");
    }

    @Test
    public void indexWithoutFormatTypeNotAtStringEnd() {
        assertThatThrownBy(
                        () -> CodeBlock.builder().add("$1 taco", String.class).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid format string: '$1 taco'");
    }

    @Test
    public void indexButNoArguments() {
        assertThatThrownBy(() -> CodeBlock.builder().add("$1T").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("index 1 for '$1T' not in range (received 0 arguments)");
    }

    @Test
    public void formatIndicatorAlone() {
        assertThatThrownBy(() -> CodeBlock.builder().add("$", String.class).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dangling format characters in '$'");
    }

    @Test
    public void formatIndicatorWithoutIndexOrFormatType() {
        assertThatThrownBy(() ->
                        CodeBlock.builder().add("$ tacoString", String.class).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid format string: '$ tacoString'");
    }

    @Test
    public void sameIndexCanBeUsedWithDifferentFormats() {
        CodeBlock block = CodeBlock.builder()
                .add("$1T.out.println($1S)", ClassName.get(System.class))
                .build();
        assertThat(block.toString()).isEqualTo("java.lang.System.out.println(\"java.lang.System\")");
    }

    @Test
    public void tooManyStatementEnters() {
        CodeBlock codeBlock = CodeBlock.builder().add("$[$[").build();
        // We can't report this error until rendering type because code blocks might be composed.
        assertThatThrownBy(codeBlock::toString)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("statement enter $[ followed by statement enter $[");
    }

    @Test
    public void statementExitWithoutStatementEnter() {
        CodeBlock codeBlock = CodeBlock.builder().add("$]").build();
        // We can't report this error until rendering type because code blocks might be composed.
        assertThatThrownBy(codeBlock::toString)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("statement exit $] has no matching statement enter $[");
    }

    @Test
    public void join() {
        List<CodeBlock> codeBlocks = new ArrayList<>();
        codeBlocks.add(CodeBlock.of("$S", "hello"));
        codeBlocks.add(CodeBlock.of("$T", ClassName.get("world", "World")));
        codeBlocks.add(CodeBlock.of("need tacos"));

        CodeBlock joined = CodeBlock.join(codeBlocks, " || ");
        assertThat(joined.toString()).isEqualTo("\"hello\" || world.World || need tacos");
    }

    @Test
    public void joining() {
        List<CodeBlock> codeBlocks = new ArrayList<>();
        codeBlocks.add(CodeBlock.of("$S", "hello"));
        codeBlocks.add(CodeBlock.of("$T", ClassName.get("world", "World")));
        codeBlocks.add(CodeBlock.of("need tacos"));

        CodeBlock joined = codeBlocks.stream().collect(CodeBlock.joining(" || "));
        assertThat(joined.toString()).isEqualTo("\"hello\" || world.World || need tacos");
    }

    @Test
    public void joiningSingle() {
        List<CodeBlock> codeBlocks = new ArrayList<>();
        codeBlocks.add(CodeBlock.of("$S", "hello"));

        CodeBlock joined = codeBlocks.stream().collect(CodeBlock.joining(" || "));
        assertThat(joined.toString()).isEqualTo("\"hello\"");
    }

    @Test
    public void joiningWithPrefixAndSuffix() {
        List<CodeBlock> codeBlocks = new ArrayList<>();
        codeBlocks.add(CodeBlock.of("$S", "hello"));
        codeBlocks.add(CodeBlock.of("$T", ClassName.get("world", "World")));
        codeBlocks.add(CodeBlock.of("need tacos"));

        CodeBlock joined = codeBlocks.stream().collect(CodeBlock.joining(" || ", "start {", "} end"));
        assertThat(joined.toString()).isEqualTo("start {\"hello\" || world.World || need tacos} end");
    }

    @Test
    public void tooManyArguments() {
        assertThatThrownBy(() -> CodeBlock.of("test", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unused arguments: expected %s, received %s", 0, 1);
        assertThatThrownBy(() -> CodeBlock.of("test", 1, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unused arguments: expected %s, received %s", 0, 2);
        assertThatThrownBy(() -> CodeBlock.of("test $L", 2, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unused arguments: expected %s, received %s", 1, 2);
    }

    @Test
    public void clear() {
        CodeBlock block =
                CodeBlock.builder().addStatement("$S", "Test string").clear().build();

        assertThat(block.toString()).isEmpty();
    }
}
