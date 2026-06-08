package com.example.meetings.service;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for UserService
 * This class isolates and validates the behaviour of {@link UserService}
 * using pure unit testing.
 * I used {@link MockitoExtension} since it is lightweight and efficient.
 * {@link UserService} manages user account lifecycles, specifically handling
 * credentials security transformations via a password encoder, ensuring
 * uniqueness constraints during registration, and enforcing mandatory user
 * lookups.
 * 
 * @author Diogo Carolino 58169
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    /**
     * Mock object replacing the true database persistence layer.
     * Reasoning -> We don't want SQL executions or connection setups in this phase.
     */
    @Mock
    private UserRepository userRepository;

    /**
     * Mock object replacing the actual encryption engine layer.
     * Reasoning -> Bypasses heavy cryptographic processing operations during tests,
     * allowing us to immediately simulate predictable string hashing results.
     */
    @Mock
    private PasswordEncoder passwordEncoder;

    // The class under test.
    @InjectMocks
    private UserService userService;

    /**
     * Test Case register -> Successful Registration Path
     * Objectives: Verify that when a new username is completely unique, the
     * registration process correctly secures the raw credentials and commits the
     * entity model.
     * 
     * Specific Verifications:
     * - Ensures duplication safety flags are checked first
     * - Ensures the password hash is computed safely through our security encoder
     * - Ensures fields map properly into the saved target model object
     */
    @Test
    @DisplayName("register - Should secure password and save user when username is unique")
    void register_Success() {

        // Setup the Input and mock expectations
        String username = "bob";
        String email = "bob@example.com";
        String rawPassword = "password123";
        String encodedPassword = "encrypted_hash_value";

        when(userRepository.existsByUsername(username)).thenReturn(false);
        when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);

        // Dynamically mirror back the exact mock instance passed into the repository
        // save operation
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Execute a unit of work
        User registeredUser = userService.register(username, email, rawPassword);

        // Assert 1: The response must be an initialized object
        assertNotNull(registeredUser);

        // Assert 2: Fields inside the returned user object must match initial
        // registrations
        assertEquals(username, registeredUser.getUsername());
        assertEquals(email, registeredUser.getEmail());

        // Assert 3: The plain text password must never be recorded; it must match the
        // hash contract
        assertEquals(encodedPassword, registeredUser.getPasswordHash());

        // Assert 4: Validate interaction sequences to ensure business step safety rules
        // match up
        verify(userRepository, times(1)).existsByUsername(username);
        verify(passwordEncoder, times(1)).encode(rawPassword);
        verify(userRepository, times(1)).save(any(User.class));
    }

    /**
     * Test Case register -> Duplicate Username Edge Case
     * Objectives: Verify that if a username matches an existing database account,
     * the system terminates immediately with an exceptional message constraint.
     */
    @Test
    @DisplayName("register - Should throw IllegalArgumentException when username already exists")
    void register_UsernameTaken_ThrowsException() {

        // Setup the Input and mock expectations reflecting a duplicate match state
        String username = "existing_user";
        when(userRepository.existsByUsername(username)).thenReturn(true);

        // Assert 1: Ensure execution throws the expected exception
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> userService.register(username, "test@test.com", "pass"));

        // Assert 2: Validate that the specific error description is explicit
        assertEquals("Username already taken", exception.getMessage());

        // Assert 3: Confirm that the system fails fast and avoids hitting downline
        // encryptions
        verify(userRepository, times(1)).existsByUsername(username);
        verifyNoMoreInteractions(passwordEncoder, userRepository);
    }

    /**
     * Test Case requireByUsername -> Successful Lookup Path
     * Objectives: Verify that when a requested user exists, the method cleanly
     * unboxes the optional container state and returns the underlying instance
     * object.
     */
    @Test
    @DisplayName("requireByUsername - Should return found User object")
    void requireByUsername_Success() {

        // Setup the Input and mock expectations
        String username = "clara";
        User mockUser = new User(username, "clara@test.com", "hash");
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(mockUser));

        // Execute a unit of work
        User result = userService.requireByUsername(username);

        // Assert 1: The response must be initialized with the requested record data
        assertNotNull(result);
        assertEquals(username, result.getUsername());
    }

    /**
     * Test Case requireByUsername -> User Missing Edge Case
     * Objectives: Verify that if a user search returns an empty database result,the
     * method enforces a strict system error throw block instead of leaking a
     * nullable object.
     */
    @Test
    @DisplayName("requireByUsername - Should throw IllegalArgumentException when username does not map to any database record")
    void requireByUsername_NotFound_ThrowsException() {

        // Setup the Input simulating a missing registry record state
        String username = "missing_user";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // Assert 1: Verify execution throws the expected exception
        assertThrows(IllegalArgumentException.class, () -> userService.requireByUsername(username));
    }
}