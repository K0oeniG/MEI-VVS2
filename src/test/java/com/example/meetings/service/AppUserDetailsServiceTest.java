package com.example.meetings.service;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for AppUserDetailsService
 * This classe isolates and validates the behaviour of
 * {@link AppUserDetailsService} using pure unit testing.
 * I used {@link MockitoExtension} since its lightweight and efficient
 * 
 * {@link AppUserDetailsService} bridges our application database layer with
 * Spring Security's authentication engine.
 * Its sole responsability is to translate our domain-level {@link User} entity
 * into a Spring Security compliant {@link UserDetails}
 * contract or throw a standard expection if the user cannot be identified
 * 
 * @author Diogo Carolino 58169
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AppUserDetailsService Unit Tests")
class AppUserDetailsServiceTest {

    /**
     * Mock object replacing the true database persistence layer
     * Reasoning -> We don't want SQL executions or connection setups in this phase.
     * Mocking {@link UserRepository} allows us to control database feedback
     */
    @Mock
    private UserRepository userRepository;

    /**
     * The class under test
     * Reasoning -> Instruct mockito to initiate {@link AppUserDetailsService} and
     * pass the mocked {@code UserRepository} bean into its contructor
     */
    @InjectMocks
    private AppUserDetailsService userDetailsService;

    /**
     * Test Case loadUserByUsername -> Successfull Path
     * Objectives: Verify that when a requested username exists in the database, the
     * service correctly extracts credentials and does the transformation
     * Specific Verifications:
     * - Ensures username matching is successfull
     * - Ensures password hashes match for upstream authentication comparisons
     * - Ensures the standard {@code ROLE_USER} authority is mapped to prevent
     * blocks
     */
    @Test
    @DisplayName("loadUserByUsername - Should return Spring Security User Details when user exists")
    void loadUserByUsername_Success() {

        // Setup the Input and mock expectations
        String username = "alice";
        User mockUser = new User(username, "alice@example.com", "hashed_password");
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(mockUser));

        // Execute a unit of work
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        // Assert 1: The response must be an initialized object
        assertNotNull(userDetails);
        // Assert 2: The username inside the details must match the database
        assertEquals(username, userDetails.getUsername());
        // Assert 3: The password hash must match to PasswordEncoder
        assertEquals("hashed_password", userDetails.getPassword());
        // Assert 4: The user must have ROLE_USER authority
        boolean hasUserRole = userDetails.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER"));
        assertTrue(hasUserRole, "User should have ROLE_USER authority");
        // Assert 5: Confirm that the underlying repository was contacted exactly once
        verify(userRepository, times(1)).findByUsername(username);
    }

    /**
     * Test Case loadUserByUsername - Exception/Edge Case Handling
     * Objectives: Verifity that when an unknown username is request the system
     * terminates properly
     */
    @Test
    @DisplayName("loadUserByUsername - Should throw UsernameNotFoundException when user does not exist")
    void loadUserByUsername_UserNotFound_ThrowsException() {

        // Setup the Input and mock expectations
        String username = "unknown_user";
        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // Assert 1: Ensure execution throws the expected exception
        UsernameNotFoundException exception = assertThrows(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername(username));
        // Assert 2: Validate the message body contents.
        assertTrue(exception.getMessage().contains("Unknown user: " + username));
        // Assert 3: Confirm that the underlying repository was contacted exactly once
        verify(userRepository, times(1)).findByUsername(username);
    }
}