package com.example.meetings.controller;

import com.example.meetings.model.InviteStatus;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web Layer Integration Tests for MeetingController
 * This class validates secure form actions, user principal extractions, and
 * model transitions of the meeting scheduler endpoint layouts using
 * {@link WebMvcTest}.
 * Reasoning -> We leverage {@link WithMockUser} to inject a authenticated user
 * principal into Spring Security's context context automatically. This
 * satisfies the controller's {@code @AuthenticationPrincipal} mapping parameter
 * without loading OAuth or database details.
 * 
 * @author Diogo Carolino 58169
 */
@WebMvcTest(MeetingController.class)
@DisplayName("MeetingController Web Integration Tests")
class MeetingControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MeetingService meetingService;

    @MockBean
    private UserService userService;

    /**
     * Test Case proposeForm -> View Resolution Path
     * Objectives: Confirm that a simple request to create a new meeting properly
     * renders the "propose" view template.
     */
    @Test
    @WithMockUser
    @DisplayName("GET /meetings/new - Should resolve and display the HTML form template string name")
    void proposeForm_ResolvesCorrectViewName() throws Exception {
        mockMvc.perform(get("/meetings/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("propose"));
    }

    /**
     * Test Case respond -> Invitation Acceptance Post Request Path
     * Objectives: Verify that submitting a response post request correctly updates
     * the status parameter values and issues an HTTP redirect back to the central
     * calendar page.
     */
    @Test
    @WithMockUser(username = "maria_tester")
    @DisplayName("POST /meetings/{id}/respond - Should update invite status to ACCEPTED and issue a context redirect")
    void respond_ActionAccept_MutatesStateAndRedirects() throws Exception {

        // Setup internal context structures
        Long targetMeetingId = 442L;
        User resolvedMockUser = new User("maria_tester", "maria@example.com", "hash");
        when(userService.requireByUsername("maria_tester")).thenReturn(resolvedMockUser);

        // Execute Post parameter submission
        mockMvc.perform(post("/meetings/" + targetMeetingId + "/respond")
                .param("action", "accept")
                .with(csrf())) // Simulates valid CSRF tokens
                // Assert 1: The response must issue an HTTP redirect status
                .andExpect(status().is3xxRedirection())
                // Assert 2: The redirect location path must lead to the main user calendar
                // layout
                .andExpect(redirectedUrl("/calendar"));

        // Assert 3: Confirm service business method received correct parameter
        // arguments
        verify(meetingService, times(1)).respond(eq(targetMeetingId), eq(resolvedMockUser), eq(InviteStatus.ACCEPTED));
    }

    /**
     * Test Case propose -> Form Validation Failure Exception Catch Path
     * Objectives: Verify that if a user fills out the scheduling parameters
     * incorrectly, the controller catches the error, appends data variables back
     * into the page context, and shows the form again with explicit error messages.
     */
    @Test
    @WithMockUser(username = "diogo_organizer")
    @DisplayName("POST /meetings/new - Should catch business errors, retain form inputs, and return template context")
    void propose_ServiceException_ReturnsFormWithErrorContext() throws Exception {

        // Setup the expectation that service throws error (e.g. invalid date order
        // bounds)
        when(meetingService.propose(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("End time cannot match start threshold boundaries"));

        // Execute post request containing invalid fields
        mockMvc.perform(post("/meetings/new")
                .param("title", "Broken Meeting Title")
                .param("description", "Faulty validation description data")
                .param("start", "2026-06-08T10:00:00")
                .param("end", "2026-06-08T09:00:00") // <-- Chronologically broken
                .param("invitees", "bob, alice")
                .with(csrf()))
                // Assert 1: Request should complete with 200 OK to re-render error text inputs
                .andExpect(status().isOk())
                // Assert 2: Must fall back to the base propose form layout view name
                .andExpect(view().name("propose"))
                // Assert 3: Context variables must be retained to avoid wiping user form inputs
                .andExpect(model().attribute("error", "End time cannot match start threshold boundaries"))
                .andExpect(model().attribute("title", "Broken Meeting Title"))
                .andExpect(model().attribute("invitees", "bob, alice"));
    }
}