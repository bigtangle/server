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
import net.bigtangle.core.Utils;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkHttp3Util {

    private static final Logger logger = LoggerFactory.getLogger(OkHttp3Util.class);
    private static long timeoutMinute = 6;
    private static OkHttpClient client = null;
    public static String pubkey;
    public static String signHex;
    public static String contentHex;

    public static String post(String url, byte[] b) throws Exception {
        logger.debug(url);
        OkHttpClient client = getOkHttpClientSafe(pubkey, signHex, contentHex);
        RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream; charset=utf-8"), b);
        Request request = new Request.Builder().url(url).post(body).build();
        Response response = client.newCall(request).execute();
        try {
            String resp = response.body().string();
            // logger.debug(resp);
            checkResponse(resp);
            return resp;

        } finally {
            // client.cache().close();
            response.close();
            response.body().close();
        }
    }

    @SuppressWarnings("unchecked")
    public static byte[] post(String url, String s) throws Exception {
        logger.debug("url : " + url);
        OkHttpClient client = getOkHttpClientSafe(pubkey, signHex, contentHex);
        RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream; charset=utf-8"), s);
        Request request = new Request.Builder().url(url).post(body).build();
        Response response = client.newCall(request).execute();
        try {
            // return response.body().bytes();
            String resp = response.body().string();
            checkResponse(resp);
            HashMap<String, Object> result = Json.jsonmapper().readValue(resp, HashMap.class);
            String dataHex = (String) result.get("dataHex");
            if (dataHex != null) {
                return Utils.HEX.decode(dataHex);
            } else {
                return null;
            }
        } finally {
            // client.cache().close();
            response.close();
            response.body().close();
        }
    }

    public static String postString(String url, String s) throws Exception {
        OkHttpClient client = getOkHttpClientSafe(pubkey, signHex, contentHex);
        RequestBody body = RequestBody.create(MediaType.parse("application/octet-stream; charset=utf-8"), s);
        Request request = new Request.Builder().url(url).post(body).build();
        Response response = client.newCall(request).execute();
        try {
            String resp = response.body().string();
            checkResponse(resp);
            return resp;
        } finally {
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
            if (error > 0) {
                if (result2.get("message") == null) {
                    throw new RuntimeException("Server Error: " + error);
                } else {

                    throw new RuntimeException("Server Error: " + result2.get("message"));
                }
            }
        }
    }

    private static OkHttpClient getOkHttpClient() {
        if (client == null)
            client = getUnsafeOkHttpClient();
        return client;

    }

    private static OkHttpClient getOkHttpClientSafe(String pubkey, String signHex, String contentHex) {
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(timeoutMinute, TimeUnit.MINUTES)
                .writeTimeout(timeoutMinute, TimeUnit.MINUTES).readTimeout(timeoutMinute, TimeUnit.MINUTES)
                .addInterceptor(new BasicAuthInterceptor(pubkey, signHex, contentHex)).build();
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
                    }).connectTimeout(timeoutMinute, TimeUnit.MINUTES).writeTimeout(timeoutMinute, TimeUnit.MINUTES)
                    .readTimeout(timeoutMinute, TimeUnit.MINUTES).build();

            return client;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
