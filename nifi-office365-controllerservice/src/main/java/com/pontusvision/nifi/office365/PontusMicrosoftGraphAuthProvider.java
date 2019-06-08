package com.pontusvision.nifi.office365;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpException;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

import com.google.gson.stream.JsonReader;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.http.IHttpRequest;



public class PontusMicrosoftGraphAuthProvider implements IAuthenticationProvider {

    private static final RequestConfig REQUEST_CONFIG = RequestConfig.custom()
            .setConnectionRequestTimeout(30000)
            .setConnectTimeout(30000)
            .setSocketTimeout(30000)
            .build();

    private static PontusMicrosoftGraphAuthProvider me;

    private final Object syncObj = new Object();

    private String mTenantId;
    private String mClientId;
    private String mClientSecret;
    private String mGrantType;
    private String mScope;
    public String mAccessToken;

    /**
     * Singleton constructor.
     */
    private PontusMicrosoftGraphAuthProvider(String tenantId, String clientId, String clientSecret,
                                                  String grantType, String scope) {
        mTenantId = tenantId;
        mClientId = clientId;
        mClientSecret = clientSecret;
        mGrantType = grantType;
        mScope = scope;
    }

    /**
     * Returns an instance of authenticator.
     */
    public static synchronized PontusMicrosoftGraphAuthProvider getInstance(String tenantId, String clientId, String clientSecret,
                                                                            String grantType, String scope) {
        if (me == null || !me.mTenantId.equals(tenantId)) {
            me = new PontusMicrosoftGraphAuthProvider(tenantId, clientId, clientSecret, grantType, scope);
        }

        return me;
    }

    /**
     * Authenticates the request.
     */
    @Override
    public void authenticateRequest(IHttpRequest request) {
        try {
            synchronized (syncObj) {
                if (mAccessToken == null) {
                    loadAccessToken();
                }
            }

            request.addHeader("Authorization", mAccessToken);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Refresh the access token.
     */
    public void refreshToken() {
        synchronized (syncObj) {
            mAccessToken = null;
        }
    }

    /*
     * Returns the access token for a company.
     */
    private void loadAccessToken() throws Exception {
        final String url = String.format("https://login.microsoftonline.com/%s/oauth2/v2.0/token", mTenantId);

        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>(4);
        params.add(new BasicNameValuePair("grant_type", mGrantType));
        params.add(new BasicNameValuePair("client_id", mClientId));
        params.add(new BasicNameValuePair("client_secret", mClientSecret));
        params.add(new BasicNameValuePair("scope", mScope));

        HttpPost httpPost = new HttpPost(url);
        httpPost.setConfig(REQUEST_CONFIG);
        httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded");
        httpPost.setEntity(new UrlEncodedFormEntity(params, StandardCharsets.UTF_8));

        // Open URL and executes request
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            try (CloseableHttpResponse httpResponse = httpClient.execute(httpPost)) {
                StatusLine statusLine = httpResponse.getStatusLine();
                int httpCode = statusLine.getStatusCode();

                if (httpCode >= 300) {
                    String httpMessage = statusLine.getReasonPhrase();
                    String details = IOUtils.toString(httpResponse.getEntity().getContent(), StandardCharsets.UTF_8);

                    throw new HttpException(httpMessage + details);
                }

                try (InputStream is = httpResponse.getEntity().getContent()) {
                    String tokenType = null;
                    String accessToken = null;

                    try (JsonReader reader = new JsonReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        for (reader.beginObject(); reader.hasNext();) {
                            switch (reader.nextName()) {
                                case "token_type": tokenType = reader.nextString(); break;
                                case "access_token": accessToken = reader.nextString(); break;
                                default: reader.skipValue();
                            }
                        }
                        reader.endObject();
                    }

                    mAccessToken = tokenType + " " + accessToken;
                }
            }
        }
    }
}