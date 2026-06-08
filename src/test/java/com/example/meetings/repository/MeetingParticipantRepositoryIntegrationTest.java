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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concrete Database Integration Tests for MeetingParticipantRepository
 * This class validates mapping relations, join keys, and multi-conditional
 * column queries on the intermediate join table schema using
 * {@link DataJpaTest}.
 * Reasoning -> Validates that composite foreign-key associations and user
 * invitation states map seamlessly to database rows without causing data
 * mapping truncation or cascade collection faults.
 * 
 * @author Diogo Carolino 58169
 */
@DataJpaTest
@DisplayName("MeetingParticipantRepository Concrete Database Integration Tests")
class MeetingParticipantRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private MeetingParticipantRepository participantRepository;

    /**
     * Test Case findByUserAndStatus -> Pending Invite Isolation Path
     * Objectives: Verify that querying via a user and a state cleanly extracts
     * invitations matching that exact state, while filtering out everything else.
     */
    @Test
    @DisplayName("findByUserAndStatus - Should isolate specified invite states from general participant lines")
    void findByUserAndStatus_FiltersExactStateRows() {

        // 1. Arrange contextual domain assets
        User invitee = userRepository.save(new User("maria_invites", "maria@inv.com", "pass"));
        User host = userRepository.save(new User("host_user", "host@inv.com", "pass"));

        Meeting meeting1 = meetingRepository
                .save(new Meeting("Meeting One", "Desc", Instant.now(), Instant.now().plusSeconds(3600), host));
        Meeting meeting2 = meetingRepository
                .save(new Meeting("Meeting Two", "Desc", Instant.now(), Instant.now().plusSeconds(3600), host));

        // Create two distinct invitation state rows
        participantRepository.save(new MeetingParticipant(meeting1, invitee, InviteStatus.PENDING));
        participantRepository.save(new MeetingParticipant(meeting2, invitee, InviteStatus.ACCEPTED));

        // Execute unit of work
        List<MeetingParticipant> pendingInvites = participantRepository.findByUserAndStatus(invitee,
                InviteStatus.PENDING);

        // Assertions
        assertNotNull(pendingInvites);
        assertEquals(1, pendingInvites.size(),
                "Only invitations matching the PENDING parameter flag should be returned");
        assertEquals("Meeting One", pendingInvites.get(0).getMeeting().getTitle(),
                "The query must isolate the correct meeting reference string row");
    }

    /**
     * Test Case findByMeetingIdAndUserId -> Composite Identification Path
     * Objectives: Test composite identifier cross-referencing to confirm that the
     * repository can locate a specific person's invitation row record for a single
     * given meeting ID.
     */
    @Test
    @DisplayName("findByMeetingIdAndUserId - Should resolve single localized participant links using structural compound foreign keys")
    void findByMeetingIdAndUserId_LocatesUniqueIntersectingRow() {

        User participant = userRepository.save(new User("bob_builder", "bob@build.com", "pass"));
        User manager = userRepository.save(new User("manager_user", "man@build.com", "pass"));

        Meeting concreteMeeting = meetingRepository
                .save(new Meeting("Scrum Alignment", "Desc", Instant.now(), Instant.now().plusSeconds(3600), manager));
        MeetingParticipant record = participantRepository
                .save(new MeetingParticipant(concreteMeeting, participant, InviteStatus.PENDING));

        // Execute precise compound cross-reference queries
        Optional<MeetingParticipant> resolvedLinkOpt = participantRepository
                .findByMeetingIdAndUserId(concreteMeeting.getId(), participant.getId());
        Optional<MeetingParticipant> brokenLinkOpt = participantRepository
                .findByMeetingIdAndUserId(concreteMeeting.getId(), 99999L); // Bad User ID

        // Assert structural resolution accuracy
        assertTrue(resolvedLinkOpt.isPresent(),
                "The join entity should be found when passing valid meeting and user identifier combinations");
        assertEquals(InviteStatus.PENDING, resolvedLinkOpt.get().getStatus(),
                "The loaded link record must retain its mutable enum state database flags");

        assertTrue(brokenLinkOpt.isEmpty(),
                "Queries using a non-existent mapping ID must exit with an empty optional state container");
    }
}