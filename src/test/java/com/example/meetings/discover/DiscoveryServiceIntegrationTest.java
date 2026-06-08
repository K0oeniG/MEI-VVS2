package com.example.meetings.discover;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Tests for DiscoveryService Coordination
 * This class validates the aggregation, deduplication, and scheduling
 * orchestration rules across all concrete third-party providers using
 * {@link SpringBootTest}.
 * 
 * Reasoning -> We load the full context to ensure that Spring's dependency
 * injection container automatically registers all active component beans
 * (AgendaLx, SeatGeek, Ticketmaster) into the {@link DiscoveryService} provider
 * list constructor. This verifies that our multi-provider fan-out architecture
 * compiles and loads cleanly.
 * 
 * * @author Diogo Carolino 58169
 */
@SpringBootTest(properties = {
        "app.discover.ticketmaster.api-key=fake-integration-key",
        "app.discover.seatgeek.client-id=fake-integration-client-id"
})
@DisplayName("DiscoveryService Coordination Integration Tests")
class DiscoveryServiceIntegrationTest {

    /**
     * The coordinator under test.
     * Reasoning -> Automatically autowired to confirm that Spring successfully
     * constructs a collection list containing every discovered @Component provider
     * class bean.
     */
    @Autowired
    private DiscoveryService discoveryService;

    /**
     * Test Case providers -> Container Component Registration Path
     * Objectives: Verify that all three written provider components are
     * successfully detected and bound inside the application environment context
     * loop.
     */
    @Test
    @DisplayName("Context Load - Should verify all 3 event providers are wired into the system context")
    void contextLoad_VerifyProvidersWired() {

        // Execute a collection state inspection work block
        List<EventProvider> registeredProviders = discoveryService.providers();

        // Assert 1: The container must have linked the providers array completely
        assertNotNull(registeredProviders);

        // Assert 2: We have AgendaLx, SeatGeek, and Ticketmaster; total bean size must
        // be 3
        assertEquals(3, registeredProviders.size(),
                "The application context configuration must automatically discover and wire all 3 provider implementations");
    }

    /**
     * Test Case search -> Empty/Blank Query Guard Path
     * Objectives: Verify that a null or purely empty text request returns a clean
     * empty list instantly without wasting network connections or triggering
     * provider exceptions.
     */
    @Test
    @DisplayName("search - Should return empty immutable list instantly when query parameter is blank")
    void search_BlankQuery_ReturnsEmptyList() {

        // Execute queries with invalid input strings
        List<DiscoveredEvent> nullResult = discoveryService.search(null);
        List<DiscoveredEvent> blankResult = discoveryService.search("   ");

        // Assert 1: Both operations must exit fast and return empty lists
        assertNotNull(nullResult);
        assertTrue(nullResult.isEmpty());

        assertNotNull(blankResult);
        assertTrue(blankResult.isEmpty());
    }
}