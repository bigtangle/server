package net.bigtangle.utils;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

//https://stackoverflow.com/questions/22490057/android-okhttp-with-basic-authentication
//https://github.com/square/okhttp/wiki/Recipes
public class BasicAuthInterceptor implements Interceptor {

    private String credentials;

    public BasicAuthInterceptor(String pubkey, String signHex, String contentHex) {
        this.credentials = pubkey + ":" + signHex + ":" + contentHex;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Request authenticatedRequest = request.newBuilder().header("Authorization", credentials).build();
        return chain.proceed(authenticatedRequest);
    }
}
