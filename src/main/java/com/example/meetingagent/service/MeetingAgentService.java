package com.example.meetingagent.service;

import com.example.meetingagent.model.Meeting;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The "agent": pulls today's calendar, applies the deterministic priority rules,
 * then asks the LLM to turn that structured data into a short, actionable briefing
 * (what to prep, what could be shortened/declined/delegated, gaps to protect, etc).
 */
@Service
public class MeetingAgentService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("h:mm a");

    private final GoogleCalendarService googleCalendarService;
    private final MeetingPriorityService meetingPriorityService;
    private final ChatClient chatClient;

    public MeetingAgentService(GoogleCalendarService googleCalendarService,
                                MeetingPriorityService meetingPriorityService,
                                ChatClient.Builder chatClientBuilder) {
        this.googleCalendarService = googleCalendarService;
        this.meetingPriorityService = meetingPriorityService;
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        You are a concise executive assistant. You are given today's meetings as json format.

                        Write a short daily briefing that:
                          - Lists the meetings back and sort by the number of attendeeEmails
                          - For each, gives a one-line note on why it matters or how to prep, when the
                            title/description gives you enough to say something useful
                          - Flags any back-to-back meetings with no gap between them
                          - Suggests, if relevant, any low-priority meeting the user might shorten,
                            delegate, or decline given a busy day
                        Keep it tight — bullet points, no fluff, no restating these instructions.
                        """)
                .build();
    }

    /** Fetches, classifies, and sorts today's meetings — no LLM call, just the deterministic pipeline. */
    public List<Meeting> getPrioritizedMeetings() throws IOException {
        List<Meeting> meetings = googleCalendarService.getTodaysMeetings();
        return meetingPriorityService.sortByPriority(meetings);
    }

    /** Full agent run: fetch + classify + sort + generate a natural-language briefing. */
    public String generateDailyBriefing() throws IOException {
        return generateDailyBriefing(getPrioritizedMeetings());
    }

    /** Same as above, but reuses an already-fetched/prioritized list to avoid a duplicate Calendar API call. */
    public String generateDailyBriefing(List<Meeting> prioritized) {
        if (prioritized.isEmpty()) {
            return "You have no meetings on your calendar today. Enjoy the focus time!";
        }

        String meetingSummary = prioritized.stream()
                .map(this::formatMeetingForPrompt)
                .collect(Collectors.joining("\n"));

        String userPrompt = """
                Here are today's meetings, already sorted by priority tier:

                %s

                Write the daily briefing.
                """.formatted(meetingSummary);

        return chatClient.prompt()
                .user(userPrompt)
                .call()
                .content();
    }

    private String formatMeetingForPrompt(Meeting m) {
        String time = (m.getStart() != null ? m.getStart().format(TIME_FMT) : "?")
                + " - " + (m.getEnd() != null ? m.getEnd().format(TIME_FMT) : "?");
        int headcount = (m.getAttendeeEmails() == null ? 0 : m.getAttendeeEmails().size())
                + (m.getOrganizerEmail() != null ? 1 : 0);
        return "- [%s] %s | %s | %d people | %s".formatted(
                m.getPriority().getLabel(),
                m.getTitle(),
                time,
                headcount,
                (m.getDescription() == null || m.getDescription().isBlank()) ? "no description" : m.getDescription()
        );
    }
}
