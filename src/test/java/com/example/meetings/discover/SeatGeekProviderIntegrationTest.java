package com.example.meetings.discover;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.github.tomakehurst.wiremock.WireMockServer;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "app.discover.seatgeek.base-url=http://localhost:8089/mock-seatgeek",
        "app.discover.seatgeek.client-id=mock-testing-id",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
@DisplayName("SeatGeek Provider Live Integration Mapping Test")
class SeatGeekProviderIntegrationTest {

    private static final WireMockServer wireMockServer = new WireMockServer(0);

    @Autowired
    private SeatGeekProvider provider;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMockServer.start();
        registry.add("app.discover.seatgeek.base-url", wireMockServer::baseUrl);
        registry.add("app.discover.seatgeek.client-id", () -> "mock-client-id");
    }

    @Test
    @DisplayName("search - Should correctly parse valid JSON payload from SeatGeek API")
    void search_ValidResponse_ParsesCorrectly() {
        String mockJsonResponse = """
                {
                  "events": [
                    {
                      "id": 999111,
                      "title": "Benfica vs Porto",
                      "short_title": "Benfica v Porto",
                      "datetime_utc": "2026-06-25T19:45:00",
                      "url": "https://seatgeek.com/benfica-porto-tickets",
                      "description": "Classic Portuguese football derby matches",
                      "venue": {
                        "name": "Estádio da Luz"
                      }
                    }
                  ]
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/events"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockJsonResponse)
                        .withStatus(200)));

        List<DiscoveredEvent> events = provider.search("Benfica");

        assertEquals(1, events.size());
        DiscoveredEvent event = events.get(0);
        assertEquals("999111", event.externalId());
        assertEquals("Benfica vs Porto", event.title());
        assertEquals("Estádio da Luz", event.venue());
        assertEquals("2026-06-25T19:45:00Z", event.start().toString()); // SeatGeek UTC parse validation
    }
}