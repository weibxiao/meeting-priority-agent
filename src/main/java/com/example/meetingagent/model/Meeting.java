package com.example.meetingagent.model;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * Simplified, agent-friendly representation of a Google Calendar event.
 */
public class Meeting {

    private String id;
    private String title;
    private String description;
    private String location;
    private String organizerEmail;
    private ZonedDateTime start;
    private ZonedDateTime end;
    private List<String> attendeeEmails;
    private MeetingPriority priority;

    public Meeting() {
    }

    public Meeting(String id, String title, String description, String location, String organizerEmail,
                    ZonedDateTime start, ZonedDateTime end, List<String> attendeeEmails) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.location = location;
        this.organizerEmail = organizerEmail;
        this.start = start;
        this.end = end;
        this.attendeeEmails = attendeeEmails;
    }

    /** Attendee count EXCLUDING the organizer — used for the 2 / 3+ classification rule. */
    public int getAttendeeCount() {
        return attendeeEmails == null ? 0 : attendeeEmails.size();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getOrganizerEmail() {
        return organizerEmail;
    }

    public void setOrganizerEmail(String organizerEmail) {
        this.organizerEmail = organizerEmail;
    }

    public ZonedDateTime getStart() {
        return start;
    }

    public void setStart(ZonedDateTime start) {
        this.start = start;
    }

    public ZonedDateTime getEnd() {
        return end;
    }

    public void setEnd(ZonedDateTime end) {
        this.end = end;
    }

    public List<String> getAttendeeEmails() {
        return attendeeEmails;
    }

    public void setAttendeeEmails(List<String> attendeeEmails) {
        this.attendeeEmails = attendeeEmails;
    }

    public MeetingPriority getPriority() {
        return priority;
    }

    public void setPriority(MeetingPriority priority) {
        this.priority = priority;
    }
}
