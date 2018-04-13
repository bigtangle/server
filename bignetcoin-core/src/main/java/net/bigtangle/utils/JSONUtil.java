package net.bigtangle.utils;

import net.bigtangle.core.Json;

public class JSONUtil {

    public static <T> T toBean(String jsonStr, Class<T> clazz) throws Exception {
        T object = Json.jsonmapper().readValue(jsonStr, clazz);
        return object;
    }

    public static String toJSONString(Object object) throws Exception {
        String jsonStr = Json.jsonmapper().writeValueAsString(object);
        return jsonStr;
    }
}
