package com.example.meetings.controller;

import com.example.meetings.model.Meeting;
import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import com.example.meetings.service.ICalService;
import com.example.meetings.service.MeetingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
public class ICalController {

    private final UserRepository userRepository;
    private final MeetingService meetingService;
    private final ICalService icalService;

    public ICalController(UserRepository userRepository,
                          MeetingService meetingService,
                          ICalService icalService) {
        this.userRepository = userRepository;
        this.meetingService = meetingService;
        this.icalService = icalService;
    }

    @GetMapping(value = "/ical/{token}.ics", produces = "text/calendar")
    public ResponseEntity<byte[]> feed(@PathVariable String token) {
        User user = userRepository.findByIcalToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<Meeting> meetings = meetingService.calendarFor(user);
        byte[] body = icalService.render(user, meetings).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/calendar; charset=UTF-8"))
                .header("Content-Disposition", "inline; filename=\"meetings.ics\"")
                .body(body);
    }
}
