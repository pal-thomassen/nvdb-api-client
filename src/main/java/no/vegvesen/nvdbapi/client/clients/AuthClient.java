package no.vegvesen.nvdbapi.client.clients;

import com.google.gson.Gson;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.singletonMap;

class AuthClient extends AbstractJerseyClient {
    protected AuthClient(String baseUrl, HttpClient client) {
        super(baseUrl, client);
    }

    public Login login(String username, String password) {
        Gson gson = new Gson();
        HttpPost post = new HttpPost("/auth/login");
        post.setEntity(new StringEntity(gson.toJson(credentialsJson(username, password)), ContentType.APPLICATION_JSON));
        try ( ClassicHttpResponse response = getClient().execute(start(), post) ){
            Login.AuthTokens authTokens = gson.fromJson(EntityUtils.toString(response.getEntity()), Login.AuthTokens.class);
            return Login.success(authTokens);
        } catch (Exception e) {
            return Login.failed(e.getMessage());
        }
    }


    public Login refresh(String refreshToken) {
        Gson gson = new Gson();
        HttpPost post = new HttpPost("/auth/refresh");
        post.setEntity(new StringEntity(gson.toJson(refreshJson(refreshToken)), ContentType.APPLICATION_JSON));
        try ( ClassicHttpResponse response = getClient().execute(start(), post) ){
            Login.AuthTokens authTokens = gson.fromJson(EntityUtils.toString(response.getEntity()), Login.AuthTokens.class);
            return Login.success(authTokens);
        } catch (Exception e) {
            return Login.failed(e.getMessage());
        }
    }

    private Map<String, String> refreshJson(String refreshToken) {
        return singletonMap("refreshToken", refreshToken);
    }


    private Map<String, String> credentialsJson(String username, String password) {
        Map<String, String> credentials = new HashMap<>();
        credentials.put("username", username);
        credentials.put("password", password);
        return credentials;
    }
}
