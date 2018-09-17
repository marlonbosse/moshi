package com.squareup.moshi;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.Ignore;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeAnnotationsTest {
  @Test public void test() {
    Foo foo = new Foo();
    foo.list = Arrays.asList("A", "B");

    Moshi moshi = new Moshi.Builder()
        .add(String.class, Lowercase.class, new LowercaseStringAdapter())
        .add(String.class, BarPostFix.class, new BarPostFixStringAdapter())
        .add(ReversedListAdapter.FACTORY)
        .build();

    JsonAdapter<Foo> jsonAdapter = moshi.adapter(Foo.class);
    assertThat(jsonAdapter.toJson(foo)).isEqualTo("{\"list\":[\"b\",\"a\"]}");
  }

  @Test public void test2() {
    Toto toto = new Toto();
    toto.list = Arrays.asList("A", "B");

    Moshi moshi = new Moshi.Builder()
        .add(String.class, Lowercase.class, new LowercaseStringAdapter())
        .add(String.class, BarPostFix.class, new BarPostFixStringAdapter())
        .add(ReversedListAdapter.FACTORY)
        .build();

    JsonAdapter<Toto> jsonAdapter = moshi.adapter(Toto.class);
    assertThat(jsonAdapter.toJson(toto)).isEqualTo("{\"list\":[\"B\",\"A\"]}");
  }

  @Ignore("not implemented")
  @Test public void nonTypeAnnotationUse() {
    Moshi moshi = new Moshi.Builder()
        .add(String.class, Lowercase.class, new LowercaseStringAdapter())
        .add(ReversedListAdapter.FACTORY)
        .build();

    JsonAdapter<String> lowercaseStringAdapter = moshi.adapter(String.class, Lowercase.class);
    assertThat(lowercaseStringAdapter.toJson("A")).isEqualTo("\"a\"");

    JsonAdapter<List<String>> listAdapter =
        moshi.adapter(Types.newParameterizedType(List.class, String.class), Reversed.class);
    assertThat(listAdapter.toJson(Arrays.asList("A", "B"))).isEqualTo("[\"B\",\"A\"]");
  }

  @Ignore("not implemented")
  @Test public void testTypeAnnotationsWithTypeResolution() {
    Bar<String> bar = new Bar<>();
    bar.list = Arrays.asList("A", "B");

    Moshi moshi = new Moshi.Builder()
        .add(String.class, Lowercase.class, new LowercaseStringAdapter())
        .add(ReversedListAdapter.FACTORY)
        .build();

    JsonAdapter<Bar<String>> jsonAdapter = moshi.adapter(
        Types.newParameterizedTypeWithOwner(TypeAnnotationsTest.class, Bar.class, String.class));
    assertThat(jsonAdapter.toJson(bar)).isEqualTo("{\"list\":[\"b\",\"a\"]}");
  }

  @Ignore("not implemented")
  @Test public void testTypeAnnotationsWithTypeResolutionMax() {
    Bar<@Reversed String> bar = new Bar<>();
    bar.list = Arrays.asList("A", "B");

    Moshi moshi = new Moshi.Builder()
        .add(String.class, Lowercase.class, new LowercaseStringAdapter())
        .add(ReversedListAdapter.FACTORY)
        .build();

    JsonAdapter<Bar<String>> jsonAdapter = moshi.adapter(
        Types.newParameterizedTypeWithOwner(TypeAnnotationsTest.class, Bar.class, String.class));
    assertThat(jsonAdapter.toJson(bar)).isEqualTo("{\"list\":[\"b\",\"a\"]}");
  }

  static class Toto {
    @Reversed public List<String> list;
  }

  static class Foo {
    @Reversed List<@BarPostFix @Lowercase String> list;
  }

  static class Bar<T> {
    @Reversed List<@Lowercase T> list;
  }

  @JsonQualifier
  @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
  @Retention(RetentionPolicy.RUNTIME) @interface Lowercase {
  }

  @JsonQualifier
  @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
  @Retention(RetentionPolicy.RUNTIME) @interface BarPostFix {
  }

  static class LowercaseStringAdapter extends JsonAdapter<String> {
    @Override public String fromJson(JsonReader reader) throws IOException {
      return reader.nextString().toLowerCase(Locale.US);
    }

    @Override public void toJson(JsonWriter writer, String value) throws IOException {
      writer.value(value);
    }
  }

  static class BarPostFixStringAdapter extends JsonAdapter<String> {
    @Override public String fromJson(JsonReader reader) throws IOException {
      return reader.nextString() + "bar";
    }

    @Override public void toJson(JsonWriter writer, String value) throws IOException {
      writer.value(value);
    }
  }

  static class ReversedStringAdapter extends JsonAdapter<String> {
    @Override public String fromJson(JsonReader reader) throws IOException {
      return new StringBuilder(reader.nextString()).reverse().toString();
    }

    @Override public void toJson(JsonWriter writer, String value) throws IOException {
      writer.value(new StringBuilder(value).reverse().toString());
    }
  }

  @JsonQualifier
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) @interface Reversed {
  }

  static class ReversedListAdapter<T> extends JsonAdapter<List<T>> {
    static final JsonAdapter.Factory FACTORY = new JsonAdapter.Factory() {
      @Override
      public JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
        if (!(type instanceof ParameterizedType)) return null;
        if (Types.nextAnnotations(annotations, Reversed.class) == null) return null;

        ParameterizedType parameterizedType = (ParameterizedType) type;
        if (parameterizedType.getRawType() != List.class) return null;

        Type elementType = parameterizedType.getActualTypeArguments()[0];
        return new ReversedListAdapter<>(moshi.adapter(elementType));
      }
    };

    private final JsonAdapter<T> jsonAdapter;

    ReversedListAdapter(JsonAdapter<T> jsonAdapter) {
      this.jsonAdapter = jsonAdapter;
    }

    @Override public List<T> fromJson(JsonReader reader) throws IOException {
      List<T> result = new ArrayList<>();
      reader.beginArray();
      while (reader.hasNext()) {
        T value = jsonAdapter.fromJson(reader);
        result.add(value);
      }
      reader.endArray();
      Collections.reverse(result);
      return result;
    }

    @Override public void toJson(JsonWriter writer, List<T> values) throws IOException {
      writer.beginArray();
      for (int i = values.size() - 1; i >= 0; i--) {
        jsonAdapter.toJson(writer, values.get(i));
      }
      writer.endArray();
    }
  }
}
