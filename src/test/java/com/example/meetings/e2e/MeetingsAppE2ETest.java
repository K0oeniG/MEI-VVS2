package com.example.meetings.e2e;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End (E2E) UI Automation Test Suite
 * This class orchestrates full-stack boundary evaluations using a live embedded
 * server container,
 * an isolated test database registry, and automated Selenium browser
 * interactions.
 * * @author Diogo Carolino 58169
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@ActiveProfiles("test")
@DisplayName("Full-Stack Selenium End-to-End Integration Tests")
class MeetingsAppE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private MeetingParticipantRepository participantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private WebDriver driver;
    private String baseAppUrl;

    @BeforeEach
    void setupDriverAndDatabase() {
        this.baseAppUrl = "http://localhost:" + port;

        // Purge transient relational states to enforce data isolation between clean
        // runs
        participantRepository.deleteAll();
        meetingRepository.deleteAll();
        userRepository.deleteAll();

        // Configure headless Chrome execution for portability in automated environments
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");

        this.driver = new ChromeDriver(options);
    }

    @AfterEach
    void teardownDriverContext() {
        if (this.driver != null) {
            this.driver.quit();
        }
    }

    /**
     * Test Case 1: Registration and Authentication Flow
     */
    @Test
    @DisplayName("E2E -> Guest should register successfully and access the secure calendar dashboard view")
    void fullRegistrationAndLoginWorkflow() {
        driver.get(baseAppUrl + "/register");

        WebElement usernameInput = driver.findElement(By.name("username"));
        WebElement emailInput = driver.findElement(By.name("email"));
        WebElement passwordInput = driver.findElement(By.name("password"));
        WebElement submitButton = driver.findElement(By.cssSelector("button[type='submit']"));

        usernameInput.sendKeys("selenium_diogo");
        emailInput.sendKeys("diogo_e2e@example.com");
        passwordInput.sendKeys("securePass123");
        submitButton.click();

        User structuralRecord = userRepository.findByUsername("selenium_diogo").orElse(null);
        assertNotNull(structuralRecord,
                "The registration workflow must commit a valid entity row to the concrete database");
        assertEquals("diogo_e2e@example.com", structuralRecord.getEmail());

        driver.get(baseAppUrl + "/login");
        driver.findElement(By.name("username")).sendKeys("selenium_diogo");
        driver.findElement(By.name("password")).sendKeys("securePass123");
        driver.findElement(By.cssSelector("button[type='submit']")).click();

        String postAuthUrl = driver.getCurrentUrl();
        assertTrue(postAuthUrl.contains("/calendar"),
                "With correct credentials, the user must land on the /calendar dashboard layout view");

        WebElement dashboardBody = driver.findElement(By.tagName("body"));
        assertTrue(dashboardBody.getText().contains("selenium_diogo"),
                "The authenticated layout template must render the principal name context variable");
    }

    /**
     * Test Case 2: Meeting Proposal Flow
     */
    @Test
    @DisplayName("E2E -> Authenticated user should propose a meeting and confirm it populates the local calendar list layout")
    void proposeMeetingFormSubmissionWorkflow() {
        User organizer = new User("diogo_host", "host@test.com", passwordEncoder.encode("pass"));
        User invitee = new User("alice_invitee", "alice@test.com", passwordEncoder.encode("pass"));
        userRepository.saveAll(List.of(organizer, invitee));

        performProgrammaticLogin("diogo_host", "pass");

        driver.get(baseAppUrl + "/meetings/new");

        driver.findElement(By.name("title")).sendKeys("Strategic VVS Review");
        driver.findElement(By.name("description"))
                .sendKeys("Evaluating automated test frameworks and performance validation tiers.");

        WebElement startInput = driver.findElement(By.name("start"));
        WebElement endInput = driver.findElement(By.name("end"));

        // Temporarily convert date inputs to text fields so sendKeys bypasses native
        // picker UI locks completely
        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
        jsExecutor.executeScript("arguments[0].setAttribute('type', 'text');", startInput);
        jsExecutor.executeScript("arguments[0].setAttribute('type', 'text');", endInput);

        startInput.sendKeys("2026-07-20T10:00");
        endInput.sendKeys("2026-07-20T12:00");

        driver.findElement(By.name("invitees")).sendKeys("alice_invitee");

        // FIX: Target the submit button strictly within the scope of this specific form
        // container.
        // This prevents matching the "Logout" button inside the authenticated navbar
        // header.
        WebElement proposalForm = driver.findElement(By.name("title")).findElement(By.xpath("./ancestor::form"));
        proposalForm.findElement(By.cssSelector("button[type='submit']")).click();

        String currentUrl = driver.getCurrentUrl();
        assertTrue(currentUrl.contains("/calendar"),
                "Submitting a valid meeting proposal form must redirect to the calendar landing grid. Current URL: "
                        + currentUrl);

        String calendarPageContent = driver.findElement(By.tagName("body")).getText();
        assertTrue(calendarPageContent.contains("Strategic VVS Review"),
                "The newly saved meeting metadata title must render explicitly on the host's timeline stream");

        List<Meeting> persistedMeetings = meetingRepository.findCalendarMeetings(organizer);
        assertEquals(1, persistedMeetings.size());
        assertEquals("Strategic VVS Review", persistedMeetings.get(0).getTitle());
    }

    /**
     * Test Case 3: Invitation Interaction and Action Flow
     */
    @Test
    @DisplayName("E2E -> Invitee should view a pending meeting item, accept it, and mutate relational schema variables cleanly")
    void acceptPendingInvitationWorkflow() {
        User host = userRepository.save(new User("manager_one", "man@test.com", passwordEncoder.encode("secure")));
        User invitee = userRepository
                .save(new User("diogo_worker", "worker@test.com", passwordEncoder.encode("secure")));

        Instant startWindow = Instant.now().plus(2, ChronoUnit.DAYS);
        Meeting meeting = new Meeting("Quarterly Sync Alignment", "Core Milestone Analysis", startWindow,
                startWindow.plus(1, ChronoUnit.HOURS), host);
        meeting.addParticipant(new MeetingParticipant(meeting, invitee, InviteStatus.PENDING));
        meetingRepository.save(meeting);

        performProgrammaticLogin("diogo_worker", "secure");

        driver.get(baseAppUrl + "/calendar");

        String pageBodyText = driver.findElement(By.tagName("body")).getText();
        assertTrue(pageBodyText.contains("Quarterly Sync Alignment"),
                "The user's notification region must visibly present the pending invitation context string");

        WebElement acceptButton = driver.findElement(By.cssSelector("form[action*='/respond'] button"));
        assertNotNull(acceptButton,
                "The template layout must present clear clickable action button nodes to handle invitation responses");

        JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
        jsExecutor.executeScript("arguments[0].click();", acceptButton);

        assertTrue(driver.getCurrentUrl().contains("/calendar"),
                "Completing the response action must redirect the user cleanly back to their calendar matrix view");

        List<MeetingParticipant> pendingList = participantRepository.findByUserAndStatus(invitee, InviteStatus.PENDING);
        List<MeetingParticipant> acceptedList = participantRepository.findByUserAndStatus(invitee,
                InviteStatus.ACCEPTED);

        assertTrue(pendingList.isEmpty(), "The pending meeting queue list must empty out upon response completion");
        assertEquals(1, acceptedList.size(),
                "The relationship bridge link entity status must shift cleanly to the ACCEPTED data state enum variable");

        Long meetingId = acceptedList.get(0).getMeeting().getId();
        Meeting updatedMeeting = meetingRepository.findById(meetingId).orElse(null);
        assertNotNull(updatedMeeting);
        assertEquals("Quarterly Sync Alignment", updatedMeeting.getTitle());
    }

    private void performProgrammaticLogin(String username, String password) {
        driver.get(baseAppUrl + "/login");
        driver.findElement(By.name("username")).sendKeys(username);
        driver.findElement(By.name("password")).sendKeys(password);
        driver.findElement(By.cssSelector("button[type='submit']")).click();
    }
}