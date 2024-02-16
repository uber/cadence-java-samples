package com.uber.cadence.samples.spring.cadence;

import com.google.gson.TypeAdapterFactory;
import com.uber.cadence.converter.JsonDataConverter;
import java.time.ZonedDateTime;
import java.util.ServiceLoader;

// This singleton JsonDataConverter should be universally used in all components that require a
// JsonDataConverter. Specifically in Cadence's case, WorkflowClient uses Json serialize function
// and Worker uses deserialize function, therefore they have to match with one another.
public class CadenceDataConverter {
  public static JsonDataConverter cadenceJsonDataConverter() {
    return new JsonDataConverter(
        gsonBuilder -> {
          for (TypeAdapterFactory factory : ServiceLoader.load(TypeAdapterFactory.class)) {
            gsonBuilder.registerTypeAdapterFactory(factory);
          }
          return gsonBuilder.registerTypeAdapter(ZonedDateTime.class, new ZonedDatetimeConverter());
        });
  }
}
