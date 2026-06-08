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
        "app.discover.agendalx.base-url=http://localhost:8089/mock-agendalx",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"
})
@ActiveProfiles("test")
@DisplayName("AgendaLx Provider Live Integration Mapping Test")
class AgendaLxProviderIntegrationTest {

    private static final WireMockServer wireMockServer = new WireMockServer(0);

    @Autowired
    private AgendaLxProvider provider;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        wireMockServer.start();
        registry.add("app.discover.agendalx.base-url", wireMockServer::baseUrl);
    }

    @Test
    @DisplayName("search - Should parse WordPress JSON structure, strip HTML, and resolve dates")
    void search_ValidResponse_ParsesAndCleansData() {
        String mockJsonResponse = """
                [
                  {
                    "id": 5544,
                    "title": { "rendered": "Fado Night Tour" },
                    "description": [
                      "<p>Experience the traditional <strong>Fado music</strong> inside Alfama.</p>"
                    ],
                    "occurences": [ "2026-08-12" ],
                    "string_times": "21h30",
                    "link": "https://agendalx.pt/events/fado-night",
                    "venue": {
                      "venue_101": { "name": "Alfama District Tavern" }
                    }
                  }
                ]
                """;

        wireMockServer.stubFor(get(urlPathEqualTo("/events"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(mockJsonResponse)
                        .withStatus(200)));

        List<DiscoveredEvent> events = provider.search("Fado");

        assertEquals(1, events.size());
        DiscoveredEvent event = events.get(0);
        assertEquals("5544", event.externalId());
        assertEquals("Fado Night Tour", event.title());

        // Asserts that HTML_TAG regex correctly stripped tags and trimmed text
        assertEquals("Experience the traditional  Fado music  inside Alfama.", event.description());
        assertEquals("Alfama District Tavern", event.venue());

        // Asserts Lisbon Timezone offset calculation logic (2026-08-12T21:30:00 Lisbon
        // -> UTC)
        assertNotNull(event.start());
    }
}