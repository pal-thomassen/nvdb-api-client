/*
 * Copyright (c) 2015-2017, Statens vegvesen
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.vegvesen.nvdbapi.client.clients;

import no.vegvesen.nvdbapi.client.ProxyConfig;
import no.vegvesen.nvdbapi.client.model.datakatalog.Datakatalog;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.util.Arrays.asList;
import static java.util.Objects.isNull;

public final class ClientFactory implements AutoCloseable {
    private final HttpHost baseUrl;

    private static final String apiRevision = "application/vnd.vegvesen.nvdb-v3-rev1+json";

    private Datakatalog datakatalog;
    private List<AbstractJerseyClient> clients;
    private final CloseableHttpClient httpclient;
    private boolean isClosed;
    private final Logger debugLogger;
    private Login.AuthTokens authTokens;

    /**
     * @param baseUrl - what base url to use. For production: https://apilesv3.atlas.vegvesen.no
     * @param xClientName - a name describing/name of your consumer application.
     */
    public ClientFactory(String baseUrl, String xClientName) {
        this(baseUrl, xClientName, (ProxyConfig) null);
    }

    /**
     * @param baseUrl - what base url to use. For production: https://apilesv3.atlas.vegvesen.no
     * @param xClientName - a name describing/name of your consumer application.
     * @param proxyConfig - Config if traffic have to go through proxy.
     */
    public ClientFactory(String baseUrl, String xClientName, ProxyConfig proxyConfig) {
        this(baseUrl, xClientName, null, proxyConfig);
    }

    /**
     * @param baseUrl - what base url to use. For production: https://apilesv3.atlas.vegvesen.no
     * @param xClientName - a name describing/name of your consumer application.
     * @param xSession - something identifying this session. Used to tag a sequence of requests, such that
     *                 if there are several instances that have the same xClientName it is possible to tell
     *                 the requests of each instance apart.
     *                 Use a uuid or something similar. not something that can identify a user, like username or email.
     * @param proxyConfig - Config if traffic have to go through proxy.
     */
    public ClientFactory(String baseUrl, String xClientName, String xSession, ProxyConfig proxyConfig) {
        this.baseUrl = new HttpHost(baseUrl);
        this.debugLogger = LoggerFactory.getLogger("no.vegvesen.nvdbapi.Client");
        this.clients = new ArrayList<>();
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        HttpClientBuilder builder = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultHeaders(
                        asList(
                                new BasicHeader("X-Client", xClientName),
                                new BasicHeader("X-Client-Session", getOrCreateSessionId()),
                                new BasicHeader(HttpHeaders.ACCEPT, apiRevision)
                        )
                )
                .setUserAgent(getUserAgent());
        if(proxyConfig != null) {
            HttpHost proxy = new HttpHost(proxyConfig.getUrl());
            builder.setProxy(proxy);
            if(proxyConfig.hasCredentials()) {
                BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
                credsProvider.setCredentials(
                        new AuthScope(proxy),
                        new UsernamePasswordCredentials(proxyConfig.getUsername(), proxyConfig.getPassword().toCharArray()));
            }
        }
        builder.addRequestInterceptorFirst((httpRequest, entityDetails, httpContext) -> {
            if(authTokens != null) {
                httpRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + authTokens.idToken);
            }
        });
        httpclient = builder.build();
    }

    private String getUserAgent() {
        return "nvdb-api-client-" + getClientVersion();
    }

    public boolean isClosed() {
        return isClosed;
    }

    /**
     * Authenticate with username and password.
     * If successful the {@code AuthTokens} recieved is used in followinf calls.
     * @param username -
     * @param password -
     * @return {@code Login} containing either {@code AuthTokens} if successful or {@code Failure} if not
     */
    public Login login(String username, String password) {
        try {
            AuthClient client = getAuthClient();
            Login login = client.login(username, password);
            if(login.isSuccessful()) {
                this.authTokens = login.authTokens;
            }
            return login;
        } catch (Exception e) {
            debugLogger.error("Login failed", e);
            return Login.failed(e.getMessage());
        }
    }

    /**
     * clear the ClientFactory's auth tokens.
     */
    public void logout() {
        this.authTokens = null;
    }

    private AuthClient getAuthClient() {
        return clients.stream()
            .filter(c -> c.getClass().equals(AuthClient.class))
            .map(AuthClient.class::cast)
            .findFirst()
            .orElseGet(() -> {
                AuthClient client = new AuthClient(baseUrl, httpclient);
                clients.add(client);
                return client;
            });
    }

    /**
     * Use an existing refresh token to authenticate.
     * @param refreshToken from a previous session
     * @return {@code Login} containing either {@code AuthTokens} if successful or {@code Failure} if not
     */
    public Login refresh(String refreshToken) {
        try {
            AuthClient client = getAuthClient();
            Login refresh = client.refresh(refreshToken);
            if(refresh.isSuccessful()) {
                this.authTokens = refresh.authTokens;
            }
            return refresh;
        } catch (Exception e) {
            debugLogger.error("Login failed", e);
            return Login.failed(e.getMessage());
        }
    }

    /**
     * Refresh authentication using internal {@code AuthTokens}.
     * @return {@code Login} containing either {@code AuthTokens} if successful or {@code Failure} if not
     */
    public Login refresh() {
        if(isNull(this.authTokens)) {
            throw new IllegalStateException("Tried to refresh without existing AuthTokens");
        }

        return refresh(this.authTokens.refreshToken);
    }

    public RoadNetClient createRoadNetService() {
        assertIsOpen();
        RoadNetClient c = new RoadNetClient(baseUrl, httpclient);
        clients.add(c);
        return c;
    }

    public SegmentedRoadNetClient createSegmentedRoadNetService() {
        assertIsOpen();
        SegmentedRoadNetClient c = new SegmentedRoadNetClient(baseUrl, httpclient);
        clients.add(c);
        return c;
    }

    public RoadNetRouteClient createRoadNetRouteClient() {
        assertIsOpen();
        RoadNetRouteClient c = new RoadNetRouteClient(baseUrl, httpclient);
        clients.add(c);
        return c;
    }

    private void assertIsOpen() {
        if (isClosed) {
            throw new IllegalStateException("Client factory is closed! Create new instance to continue.");
        }
    }

    public DatakatalogClient createDatakatalogClient() {
        assertIsOpen();
        DatakatalogClient c = new DatakatalogClient(baseUrl, httpclient);
        clients.add(c);
        return c;
    }

    public Datakatalog getDatakatalog() {
        if (datakatalog == null) {
            datakatalog = createDatakatalogClient().getDatakalog();
        }
        return datakatalog;
    }

    public AreaClient createAreaClient() {
        assertIsOpen();
        AreaClient c = new AreaClient(baseUrl, httpclient);
        clients.add(c);
        return c;
    }

    public RoadObjectClient createRoadObjectClient() {
        assertIsOpen();
        Datakatalog datakatalog = getDatakatalog();
        RoadObjectClient c =
            new RoadObjectClient(
                baseUrl,
                    httpclient,
                datakatalog);
        clients.add(c);
        return c;
    }

    public PositionClient createPlacementClient() {
        assertIsOpen();
        PositionClient c = new PositionClient(baseUrl, httpclient);
        clients.add(c);
        return c;
    }

    public RoadPlacementClient createRoadPlacementClient() {
        assertIsOpen();
        RoadPlacementClient c = new RoadPlacementClient(baseUrl, httpclient);
        clients.add(c);
        return c;
    }

    public StatusClient createStatusClient() {
        assertIsOpen();
        StatusClient c = new StatusClient(baseUrl, httpclient);
        clients.add(c);
        return c;
    }

    public TransactionsClient createTransactionsClient(){
        assertIsOpen();
        TransactionsClient c = new TransactionsClient(baseUrl, httpclient);
        clients.add(c);
        return c;
    }

    @Override
    public void close() throws Exception {
        httpclient.close();
        isClosed = true;
    }

    private String getClientVersion() {
        try {
            Enumeration<URL> resources = getClass().getClassLoader()
                .getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                Manifest manifest = new Manifest(resources.nextElement().openStream());
                Attributes attributes = manifest.getMainAttributes();
                if("nvdb-api-client".equals(attributes.getValue("Implementation-Title"))) {
                    return attributes.getValue("Implementation-Version");
                }
            }
        } catch (IOException E) { /* ignore */ }
        return "unknown";
    }

    private String getOrCreateSessionId() {
        try {
            String userHome = System.getProperty("user.home");
            File dotFolder = new File(userHome, ".nvdb-api-read-v3");
            if(!dotFolder.exists()) {
                dotFolder.mkdir();
            }
            File sessionIdFile = new File(dotFolder, "session");
            if(sessionIdFile.exists()) {
                return Files.readAllLines(sessionIdFile.toPath(), StandardCharsets.UTF_8).get(0);
            } else {
                String sessionId = UUID.randomUUID().toString();
                Files.write(sessionIdFile.toPath(), sessionId.getBytes(StandardCharsets.UTF_8), CREATE);
                return sessionId;
            }
        } catch (IOException e) {
            return UUID.randomUUID().toString();
        }
    }
}
