package com.example.meetings.repository;

import com.example.meetings.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concrete Database Integration Tests for UserRepository
 * This class validates framework-derived keyword queries and unique identity
 * lookup rows against the persistent test database using {@link DataJpaTest}.
 * Reasoning -> Ensures that entity property mappings (username, email,
 * icalToken) map correctly to their respective table columns and comply with
 * unique data constraints.
 * 
 * @author Diogo Carolino 58169
 */
@DataJpaTest
@DisplayName("UserRepository Concrete Database Integration Tests")
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    /**
     * Test Case findByUsername -> Active Resolution Path
     * Objectives: Verify that querying an existing username successfully extracts
     * the correct model object, and querying a non-existent identifier returns an
     * empty Optional state safely.
     */
    @Test
    @DisplayName("findByUsername - Should locate accurate user row profile or exit with empty Optional state")
    void findByUsername_ReturnsUserOrEmpty() {

        // Setup base target records
        User savedUser = userRepository.save(new User("diogo_dev", "diogo@dev.com", "securehash"));

        // Execute lookups
        Optional<User> foundUserOpt = userRepository.findByUsername("diogo_dev");
        Optional<User> missingUserOpt = userRepository.findByUsername("unknown_ghost");

        // Assertions
        assertTrue(foundUserOpt.isPresent(), "An existing persistent username must be found");
        assertEquals("diogo@dev.com", foundUserOpt.get().getEmail(),
                "The recovered user email must match the initialized row values");

        assertTrue(missingUserOpt.isEmpty(),
                "Searching for a missing username must result in an empty optional container");
    }

    /**
     * Test Case findByIcalToken -> Calendar Feed Token Resolution Path
     * Objectives: Validate that the cryptographic feed token uniquely maps back to
     * a single specific user registry record.
     */
    @Test
    @DisplayName("findByIcalToken - Should successfully match unique feed tokens to user data rows")
    void findByIcalToken_ResolvesTokenCorrectly() {

        User user = new User("token_tester", "token@test.com", "hash");
        // Ensure the token exists in the model context before saving
        String customToken = user.getIcalToken();
        userRepository.save(user);

        Optional<User> resolvedUserOpt = userRepository.findByIcalToken(customToken);

        assertTrue(resolvedUserOpt.isPresent(),
                "The system must resolve active calendars using their unique token hashes");
        assertEquals("token_tester", resolvedUserOpt.get().getUsername());
    }

    /**
     * Test Case existsByUsername -> Boolean Existence Gate Path
     * Objectives: Confirm that registration guard checks evaluate to true for
     * existing rows and false for available strings.
     */
    @Test
    @DisplayName("existsByUsername - Should accurately switch boolean statuses for registration validation guards")
    void existsByUsername_ReturnsAccurateBooleanFlags() {

        userRepository.save(new User("duplicate_check", "dup@check.com", "hash"));

        boolean formatTaken = userRepository.existsByUsername("duplicate_check");
        boolean formatFree = userRepository.existsByUsername("available_name");

        assertTrue(formatTaken, "Must flag true when looking up an already registered username entry");
        assertFalse(formatFree, "Must return false when looking up a completely fresh registration username string");
    }
}