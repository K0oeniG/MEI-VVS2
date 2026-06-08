package com.example.meetings.controller;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import com.example.meetings.service.ICalService;
import com.example.meetings.service.MeetingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web Layer Integration Tests for ICalController
 * This class isolates and validates HTTP bindings, media type headers, and
 * unauthenticated public access boundaries of the iCalendar stream using
 * {@link WebMvcTest}.
 * Reasoning -> I use {@link MockMvc} to trigger endpoints without running a
 * real server. Downline layers are replaced using {@link MockBean}, ensuring
 * our assertions focus purely on routing, parameter conversions, and standard
 * HTTP response status contract states.
 * 
 * @author Diogo Carolino 58169
 */
@WebMvcTest(ICalController.class)
@DisplayName("ICalController Web Integration Tests")
class ICalControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    // Mock declarations bound directly into the WebMvc context structure.
    @MockBean
    private UserRepository userRepository;

    @MockBean
    private MeetingService meetingService;

    @MockBean
    private ICalService icalService;

    /**
     * Test Case feed -> Successful Token Lookup Path
     * Objectives: Verify that providing a matching token bypasses authentication
     * controls, resolves the user, fetches meetings, and writes an explicit
     * text/calendar response.
     */
    @Test
    @WithMockUser
    @DisplayName("GET /ical/{token}.ics - Should return 200 OK with correct calendar media headers")
    void feed_ValidToken_ReturnsCalendarStream() throws Exception {

        // Setup the Input and mock expectations
        String token = "secure-feed-token-abc";
        User mockUser = new User("diogo_user", "diogo@example.com", "hash");
        String dummyIcalContent = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n";

        when(userRepository.findByIcalToken(token)).thenReturn(Optional.of(mockUser));
        when(meetingService.calendarFor(mockUser)).thenReturn(Collections.emptyList());
        when(icalService.render(mockUser, Collections.emptyList())).thenReturn(dummyIcalContent);

        // Execute and Assert via MockMvc HTTP emulation
        mockMvc.perform(get("/ical/" + token + ".ics"))
                // Assert 1: Request must succeed without needing standard login credentials
                .andExpect(status().isOk())
                // Assert 2: Content-Type header must exactly specify calendar formatting
                // parameters
                .andExpect(content().contentType("text/calendar;charset=UTF-8"))
                // Assert 3: Content-Disposition attachment parameters must match RFC guidelines
                .andExpect(header().string("Content-Disposition", "inline; filename=\"meetings.ics\""))
                // Assert 4: Content body stream matches rendered value payload
                .andExpect(content().bytes(dummyIcalContent.getBytes()));

        // Verify downline dependencies interacted exactly once
        verify(userRepository, times(1)).findByIcalToken(token);
        verify(meetingService, times(1)).calendarFor(mockUser);
        verify(icalService, times(1)).render(mockUser, Collections.emptyList());
    }

    /**
     * Test Case feed -> Unknown/Invalid Token Path
     * Objectives: Verify that providing an invalid token throws an immediate HTTP
     * 404 NOT FOUND status state to prevent system data leakages.
     */
    @Test
    @WithMockUser
    @DisplayName("GET /ical/{token}.ics - Should return 404 Not Found when token is missing from registry")
    void feed_InvalidToken_ThrowsNotFound() throws Exception {

        // Setup mock expectation to return empty mapping container
        String badToken = "corrupted-token-xyz";
        when(userRepository.findByIcalToken(badToken)).thenReturn(Optional.empty());

        // Execute and Assert via MockMvc HTTP emulation
        mockMvc.perform(get("/ical/" + badToken + ".ics"))
                .andExpect(status().isNotFound());

        // Ensure rendering engines are short-circuited completely
        verifyNoInteractions(meetingService, icalService);
    }
}