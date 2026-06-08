package com.example.meetings.service;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for ICalService
 * This class isolates and validates the behaviour of {@link ICalService}
 * using pure unit testing.
 * {@link ICalService} is responsible for translating application domain models
 * into a standard RFC 5545 compliant iCalendar (.ics) text string.
 * This ensures external calendar applications (Google Calendar, Outlook, Apple
 * Calendar)
 * can parse our user's data accurately.
 * * @author Diogo Carolino 58169
 */
@DisplayName("ICalService Unit Tests")
class ICalServiceTest {

    private ICalService iCalService;
    private User owner;

    /**
     * Test Environment Setup
     * Reasoning -> Executed before every single test case to ensure a fresh,
     * independent state and avoid side-effects between test assertions.
     */
    @BeforeEach
    void setUp() {
        iCalService = new ICalService();
        owner = mock(User.class);
        when(owner.getUsername()).thenReturn("john_doe");
    }

    /**
     * Test Case render -> Successful Empty Calendar Path
     * Objectives: Verify that when a user has no scheduled meetings, the service
     * still generates a valid, well-formed VCALENDAR envelope.
     * Specific Verifications:
     * - Ensures the document begins and ends with correct protocol identifiers
     * - Ensures the calendar name contains the owner's identity properties
     */
    @Test
    @DisplayName("render - Should generate valid empty VCALENDAR when no meetings present")
    void render_EmptyCalendar() {

        // Execute a unit of work with zero elements
        String result = iCalService.render(owner, Collections.emptyList());

        // Assert 1: Must begin with standard iCalendar header boundaries
        assertTrue(result.startsWith("BEGIN:VCALENDAR\r\n"));
        // Assert 2: Must define a metadata property carrying the owner name
        assertTrue(result.contains("X-WR-CALNAME:john_doe's meetings\r\n"));
        // Assert 3: Must close properly matching the initial wrapper boundaries
        assertTrue(result.endsWith("END:VCALENDAR\r\n"));
    }

    /**
     * Test Case render -> Complex Integration Mapping and Escaping Path
     * Objectives: Verify that when meetings exist, the service successfully maps
     * structural data, parses times to UTC formats, and handles critical character
     * escaping.
     * Specific Verifications:
     * - Ensures standard dates convert into standard UTC pattern configurations
     * - Ensures dynamic DTSTAMP timestamps match proper protocol patterns
     * - Ensures metadata control characters (\n , ; \\) escape per RFC 5545
     * regulations
     * - Ensures participant status converts from application state to iCal terms
     * (PENDING -> NEEDS-ACTION)
     */
    @Test
    @DisplayName("render - Should properly map meetings, format dates, and handle character escaping")
    void render_WithMeetingsAndEscaping() {

        // Setup a meeting that contains characters needing escaping (\ , ; \n)
        Meeting meeting = mock(Meeting.class);
        when(meeting.getId()).thenReturn(42L);
        when(meeting.getTitle()).thenReturn("Project Sync; Urgent, Status");
        when(meeting.getDescription()).thenReturn("Line 1\nLine 2");
        when(meeting.getStartTime()).thenReturn(Instant.parse("2026-06-01T10:00:00Z"));
        when(meeting.getEndTime()).thenReturn(Instant.parse("2026-06-01T11:00:00Z"));
        when(meeting.isConfirmed()).thenReturn(true);

        // Setup organizer
        User organizer = mock(User.class);
        when(organizer.getUsername()).thenReturn("boss_man");
        when(organizer.getEmail()).thenReturn("boss@company.com");
        when(meeting.getOrganizer()).thenReturn(organizer);

        // Setup an invitee/participant
        User participantUser = mock(User.class);
        when(participantUser.getUsername()).thenReturn("dev_lee");
        when(participantUser.getEmail()).thenReturn("lee@company.com");

        MeetingParticipant participant = mock(MeetingParticipant.class);
        when(participant.getUser()).thenReturn(participantUser);
        when(participant.getStatus()).thenReturn(InviteStatus.PENDING);

        // Bind the participant set data to our parent meeting instance
        when(meeting.getParticipants()).thenReturn(Set.of(participant));

        // Execute unit of work
        String result = iCalService.render(owner, List.of(meeting));

        // Assert 1: The response must open and define an individual calendar event
        // block
        assertTrue(result.contains("BEGIN:VEVENT\r\n"));
        assertTrue(result.contains("UID:meeting-42@meetings-app\r\n"));
        // Assert 2: The start and end timestamps must strictly follow the UTC compact
        // pattern layout
        assertTrue(result.contains("DTSTART:20260601T100000Z\r\n"));
        assertTrue(result.contains("DTEND:20260601T110000Z\r\n"));
        // Assert 3: Ensure current stamp generation evaluates properly
        assertTrue(result.matches("(?s).*DTSTAMP:\\d{8}T\\d{6}Z\r\n.*"));
        // Assert 4: Verify text field escaping logic to shield calendar against
        // parsing flaws
        assertTrue(result.contains("SUMMARY:Project Sync\\; Urgent\\, Status\r\n"));
        assertTrue(result.contains("DESCRIPTION:Line 1\\nLine 2\r\n"));
        // Assert 5: Confirm that PENDING converts to the iCal standard 'NEEDS-ACTION'
        assertTrue(result.contains("ATTENDEE;CN=dev_lee;PARTSTAT=NEEDS-ACTION:mailto:lee@company.com\r\n"));
        // Assert 6: Verify correct mapping of email and name parameters
        assertTrue(result.contains("ORGANIZER;CN=boss_man:mailto:boss@company.com\r\n"));
        // Assert 7: Verify overall status
        assertTrue(result.contains("STATUS:CONFIRMED\r\n"));
        assertTrue(result.contains("END:VEVENT\r\n"));
    }

    /**
     * Test Case render -> Participant Status and Confirmation Branch Coverage
     * Objectives: Verify alternative participant invitation responses and meeting
     * configurations mapping safely inside the calendar stream buffer.
     * Specific Verifications:
     * - Ensures InviteStatus.ACCEPTED cleanly transforms into PARTSTAT=ACCEPTED
     * - Ensures InviteStatus.DECLINED cleanly transforms into PARTSTAT=DECLINED
     * - Ensures an unconfirmed meeting correctly evaluates to STATUS:TENTATIVE
     */
    @Test
    @DisplayName("render - Should correctly map ACCEPTED/DECLINED statuses and TENTATIVE meeting status")
    void render_WithDifferentStatusesAndUnconfirmedMeeting() {

        // Setup an unconfirmed meeting to evaluate the false statement branch
        Meeting meeting = mock(Meeting.class);
        when(meeting.getId()).thenReturn(101L);
        when(meeting.getTitle()).thenReturn("Branch Coverage Meeting");
        when(meeting.getStartTime()).thenReturn(Instant.parse("2026-06-10T10:00:00Z"));
        when(meeting.getEndTime()).thenReturn(Instant.parse("2026-06-10T11:00:00Z"));
        when(meeting.isConfirmed()).thenReturn(false);

        // Setup base organizer mock details
        User organizer = mock(User.class);
        when(organizer.getUsername()).thenReturn("organizer");
        when(organizer.getEmail()).thenReturn("org@test.com");
        when(meeting.getOrganizer()).thenReturn(organizer);

        // Setup Participant 1 to force the isolated switch expression ACCEPTED path
        User user1 = mock(User.class);
        when(user1.getUsername()).thenReturn("accepted_user");
        when(user1.getEmail()).thenReturn("accepted@test.com");
        MeetingParticipant p1 = mock(MeetingParticipant.class);
        when(p1.getUser()).thenReturn(user1);
        when(p1.getStatus()).thenReturn(InviteStatus.ACCEPTED);

        // Setup Participant 2 to force the isolated switch expression DECLINED path
        User user2 = mock(User.class);
        when(user2.getUsername()).thenReturn("declined_user");
        when(user2.getEmail()).thenReturn("declined@test.com");
        MeetingParticipant p2 = mock(MeetingParticipant.class);
        when(p2.getUser()).thenReturn(user2);
        when(p2.getStatus()).thenReturn(InviteStatus.DECLINED);

        // Bind multi-branch participant configurations into the parent stream context
        when(meeting.getParticipants()).thenReturn(Set.of(p1, p2));

        // Execute unit of work
        String result = iCalService.render(owner, List.of(meeting));

        // Assert 1: Confirm that the unconfirmed condition produces correct RFC string
        // status
        assertTrue(result.contains("STATUS:TENTATIVE\r\n"));
        // Assert 2: Verify both distinct switch evaluation properties map perfectly
        assertTrue(result.contains("ATTENDEE;CN=accepted_user;PARTSTAT=ACCEPTED:mailto:accepted@test.com\r\n"));
        assertTrue(result.contains("ATTENDEE;CN=declined_user;PARTSTAT=DECLINED:mailto:declined@test.com\r\n"));
    }

    /**
     * Test Case render -> Description Property Conditional Guards
     * Objectives: Verify that optional descriptions completely suppress rendering
     * structural components when processing null variants or whitespace inputs.
     * Specific Verifications:
     * - Ensures a null string reference does not invoke property output
     * - Ensures a blank space string configuration skips internal logic evaluation
     */
    @Test
    @DisplayName("render - Should completely omit DESCRIPTION when it is null or blank")
    void render_WithNullOrBlankDescription() {

        // Setup meeting one targeting the strict null verification conditional branch
        Meeting m1 = mock(Meeting.class);
        when(m1.getId()).thenReturn(1L);
        when(m1.getTitle()).thenReturn("Null Desc");
        when(m1.getStartTime()).thenReturn(Instant.parse("2026-06-10T10:00:00Z"));
        when(m1.getEndTime()).thenReturn(Instant.parse("2026-06-10T11:00:00Z"));
        when(m1.getOrganizer()).thenReturn(mock(User.class));
        when(m1.getParticipants()).thenReturn(Collections.emptySet());
        when(m1.getDescription()).thenReturn(null);

        // Setup meeting two targeting the blank validation character constraint branch
        Meeting m2 = mock(Meeting.class);
        when(m2.getId()).thenReturn(2L);
        when(m2.getTitle()).thenReturn("Blank Desc");
        when(m2.getStartTime()).thenReturn(Instant.parse("2026-06-10T12:00:00Z"));
        when(m2.getEndTime()).thenReturn(Instant.parse("2026-06-10T13:00:00Z"));
        when(m2.getOrganizer()).thenReturn(mock(User.class));
        when(m2.getParticipants()).thenReturn(Collections.emptySet());
        when(m2.getDescription()).thenReturn("   ");

        // Execute unit of work
        String result = iCalService.render(owner, List.of(m1, m2));

        // Assert 1: The document must completely isolate text lines from DESCRIPTION
        // tokens
        assertFalse(result.contains("DESCRIPTION:"));
    }

    /**
     * Test Case render -> Escape Engine Null Safeguards and Control Formatting
     * Objectives: Verify the character escaping logic under boundary conditions
     * such as missing input properties or raw carriage line control data.
     * Specific Verifications:
     * - Ensures a null property object yields safe execution via fallback text
     * expressions
     * - Ensures text strings purge standard carriage control returns (\r) per
     * specification rules
     */
    @Test
    @DisplayName("render - Should handle null strings inside escape block and strip carriage returns")
    void render_WithNullValuesAndCarriageReturn() {

        // Setup a meeting structure delivering an explicit null title and explicit
        // carriage text lines
        Meeting meeting = mock(Meeting.class);
        when(meeting.getId()).thenReturn(99L);
        when(meeting.getTitle()).thenReturn(null);
        when(meeting.getDescription()).thenReturn("Text\rWith\rCR");
        when(meeting.getStartTime()).thenReturn(Instant.parse("2026-06-10T10:00:00Z"));
        when(meeting.getEndTime()).thenReturn(Instant.parse("2026-06-10T11:00:00Z"));

        // Setup simple contextual defaults
        User organizer = mock(User.class);
        when(organizer.getUsername()).thenReturn("org");
        when(organizer.getEmail()).thenReturn("org@test.com");
        when(meeting.getOrganizer()).thenReturn(organizer);
        when(meeting.getParticipants()).thenReturn(Collections.emptySet());

        // Execute unit of work
        String result = iCalService.render(owner, List.of(meeting));

        // Assert 1: Null values must pass safety guards and resolve into standard blank
        // structures
        assertTrue(result.contains("SUMMARY:\r\n"));
        // Assert 2: Carriage control tokens must match formatting expressions and exit
        // clean
        assertTrue(result.contains("DESCRIPTION:TextWithCR\r\n"));
    }
}