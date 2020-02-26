package no.vegvesen.nvdbapi.client.clients;

import javax.ws.rs.ProcessingException;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import no.vegvesen.nvdbapi.client.ClientConfiguration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClientConfigurationTest {

    private static WireMockServer wireMockServer;

    @BeforeAll
    public static void setUp() {
        wireMockServer = new WireMockServer(options().port(8089));
        wireMockServer.start();
    }

    @AfterAll
    public static void cleanUp() {
        wireMockServer.stop();
    }

    @Test
    public void shouldHonorReadTimeout() {
        configureFor("localhost", wireMockServer.port());
        int delayInMillis = 200;
        stubFor(get(urlEqualTo("/vegobjekttyper/versjon")).willReturn(
                aResponse()
                        .withStatus(200)
                        .withFixedDelay(delayInMillis)));

        ClientFactory clientFactory = new ClientFactory(wireMockServer.baseUrl(),
                "nvdbapi-client-test", new ClientConfiguration(delayInMillis - 100, delayInMillis * 10));
        Exception exception = Assertions.assertThrows(ProcessingException.class, clientFactory::createAreaClient);
        assertTrue(exception.getMessage().contains("Timeout"));
    }
}
