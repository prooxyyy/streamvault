package me.proo0xy.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.StringReader;

@UtilityClass
@Slf4j
public class GsonUtil {

    @Getter
    private static final Gson gson = new Gson();

    public String parseJson(Object object) {
        return gson.toJson(object);
    }

    public String parseJsonWithEscapedSlashes(Object object) {
        return gson.toJson(object).replace("/", "\\/");
    }

    public <T> T unparseJson(String json, Class<T> clazz) {
        try {
            JsonReader jsonReader = new JsonReader(new StringReader(json));
            jsonReader.setStrictness(Strictness.STRICT);
            return gson.fromJson(jsonReader, clazz);
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }

    public <T> T unparseJson(JsonObject json, Class<T> clazz) {
        try {
            return gson.fromJson(json, clazz);
        } catch (Exception e) {
            log.error(e.getMessage());
            return null;
        }
    }
}
