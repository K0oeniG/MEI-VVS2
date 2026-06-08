package com.example.meetings.controller;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.discover.DiscoveryService;
import com.example.meetings.model.User;
import com.example.meetings.service.MeetingService;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web Layer Integration Tests for DiscoveryController
 * This class validates the web rendering, query lookups, and third-party copy
 * actions
 * of the event searching portal package layer using {@link WebMvcTest}.
 * * @author Diogo Carolino 58169
 */
@WebMvcTest(DiscoveryController.class)
@DisplayName("DiscoveryController Web Integration Tests")
class DiscoveryControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DiscoveryService discoveryService;

    @MockBean
    private MeetingService meetingService;

    @MockBean
    private UserService userService;

    /**
     * Test Case discover -> Initial View Loading Path
     * Objectives: Verify that navigating to the discovery search engine with an
     * empty query parameter binds provider count parameters to the layout model
     * attributes safely.
     */
    @Test
    @WithMockUser
    @DisplayName("GET /discover - Should return empty payload container lists when no keyword is searched")
    void discover_EmptySearchQuery_ReturnsProviderStats() throws Exception {

        when(discoveryService.providers()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/discover"))
                .andExpect(status().isOk())
                .andExpect(view().name("discover"))
                .andExpect(model().attribute("anyConfigured", false))
                .andExpect(model().attribute("results", Collections.emptyList()));
    }

    /**
     * Test Case copy -> Third Party Event Capture Path
     * Objectives: Verify that submitting a copy request parses external form
     * tokens, constructs an authentic {@link DiscoveredEvent} data record payload
     * structure, and saves it cleanly before issuing a standard calendar redirect
     * route.
     */
    @Test
    @WithMockUser(username = "diogo_importer")
    @DisplayName("POST /discover/copy - Should parse external API fields into record structures and copy to local calendar")
    void copy_ValidPayload_MapsToDomainRecordAndRedirects() throws Exception {

        // Setup user context boundaries
        User mockImporter = new User("diogo_importer", "import@example.com", "hash");
        when(userService.requireByUsername("diogo_importer")).thenReturn(mockImporter);

        // Execute multi-parameter post tracking request
        mockMvc.perform(post("/discover/copy")
                .param("source", "Ticketmaster")
                .param("externalId", "tm-evt-9912")
                .param("title", "Rock Concert Festival 2026")
                .param("description", "Live arena rock performance tracking.")
                .param("start", "2026-08-20T21:00:00Z")
                .param("end", "2026-08-20T23:00:00Z")
                .param("url", "https://ticketmaster.com/rock-2026")
                .param("venue", "Coliseu dos Recreios")
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/calendar"));

        // Assert 1: Verify service execution intercepted parameter mappings and built
        // record parameters
        DiscoveredEvent expectedRecordShape = new DiscoveredEvent(
                "Ticketmaster",
                "tm-evt-9912",
                "Rock Concert Festival 2026",
                "Live arena rock performance tracking.",
                Instant.parse("2026-08-20T21:00:00Z"),
                Instant.parse("2026-08-20T23:00:00Z"),
                "https://ticketmaster.com/rock-2026",
                "Coliseu dos Recreios");

        verify(userService, times(1)).requireByUsername("diogo_importer");
        verify(meetingService, times(1)).copyFromDiscovered(eq(mockImporter), eq(expectedRecordShape));
    }
}