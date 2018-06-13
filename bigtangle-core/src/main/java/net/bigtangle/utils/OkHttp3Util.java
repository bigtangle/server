/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.utils;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import net.bigtangle.core.Json;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkHttp3Util {

    public static String post(String url, byte[] b) throws Exception {
        System.out.println(url);
        OkHttpClient client = (new OkHttpClient.Builder()).connectTimeout(60, TimeUnit.MINUTES)
                .writeTimeout(60, TimeUnit.MINUTES).readTimeout(60, TimeUnit.MINUTES).build();
        RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream; charset=utf-8"), b);
        Request request = new Request.Builder().url(url).post(body).build();
        Response response = client.newCall(request).execute();
        try {
            String resp = response.body().string();
            checkResponse(resp);
            return resp;

        } finally {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
            // client.cache().close();
            response.close();
            response.body().close();
        }
    }

    public static byte[] post(String url, String s) throws Exception {
        OkHttpClient client = (new OkHttpClient.Builder()).connectTimeout(60, TimeUnit.MINUTES)
                .writeTimeout(60, TimeUnit.MINUTES).readTimeout(60, TimeUnit.MINUTES).build();
        RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream; charset=utf-8"), s);
        Request request = new Request.Builder().url(url).post(body).build();
        Response response = client.newCall(request).execute();
        try {
            return response.body().bytes();
        } finally {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
            // client.cache().close();
            response.close();
            response.body().close();
        }
    }

    public static String postString(String url, String s) throws Exception {
        OkHttpClient client = (new OkHttpClient.Builder()).connectTimeout(60, TimeUnit.MINUTES)
                .writeTimeout(60, TimeUnit.MINUTES).readTimeout(60, TimeUnit.MINUTES).build();
        RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream; charset=utf-8"), s);
        Request request = new Request.Builder().url(url).post(body).build();
        Response response = client.newCall(request).execute();
        try {
            String resp = response.body().string();
            checkResponse(resp);
            return resp;
        } finally {
            client.dispatcher().executorService().shutdown();
            client.connectionPool().evictAll();
            // client.cache().close();
            response.close();
            response.body().close();
        }
    }

    public static void checkResponse(String resp) throws JsonParseException, JsonMappingException, IOException {
        checkResponse(resp, 100);
    }

    public static void checkResponse(String resp, int code)
            throws JsonParseException, JsonMappingException, IOException {
        @SuppressWarnings("unchecked")
        HashMap<String, Object> result2 = Json.jsonmapper().readValue(resp, HashMap.class);
        if (result2.get("errorcode") != null) {
            int error = (Integer) result2.get("errorcode");
            if (error == 100)
                throw new RuntimeException("server erorr:" + result2.get("message"));
        }
    }

}
