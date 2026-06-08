package com.example.meetings.service;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for MeetingService
 * This class isolates and validates the operational logic of
 * {@link MeetingService} using pure unit testing with the lightweight
 * {@link MockitoExtension}.
 * {@link MeetingService} orchestrates our application's core transactional
 * bounds, including proposing meetings, deduplicating lists, validating
 * chronological constraints, mutating invite
 * feedback status, and converting externally discovered events into internal
 * domain instances.
 * 
 * @author Diogo Carolino 58169
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MeetingService Unit Tests")
class MeetingServiceTest {

    // Mock repositories
    @Mock
    private MeetingRepository meetingRepository;
    @Mock
    private MeetingParticipantRepository participantRepository;
    @Mock
    private UserRepository userRepository;

    // Class under test where mocks are injected.
    @InjectMocks
    private MeetingService meetingService;

    private User organizer;
    private Instant now;

    /**
     * Test Setup Configuration
     * Reasoning -> Executed before every single test case to establish a clean
     * state.
     * Since the User domain entity lacks a public ID setter to enforce strict
     * encapsulation, we use ReflectionTestUtils to safely simulate a
     * database-generated primary key identity (1L).
     */
    @BeforeEach
    void setupBaseEntities() {
        organizer = new User("org_user", "org@example.com", "hash");
        // TODO: MAKE SURE TO INCLUDE THE REFLECTION IN THE REPORT
        ReflectionTestUtils.setField(organizer, "id", 1L);
        now = Instant.now();
    }

    /**
     * Test Group: propose()
     * Objectives: Validate business rules for proposing meetings, ensuring user
     * list normalization, chronological bounds validation, and self-acceptance
     * automation loops.
     */
    @Nested
    @DisplayName("Tests for propose()")
    class ProposeTests {

        /**
         * Test Case propose -> Successful Path
         * Objectives: Ensure that valid parameters successfully instantiate a meeting,
         * auto-accept the cr eator, and clean/deduplicate input string lists.
         */
        @Test
        @DisplayName("Should create a meeting and auto-accept the organizer while sending pending invites to unique valid users")
        void propose_Success() {
            // Setup the Input and mock expectations
            String title = "Design Review";
            String description = "Discuss systemic modular structures";
            Instant end = now.plus(1, ChronoUnit.HOURS);

            User invitee = new User("invitee_1", "i1@example.com", "hash");

            when(userRepository.findByUsername("invitee_1")).thenReturn(Optional.of(invitee));
            when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Execute a unit of work (Pass duplicate usernames and padded strings to test
            // normalization/deduplication)
            Meeting result = meetingService.propose(
                    organizer, title, description, now, end,
                    List.of("invitee_1", " invitee_1 ", "org_user"));

            // Assert 1: The response must be initialized with data matching inputs
            assertNotNull(result);
            assertEquals(title, result.getTitle());
            assertEquals(description, result.getDescription());

            // Assert 2: Set collections must naturally absorb duplicates (1 organizer + 1
            // unique invitee = 2 total)
            Set<MeetingParticipant> participants = result.getParticipants();
            assertEquals(2, participants.size());

            // Assert 3: The proposing organizer must be mapped to automated ACCEPTED status
            MeetingParticipant organizerPart = participants.stream()
                    .filter(p -> p.getUser().getUsername().equals("org_user")).findFirst().orElseThrow();
            assertEquals(InviteStatus.ACCEPTED, organizerPart.getStatus());

            // Assert 4: The invited contact must default to PENDING status tracking
            MeetingParticipant inviteePart = participants.stream()
                    .filter(p -> p.getUser().getUsername().equals("invitee_1")).findFirst().orElseThrow();
            assertEquals(InviteStatus.PENDING, inviteePart.getStatus());

            // Assert 5: Confirm that the model was passed to the persistence layer once
            verify(meetingRepository, times(1)).save(any(Meeting.class));
        }

        /**
         * Test Case propose -> Chronological Failure Edge Case
         * Objectives: Prevent logical time errors by guaranteeing an exception is
         * thrown if the meeting's stop timestamp precedes its start threshold.
         */
        @Test
        @DisplayName("Should throw IllegalArgumentException when end time is chronologically before or equal to start time")
        void propose_InvalidTime_ThrowsException() {
            // Setup the Input pointing to an invalid end time
            Instant invalidEnd = now.minus(30, ChronoUnit.MINUTES);

            // Assert 1: Verify execution throws the expected exception
            assertThrows(IllegalArgumentException.class, () -> meetingService.propose(organizer, "Fail Meet", "Desc",
                    now, invalidEnd, Collections.emptyList()));

            // Assert 2: System should fail fast not hitting repositories
            verifyNoInteractions(meetingRepository, userRepository);
        }

        /**
         * Test Case propose -> Non-existent Invitee Edge Case
         * Objectives: Ensure systemic data integrity by failing the operation if an
         * invite target username cannot be resolved.
         */
        @Test
        @DisplayName("Should throw IllegalArgumentException if an invitee username cannot be found in the system")
        void propose_UnknownInvitee_ThrowsException() {
            // Setup an empty return state mapping to simulate a missing record
            Instant end = now.plus(1, ChronoUnit.HOURS);
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            // Assert 1: Verify execution throws the expected exception
            assertThrows(IllegalArgumentException.class,
                    () -> meetingService.propose(organizer, "Fail Meet", "Desc", now, end, List.of("ghost")));

            // Assert 2: Prevent corrupted models from being persisted
            verify(meetingRepository, never()).save(any());
        }
    }

    /**
     * Test Group: respond()
     * Objectives: Confirm identity mutation transitions when users provide active
     * invite feedback choices.
     */
    @Nested
    @DisplayName("Tests for respond()")
    class RespondTests {

        /**
         * Test Case respond -> Successful Path
         * Objectives: Verify that a valid participant status state updates perfectly.
         */
        @Test
        @DisplayName("Should successfully update invite status when valid response status is provided")
        void respond_Success() {
            // Setup the Input mapping an active invitation
            Long meetingId = 100L;
            MeetingParticipant participant = new MeetingParticipant(null, organizer, InviteStatus.PENDING);

            when(participantRepository.findByMeetingIdAndUserId(meetingId, organizer.getId()))
                    .thenReturn(Optional.of(participant));

            // Execute a unit of work
            meetingService.respond(meetingId, organizer, InviteStatus.ACCEPTED);

            // Assert 1: The participant invitation state must reflect the new choice values
            assertEquals(InviteStatus.ACCEPTED, participant.getStatus());
        }

        /**
         * Test Case respond -> Invalid Status Selection Edge Case
         * Objectives: Prevent users from resetting completed statuses back to PENDING.
         */
        @Test
        @DisplayName("Should throw IllegalArgumentException if client attempts to pass PENDING as a response status choice")
        void respond_InvalidStatusPending_ThrowsException() {
            // Assert 1: Verify execution throws the expected exception
            assertThrows(IllegalArgumentException.class,
                    () -> meetingService.respond(1L, organizer, InviteStatus.PENDING));
        }

        /**
         * Test Case respond -> Invitation Not Found Edge Case
         * Objectives: Ensure an operational block triggers if a user responds to an
         * event they are not invited to.
         */
        @Test
        @DisplayName("Should throw IllegalArgumentException if no invite association is matched in DB")
        void respond_NoInviteFound_ThrowsException() {
            // Setup an empty relation tracking entry
            when(participantRepository.findByMeetingIdAndUserId(1L, organizer.getId()))
                    .thenReturn(Optional.empty());

            // Assert 1: Verify execution throws the expected exception
            assertThrows(IllegalArgumentException.class,
                    () -> meetingService.respond(1L, organizer, InviteStatus.ACCEPTED));
        }
    }

    /**
     * Test Group: copyFromDiscovered()
     * Objectives: Verify structural translations from external discovery data feeds
     * into valid internal instances.
     */
    @Nested
    @DisplayName("Tests for copyFromDiscovered()")
    class CopyFromDiscoveredTests {

        /**
         * Test Case copyFromDiscovered -> Missing End Time Default Path
         * Objectives: Ensure that if an event has no specified end time, the system
         * fallback window logic
         * creates a realistic 2-hour duration window while preserving detailed tracking
         * tags in the description.
         */
        @Test
        @DisplayName("Should build comprehensive description string and fall back to a 2-hour window when structural event has no end time")
        void copyFromDiscovered_NoEndTime_DefaultsTwoHours() {
            // Setup input mimicking an incomplete external structure
            DiscoveredEvent structuralEvent = new DiscoveredEvent(
                    "TicketMaster",
                    "ext-123",
                    "Rock Concert",
                    "Live Rock Music",
                    now,
                    null, // defaults to 2 hours
                    "http://tickets.com",
                    "Arena Stadium");

            when(meetingRepository.save(any(Meeting.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // Execute a unit of work
            Meeting savedMeeting = meetingService.copyFromDiscovered(organizer, structuralEvent);

            // Assert 1: Base properties must survive mapping transitions
            assertNotNull(savedMeeting);
            assertEquals("Rock Concert", savedMeeting.getTitle());
            assertEquals(now.plus(Duration.ofHours(2)), savedMeeting.getEndTime());

            // Assert 2: Time fallbacks must accurately span exactly two hours forward from
            // start
            String desc = savedMeeting.getDescription();
            assertTrue(desc.contains("Live Rock Music"));
            assertTrue(desc.contains("Venue: Arena Stadium"));
            assertTrue(desc.contains("Source: TicketMaster (http://tickets.com)"));

            // Assert 3: Description data must construct detailed traceability strings
            assertEquals(1, savedMeeting.getParticipants().size());
            MeetingParticipant selfParticipant = savedMeeting.getParticipants().stream()
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No participants found in the set"));

            // Assert 4: Importers auto-accept their events, and Set collections match this
            // condition
            assertEquals(InviteStatus.ACCEPTED, selfParticipant.getStatus());
        }
    }

    /**
     * Test Group: calendarForIcalToken()
     * Objectives: Validate unauthenticated per-user calendar feed token checks and
     * data loading fallback routines.
     */
    @Nested
    @DisplayName("Tests for calendarForIcalToken()")
    class CalendarForIcalTokenTests {

        /**
         * Test Case calendarForIcalToken -> Successful Path
         * Objectives: Ensure users are correctly resolved from a matching secret token,
         * bypassing standard security filters.
         */
        @Test
        @DisplayName("Should resolve user from valid token and call calendar repository query fallback pipeline")
        void calendarForIcalToken_Success() {
            // Setup the input token expectations
            String mockToken = "secure-token-123";
            when(userRepository.findByIcalToken(mockToken)).thenReturn(Optional.of(organizer));
            when(meetingRepository.findCalendarMeetings(organizer)).thenReturn(Collections.emptyList());

            // Execute a unit of work
            List<Meeting> results = meetingService.calendarForIcalToken(mockToken);

            // Assert 1: The resulting array container should never be null
            assertNotNull(results);

            // Assert 2: Verify both resolution methods executed exactly once
            verify(userRepository).findByIcalToken(mockToken);
            verify(meetingRepository).findCalendarMeetings(organizer);
        }

        /**
         * Test Case calendarForIcalToken -> Token Invalid Edge Case
         * Objectives: Throw errors immediately if a token is corrupt or unauthorized to
         * prevent information leakages.
         */
        @Test
        @DisplayName("Should throw IllegalArgumentException if token is invalid or non-existent")
        void calendarForIcalToken_InvalidToken_ThrowsException() {
            // Setup an invalid token empty mapping
            when(userRepository.findByIcalToken("bad")).thenReturn(Optional.empty());

            // Assert 1: Verify execution throws the expected exception
            assertThrows(IllegalArgumentException.class, () -> meetingService.calendarForIcalToken("bad"));
        }
    }
}