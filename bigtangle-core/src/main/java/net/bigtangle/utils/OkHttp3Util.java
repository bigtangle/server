/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.utils;

import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkHttp3Util {

    public static String post(String url, byte[] b) throws Exception {
        OkHttpClient client = (new OkHttpClient.Builder()).connectTimeout(30, TimeUnit.MINUTES).build();
        RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream; charset=utf-8"), b);
        Request request = new Request.Builder().url(url).post(body).build();
        Response response = client.newCall(request).execute();
        try {
            return response.body().string();
        }
        finally {
            client.dispatcher().executorService().shutdown();   //清除并关闭线程池
            client.connectionPool().evictAll();                 //清除并关闭连接池
//            client.cache().close();                             //清除cache
            response.close();
            response.body().close();
        }
    }

    public static byte[] post(String url, String s) throws Exception {
        OkHttpClient client = (new OkHttpClient.Builder()).connectTimeout(30, TimeUnit.MINUTES).build();
        RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream; charset=utf-8"), s);
        Request request = new Request.Builder().url(url).post(body).build();
        Response response = client.newCall(request).execute();
        try {
            return response.body().bytes();
        }
        finally {
            client.dispatcher().executorService().shutdown();   //清除并关闭线程池
            client.connectionPool().evictAll();                 //清除并关闭连接池
//            client.cache().close();                             //清除cache
            response.close();
            response.body().close();
        }
    }

    public static String postString(String url, String s) throws Exception {
        OkHttpClient client = (new OkHttpClient.Builder()).connectTimeout(30, TimeUnit.MINUTES).build();
        RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream; charset=utf-8"), s);
        Request request = new Request.Builder().url(url).post(body).build();
        Response response = client.newCall(request).execute();
        try {
            return response.body().string();
        }
        finally {
            client.dispatcher().executorService().shutdown();   //清除并关闭线程池
            client.connectionPool().evictAll();                 //清除并关闭连接池
//            client.cache().close();                             //清除cache
            response.close();
            response.body().close();
        }
    }

}
