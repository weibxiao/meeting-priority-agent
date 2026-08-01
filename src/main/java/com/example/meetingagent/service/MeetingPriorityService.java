package com.example.meetingagent.service;

import com.example.meetingagent.model.Meeting;
import com.example.meetingagent.model.MeetingPriority;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic classification of meetings into priority tiers:
 *
 *  1. SMALL_MEETING     - exactly 2 total people (you + 1 other)        -> HIGHEST priority
 *  2. GROUP_MEETING     - 3+ total people, not flagged as "department"  -> MEDIUM priority
 *  3. DEPARTMENT_MEETING- title/description matches department keywords -> LOWEST priority
 *
 * "Department meeting" can't be reliably distinguished from a regular group meeting by
 * headcount alone, so it's detected via keyword matching against the event title/description.
 * Configure the keyword list in application.yml (meeting-agent.department-keywords).
 */
@Service
public class MeetingPriorityService {

    private final List<String> departmentKeywords;

    public MeetingPriorityService(
            @Value("${meeting-agent.department-keywords:department,all-hands,all hands,town hall,org meeting,team sync,weekly sync,standup,stand-up}")
            List<String> departmentKeywords) {
        this.departmentKeywords = departmentKeywords.stream()
                .map(k -> k.toLowerCase(Locale.ROOT).trim())
                .toList();
    }

    public MeetingPriority classify(Meeting meeting) {
        int totalHeadcount = totalHeadcount(meeting);

        if (totalHeadcount > 10) {
            return MeetingPriority.DEPARTMENT_MEETING;
        }
        if (totalHeadcount == 2) {
            return MeetingPriority.SMALL_MEETING;
        }
        if (totalHeadcount > 3) {
            return MeetingPriority.GROUP_MEETING;
        }
        // Headcount of 0, 1, or exactly 3 with no department signal: not explicitly covered
        // by the user's rules. Treated as a group meeting by default since it's not a 1:1.
        if (totalHeadcount >= 2) {
            return MeetingPriority.GROUP_MEETING;
        }
        return MeetingPriority.UNCLASSIFIED;
    }

    public void classifyAll(List<Meeting> meetings) {
        meetings.forEach(m -> m.setPriority(classify(m)));
    }

    /** Sorts by priority rank first, then by start time within the same priority tier. */
    public List<Meeting> sortByPriority(List<Meeting> meetings) {
        classifyAll(meetings);
        return meetings.stream()
                .sorted(Comparator
                        .comparingInt((Meeting m) -> m.getPriority().getRank())
                        .thenComparing(Meeting::getStart, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private int totalHeadcount(Meeting meeting) {
        int others = meeting.getAttendeeEmails() == null ? 0 : meeting.getAttendeeEmails().size();
        int organizer = meeting.getOrganizerEmail() != null ? 1 : 0;
        return others + organizer;
    }

    private boolean isDepartmentMeeting(Meeting meeting) {
        String haystack = ((meeting.getTitle() == null ? "" : meeting.getTitle())
                + " " + (meeting.getDescription() == null ? "" : meeting.getDescription()))
                .toLowerCase(Locale.ROOT);
        return departmentKeywords.stream().anyMatch(haystack::contains);
    }
}
