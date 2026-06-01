package com.example.meetings.controller;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.discover.DiscoveryService;
import com.example.meetings.discover.EventProvider;
import com.example.meetings.model.User;
import com.example.meetings.service.MeetingService;
import com.example.meetings.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.List;

@Controller
public class DiscoveryController {

    private final DiscoveryService discoveryService;
    private final MeetingService meetingService;
    private final UserService userService;

    public DiscoveryController(DiscoveryService discoveryService,
                               MeetingService meetingService,
                               UserService userService) {
        this.discoveryService = discoveryService;
        this.meetingService = meetingService;
        this.userService = userService;
    }

    @GetMapping("/discover")
    public String discover(@RequestParam(required = false) String q, Model model) {
        List<EventProvider> providers = discoveryService.providers();
        boolean anyConfigured = providers.stream().anyMatch(EventProvider::isConfigured);
        model.addAttribute("providers", providers);
        model.addAttribute("anyConfigured", anyConfigured);
        model.addAttribute("q", q == null ? "" : q);
        if (q != null && !q.isBlank() && anyConfigured) {
            model.addAttribute("results", discoveryService.search(q));
        } else {
            model.addAttribute("results", List.of());
        }
        return "discover";
    }

    @PostMapping("/discover/copy")
    public String copy(@AuthenticationPrincipal org.springframework.security.core.userdetails.User principal,
                       @RequestParam String source,
                       @RequestParam String externalId,
                       @RequestParam String title,
                       @RequestParam(required = false) String description,
                       @RequestParam String start,
                       @RequestParam(required = false) String end,
                       @RequestParam(required = false) String url,
                       @RequestParam(required = false) String venue) {
        User user = userService.requireByUsername(principal.getUsername());
        DiscoveredEvent event = new DiscoveredEvent(
                source,
                externalId,
                title,
                description,
                Instant.parse(start),
                end == null || end.isBlank() ? null : Instant.parse(end),
                url,
                venue);
        meetingService.copyFromDiscovered(user, event);
        return "redirect:/calendar";
    }
}
