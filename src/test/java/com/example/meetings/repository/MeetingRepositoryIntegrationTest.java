package com.example.meetings.repository;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concrete Database Integration Tests for MeetingRepository
 * This class validates custom JPQL syntax, relational joins, and transaction
 * filters against an active test database schema using {@link DataJpaTest}.
 * Reasoning -> We use {@link DataJpaTest} to spin up a concrete, non-production
 * SQL engine.
 * It automatically maps our JPA entities, compiles the underlying database
 * schema, and ensures that every test executes inside a rollback transaction
 * context to keep test states isolated.
 * 
 * @author Diogo Carolino 58169
 */
@DataJpaTest
@DisplayName("MeetingRepository Concrete Database Integration Tests")
class MeetingRepositoryIntegrationTest {

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private MeetingRepository meetingRepository;

        @Autowired
        private MeetingParticipantRepository meetingParticipantRepository;

        /**
         * Test Case findCalendarMeetings -> Organizer and Participant Exclusion
         * Boundaries
         * Objectives: Verify that the calendar retrieval query cleanly fetches meetings
         * organized by the user, includes meetings where they are a pending/accepted
         * participant, but strictly excludes any meeting invitations they explicitly
         * declined.
         */
        @Test
        @DisplayName("findCalendarMeetings - Should aggregate active commitments and exclude declined invitations")
        void findCalendarMeetings_FiltersCorrectlyBasedOnStatus() {

                // Setup Base Data Model Context Structures
                User diogo = userRepository.save(new User("diogo_test", "diogo@example.com", "pass"));
                User maria = userRepository.save(new User("maria_test", "maria@example.com", "pass"));

                Instant baseTime = Instant.now().plus(1, ChronoUnit.DAYS);

                // Meeting A: Diogo is the Organizer (Must appear on calendar)
                Meeting meetingA = new Meeting("Diogo's Sync", "Organizer Path Test", baseTime,
                                baseTime.plus(1, ChronoUnit.HOURS), diogo);
                meetingRepository.save(meetingA);

                // Meeting B: Maria organizes, Diogo accepts (Must appear on calendar)
                Meeting meetingB = new Meeting("Maria's Project Brief", "Accepted Invitee Path",
                                baseTime.plus(3, ChronoUnit.HOURS), baseTime.plus(4, ChronoUnit.HOURS), maria);
                meetingB.addParticipant(new MeetingParticipant(meetingB, diogo, InviteStatus.ACCEPTED));
                meetingRepository.save(meetingB);

                // Meeting C: Maria organizes, Diogo declines (Must NOT appear on calendar to
                // free up time slot)
                Meeting meetingC = new Meeting("Declined Gathering", "Declined Path Filter Test",
                                baseTime.plus(5, ChronoUnit.HOURS), baseTime.plus(6, ChronoUnit.HOURS), maria);
                meetingC.addParticipant(new MeetingParticipant(meetingC, diogo, InviteStatus.DECLINED));
                meetingRepository.save(meetingC);

                // Execute the Custom JPQL Unit of Work
                List<Meeting> calendarMeetings = meetingRepository.findCalendarMeetings(diogo);

                // Concrete Assertions
                assertNotNull(calendarMeetings, "The query result should never return a null collection reference");
                assertEquals(2, calendarMeetings.size(),
                                "Diogo's calendar should contain exactly 2 entries (Organized + Accepted)");

                // Chronological order verification (JPQL order by m.startTime)
                assertEquals("Diogo's Sync", calendarMeetings.get(0).getTitle(),
                                "The earlier meeting must be resolved first in chronological sort order");
                assertEquals("Maria's Project Brief", calendarMeetings.get(1).getTitle(),
                                "The later accepted meeting must occupy the second position index");

                // Guaranteeing the declined meeting was completely filtered out
                boolean containsDeclined = calendarMeetings.stream()
                                .anyMatch(m -> m.getTitle().equals("Declined Gathering"));
                assertFalse(containsDeclined,
                                "Meetings where the targeted user has a DECLINED invitation status must be completely omitted from results");
        }

        /**
         * Test Case findOverlapping -> Time Window Conflict Detection Scenarios
         * Objectives: Validate that the overlap evaluation algorithm matches
         * intersections accurately across various timeframe configurations and
         * boundaries.
         */
        @Test
        @DisplayName("findOverlapping - Should capture intersecting time segments accurately")
        void findOverlapping_IdentifiesConflictsCorrectly() {

                // Setup Base Data Model Context Structures
                User organizer = userRepository.save(new User("conflict_user", "conflict@example.com", "pass"));
                Instant anchorStart = Instant.parse("2026-07-10T14:00:00Z");
                Instant anchorEnd = Instant.parse("2026-07-10T15:30:00Z");

                // Save a core baseline meeting that occupies the 14:00 - 15:30 time window
                // block
                Meeting baselineMeeting = new Meeting("Core Standup", "Baseline Anchor", anchorStart, anchorEnd,
                                organizer);
                meetingRepository.save(baselineMeeting);

                // Execute conflict lookups against different hypothetical windows and verify
                // behavior

                // Scenario A: Exact match window (14:00 - 15:30) -> High Conflict Overlap
                // Expected
                List<Meeting> exactOverlap = meetingRepository.findOverlapping(organizer, anchorStart, anchorEnd);
                assertEquals(1, exactOverlap.size(),
                                "An identical time window query must trigger a conflict check match");

                // Scenario B: Internal subset window (14:15 - 15:00) -> Overlap Expected
                List<Meeting> internalOverlap = meetingRepository.findOverlapping(organizer,
                                anchorStart.plus(15, ChronoUnit.MINUTES), anchorEnd.minus(30, ChronoUnit.MINUTES));
                assertEquals(1, internalOverlap.size(),
                                "A query window completely enclosed within an existing meeting is a severe conflict scenario");

                // Scenario C: Partial intersection tail side (15:15 - 16:00) -> Overlap
                // Expected
                List<Meeting> tailOverlap = meetingRepository.findOverlapping(organizer,
                                anchorEnd.minus(15, ChronoUnit.MINUTES), anchorEnd.plus(30, ChronoUnit.MINUTES));
                assertEquals(1, tailOverlap.size(),
                                "A window that clips the end of an active meeting must be flagged as an overlapping conflict");

                // Scenario D: Strictly sequential/adjacent boundary check (15:30 - 17:00) ->
                // Clean / No Overlap Expected
                // Reasoning: standard business logic dictates that a meeting can start at the
                // exact moment another ends (m.startTime < :end AND m.endTime > :start)
                List<Meeting> adjacentWindow = meetingRepository.findOverlapping(organizer, anchorEnd,
                                anchorEnd.plus(1, ChronoUnit.HOURS));
                assertTrue(adjacentWindow.isEmpty(),
                                "Adjacent timelines meeting right at the boundary edge are clean and must not conflict");
        }
}