package com.example.meetingagent.controller;

import com.example.meetingagent.model.Meeting;
import com.example.meetingagent.service.MeetingAgentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
public class MeetingController {

    private final MeetingAgentService meetingAgentService;

    public MeetingController(MeetingAgentService meetingAgentService) {
        this.meetingAgentService = meetingAgentService;
    }

    /** Raw prioritized list — no LLM call, fast, good for a UI to render directly. */
    @GetMapping("/api/meetings/today")
    public List<Meeting> getTodaysPrioritizedMeetings() throws IOException {
        return meetingAgentService.getPrioritizedMeetings();
    }

    /** Full agent output — prioritized list plus an LLM-generated natural-language briefing. */
    @GetMapping("/api/meetings/today/briefing")
    public Map<String, Object> getTodaysBriefing() throws IOException {
        List<Meeting> meetings = meetingAgentService.getPrioritizedMeetings();
        String briefing = meetingAgentService.generateDailyBriefing(meetings);
        return Map.of(
                "meetings", meetings,
                "briefing", briefing
        );
    }
    @GetMapping("/Callback")
    public String callback() {
    	return "Got it";
    }
}
