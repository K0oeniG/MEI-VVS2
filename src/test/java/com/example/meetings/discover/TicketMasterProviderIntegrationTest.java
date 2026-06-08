package com.example.meetings.discover;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("Ticketmaster Provider Live Integration Mapping Test")
class TicketmasterProviderIntegrationTest {

    private static final WireMockServer wireMockServer = new WireMockServer(0); // Blazing fast random port

    @Autowired
    private TicketmasterProvider provider;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMockServer.start();
        // Redirect the provider to look at our local mock server instead of the real
        // internet
        registry.add("app.discover.ticketmaster.base-url", wireMockServer::baseUrl);
        registry.add("app.discover.ticketmaster.api-key", () -> "mock-api-key");
    }

    @Test
    @DisplayName("search - Should correctly parse a valid JSON payload from Ticketmaster")
    void search_ValidResponse_ParsesCorrectly() {
        // Arrange: Fake the response Ticketmaster would give us
        String mockJsonResponse = """
                {
                  "_embedded": {
                    "events": [
                      {
                        "id": "tm-123",
                        "name": "Rock in Rio Lisboa",
                        "url": "https://ticketmaster.com/rock-in-rio",
                        "info": "Massive music festival",
                        "dates": {
                          "start": { "dateTime": "2026-06-20T20:00:00Z" }
                        },
                        "_embedded": {
                          "venues": [{ "name": "Parque da Bela Vista" }]
                        }
                      }
                    ]
                  }
                }
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/events.json"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockJsonResponse)
                        .withStatus(200)));

        // Execute
        List<DiscoveredEvent> events = provider.search("Rock");

        // Assert: Verify mapping
        assertEquals(1, events.size());
        DiscoveredEvent event = events.get(0);
        assertEquals("tm-123", event.externalId());
        assertEquals("Rock in Rio Lisboa", event.title());
        assertEquals("Parque da Bela Vista", event.venue());
        assertEquals("2026-06-20T20:00:00Z", event.start().toString());
    }
}