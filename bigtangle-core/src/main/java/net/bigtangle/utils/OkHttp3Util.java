/*******************************************************************************
 *  Copyright   2018  Inasset GmbH. 
 *  
 *******************************************************************************/
package net.bigtangle.utils;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import net.bigtangle.core.Json;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkHttp3Util {

    private static final Logger logger = LoggerFactory.getLogger(OkHttp3Util.class);
    
    public static String post(String url, byte[] b) throws Exception {
        logger.debug( url);
        OkHttpClient client = getOkHttpClient();
        RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream; charset=utf-8"), b);
        Request request = new Request.Builder().url(url).post(body).build();
        Response response = client.newCall(request).execute();
        try {
            String resp = response.body().string();
            logger.debug( resp);
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
        logger.debug( url);
        OkHttpClient client = getOkHttpClient();
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
        OkHttpClient client = getOkHttpClient();
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

    private static OkHttpClient getOkHttpClient() {
        
        return getUnsafeOkHttpClient();
    }
    private static OkHttpClient getOkHttpClientSafe() {
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(60, TimeUnit.MINUTES)
                .writeTimeout(60, TimeUnit.MINUTES).readTimeout(60, TimeUnit.MINUTES).build();
        return client;
    }
    private static OkHttpClient getUnsafeOkHttpClient() {
        try {

            X509TrustManager tr = new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType)
                        throws CertificateException {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType)
                        throws CertificateException {
                }
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
                
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[] { tr }, null);
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient client = new OkHttpClient.Builder().sslSocketFactory(sslSocketFactory, tr)
                    .hostnameVerifier(new HostnameVerifier() {
                        @Override
                        public boolean verify(String hostname, SSLSession session) {
                            return true;
                        }
                    }).connectTimeout(60, TimeUnit.MINUTES).writeTimeout(60, TimeUnit.MINUTES)
                    .readTimeout(60, TimeUnit.MINUTES).build();

            return client;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
