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

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;

public final class TypeVariableName extends TypeName {
    private final String name;
    private final List<TypeName> bounds;

    private TypeVariableName(String name, List<TypeName> bounds) {
        this(name, bounds, new ArrayList<>());
    }

    private TypeVariableName(String name, List<TypeName> bounds, List<AnnotationSpec> annotations) {
        super(annotations);
        this.name = checkNotNull(name, "name == null");
        this.bounds = Util.immutableList(bounds);

        for (TypeName bound : this.bounds) {
            checkArgument(!bound.isPrimitive() && bound != VOID, "invalid bound: %s", bound);
        }
    }

    public String name() {
        return name;
    }

    public List<TypeName> bounds() {
        return bounds;
    }

    @Override
    public TypeVariableName annotated(List<AnnotationSpec> annotations) {
        return new TypeVariableName(name, bounds, annotations);
    }

    @Override
    public TypeName withoutAnnotations() {
        return new TypeVariableName(name, bounds);
    }

    public TypeVariableName withBounds(Type... bounds) {
        return withBounds(TypeName.list(bounds));
    }

    public TypeVariableName withBounds(TypeName... bounds) {
        return withBounds(Arrays.asList(bounds));
    }

    public TypeVariableName withBounds(List<? extends TypeName> bounds) {
        List<TypeName> newBounds = new ArrayList<>();
        newBounds.addAll(this.bounds);
        newBounds.addAll(bounds);
        return new TypeVariableName(name, newBounds, annotations());
    }

    @Override
    CodeWriter emit(CodeWriter out) throws IOException {
        emitAnnotations(out);
        return out.emitAndIndent(name);
    }

    private static TypeVariableName of(String name, List<TypeName> bounds) {
        // Strip java.lang.Object from bounds if it is present.
        List<TypeName> boundsNoObject = new ArrayList<>(bounds);
        boundsNoObject.remove(ClassName.OBJECT);
        return new TypeVariableName(name, Collections.unmodifiableList(boundsNoObject));
    }

    /** Returns type variable named {@code name} without bounds. */
    public static TypeVariableName get(String name) {
        return TypeVariableName.of(name, Collections.emptyList());
    }

    /** Returns type variable named {@code name} with {@code bounds}. */
    public static TypeVariableName get(String name, TypeName... bounds) {
        return TypeVariableName.of(name, Arrays.asList(bounds));
    }

    /** Returns type variable named {@code name} with {@code bounds}. */
    public static TypeVariableName get(String name, Type... bounds) {
        return TypeVariableName.of(name, TypeName.list(bounds));
    }

    /** Returns type variable equivalent to {@code mirror}. */
    public static TypeVariableName get(TypeVariable mirror) {
        return get((TypeParameterElement) mirror.asElement());
    }

    /**
     * Make a TypeVariableName for the given TypeMirror. This form is used internally to avoid
     * infinite recursion in cases like {@code Enum<E extends Enum<E>>}. When we encounter such a
     * thing, we will make a TypeVariableName without bounds and add that to the {@code typeVariables}
     * map before looking up the bounds. Then if we encounter this TypeVariable again while
     * constructing the bounds, we can just return it from the map. And, the code that put the entry
     * in {@code variables} will make sure that the bounds are filled in before returning.
     */
    static TypeVariableName get(TypeVariable mirror, Map<TypeParameterElement, TypeVariableName> typeVariables) {
        TypeParameterElement element = (TypeParameterElement) mirror.asElement();
        TypeVariableName typeVariableName = typeVariables.get(element);
        if (typeVariableName == null) {
            List<TypeName> bounds = new ArrayList<>();
            for (TypeMirror typeMirror : element.getBounds()) {
                typeVariableName = new TypeVariableName(element.getSimpleName().toString(), bounds);
                typeVariables.put(element, typeVariableName);
                bounds.add(TypeName.get(typeMirror, typeVariables));
            }
            bounds.remove(ClassName.OBJECT);
            typeVariableName = new TypeVariableName(element.getSimpleName().toString(), bounds);
            typeVariables.put(element, typeVariableName);
        }
        return typeVariableName;
    }

    /** Returns type variable equivalent to {@code element}. */
    public static TypeVariableName get(TypeParameterElement element) {
        String name = element.getSimpleName().toString();
        List<? extends TypeMirror> boundsMirrors = element.getBounds();

        List<TypeName> boundsTypeNames = new ArrayList<>();
        for (TypeMirror typeMirror : boundsMirrors) {
            boundsTypeNames.add(TypeName.get(typeMirror));
        }

        return TypeVariableName.of(name, boundsTypeNames);
    }

    /** Returns type variable equivalent to {@code type}. */
    public static TypeVariableName get(java.lang.reflect.TypeVariable<?> type) {
        return get(type, new LinkedHashMap<>());
    }

    /** @see #get(java.lang.reflect.TypeVariable, Map) */
    static TypeVariableName get(java.lang.reflect.TypeVariable<?> type, Map<Type, TypeVariableName> map) {
        TypeVariableName result = map.get(type);
        if (result == null) {
            List<TypeName> bounds = new ArrayList<>();
            List<TypeName> visibleBounds = Collections.unmodifiableList(bounds);
            result = new TypeVariableName(type.getName(), visibleBounds);
            map.put(type, result);
            for (Type bound : type.getBounds()) {
                bounds.add(TypeName.get(bound, map));
            }
            bounds.remove(ClassName.OBJECT);
        }
        return result;
    }
}
