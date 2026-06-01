package com.example.meetings.controller;

import com.example.meetings.model.User;
import com.example.meetings.service.MeetingService;
import com.example.meetings.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CalendarController {

    private final MeetingService meetingService;
    private final UserService userService;
    private final String baseUrl;

    public CalendarController(MeetingService meetingService,
                              UserService userService,
                              @Value("${app.base-url}") String baseUrl) {
        this.meetingService = meetingService;
        this.userService = userService;
        this.baseUrl = baseUrl;
    }

    @GetMapping("/calendar")
    public String calendar(@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
                           Model model) {
        User user = userService.requireByUsername(principal.getUsername());
        model.addAttribute("user", user);
        model.addAttribute("meetings", meetingService.calendarFor(user));
        model.addAttribute("pendingInvites", meetingService.pendingInvitesFor(user));
        // webcal:// hints subscribing clients to add the feed as a recurring subscription.
        String httpUrl = baseUrl + "/ical/" + user.getIcalToken() + ".ics";
        model.addAttribute("icalHttpUrl", httpUrl);
        model.addAttribute("icalWebcalUrl", httpUrl.replaceFirst("^https?://", "webcal://"));
        return "calendar";
    }
}
