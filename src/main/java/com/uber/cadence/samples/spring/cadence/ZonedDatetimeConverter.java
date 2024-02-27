/*
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

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
