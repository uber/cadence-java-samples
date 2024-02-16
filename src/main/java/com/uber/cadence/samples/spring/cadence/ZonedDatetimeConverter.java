package com.uber.cadence.samples.spring.cadence;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class ZonedDatetimeConverter
    implements JsonSerializer<ZonedDateTime>, JsonDeserializer<ZonedDateTime> {
  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

  @Override
  public JsonElement serialize(
      ZonedDateTime src, Type type, JsonSerializationContext jsonSerializationContext) {
    return new JsonPrimitive(FORMATTER.format(src));
  }

  @Override
  public ZonedDateTime deserialize(
      JsonElement json, Type type, JsonDeserializationContext jsonDeserializationContext)
      throws JsonParseException {
    return FORMATTER.parse(json.getAsString(), ZonedDateTime::from);
  }
}
