package com.example.meetings.discover;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Tests for Third-Party Provider Configuration Guards
 * This class validates the conditional environment initialization parameters
 * for our providers.
 * Reasoning -> By explicitly injecting blank credentials via
 * {@link TestPropertySource}, I test the real conditional boundary conditions
 * of {@link TicketmasterProvider} and {@link SeatGeekProvider} to confirm that
 * the system safely disables their search execution loops if properties are
 * missing in a production environment.
 * 
 * @author Diogo Carolino 58169
 */
@SpringBootTest
@TestPropertySource(properties = {
        "app.discover.ticketmaster.api-key=", // Explicitly blank to simulate missing secret key
        "app.discover.seatgeek.client-id=  " // Whitespace padded string to check isolation filters
})
@DisplayName("Third-Party Provider Configuration Integration Tests")
class ThirdPartyConfigurationIntegrationTest {

    @Autowired
    private TicketmasterProvider ticketmasterProvider;

    @Autowired
    private SeatGeekProvider seatGeekProvider;

    @Autowired
    private AgendaLxProvider agendaLxProvider;

    /**
     * Test Case isConfigured -> External Credential Validation Path
     * Objectives: Verify that providers correctly manage their operational
     * readiness flags based on the loaded environment properties.
     */
    @Test
    @DisplayName("isConfigured - Should dynamically toggle provider activation flags based on environment properties")
    void isConfigured_ValidatesPropertyPresence() {

        // Execute configuration readiness flag checks
        boolean isTicketmasterActive = ticketmasterProvider.isConfigured();
        boolean isSeatGeekActive = seatGeekProvider.isConfigured();
        boolean isAgendaLxActive = agendaLxProvider.isConfigured();

        // Assert 1: Ticketmaster must evaluate to false because the configuration
        // property string is completely empty
        assertFalse(isTicketmasterActive,
                "Ticketmaster should report unconfigured when its API key property is empty");

        // Assert 2: SeatGeek must evaluate to false because whitespace strings should
        // be rejected by the isBlank() check
        assertFalse(isSeatGeekActive,
                "SeatGeek should report unconfigured when its client-id contains only whitespace");

        // Assert 3: AgendaLx does not require authentication headers or credentials, so
        // it must always remain active
        assertTrue(isAgendaLxActive,
                "AgendaLx must always evaluate to configured since it relies purely on a public API endpoint");
    }

    /**
     * Test Case search -> Execution Guard Bypass Path
     * Objectives: Confirm that if a provider is unconfigured, it short-circuits
     * safely and returns an empty list instead of making a broken network call to
     * the third-party API.
     */
    @Test
    @DisplayName("search - Should short-circuit and return empty list immediately if provider is unconfigured")
    void search_UnconfiguredProvider_ShortCircuits() {

        // Execute search on an unconfigured component
        List<DiscoveredEvent> results = ticketmasterProvider.search("Rock Concert");

        // Assert 1: Must return an empty list without throwing runtime connection
        // exceptions
        assertNotNull(results);
        assertTrue(results.isEmpty(),
                "Unconfigured providers must skip remote API calls and immediately exit with an empty collection list");
    }
}