package com.example.meetingagent.service;

import com.example.meetingagent.model.Meeting;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.Events;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class GoogleCalendarService {

    private final Calendar calendarClient;

    @Value("${google.calendar.id:primary}")
    private String calendarId;

    @Value("${google.calendar.zone-id:#{T(java.time.ZoneId).systemDefault().getId()}}")
    private String zoneId;

    public GoogleCalendarService(Calendar calendarClient) {
        this.calendarClient = calendarClient;
    }

    /**
     * Fetches all events on the primary calendar for "today" in the configured
     * time zone, sorted by start time, and maps them to the internal Meeting model.
     */
    public List<Meeting> getTodaysMeetings() throws IOException {
        ZoneId zone = ZoneId.of(zoneId);
        LocalDate today = LocalDate.now(zone);

        DateTime timeMin = new DateTime(Date.from(today.atStartOfDay(zone).toInstant()));
        DateTime timeMax = new DateTime(Date.from(today.plusDays(1).atStartOfDay(zone).toInstant()));

        Events events = calendarClient.events().list(calendarId)
                .setTimeMin(timeMin)
                .setTimeMax(timeMax)
                .setSingleEvents(true)
                .setOrderBy("startTime")
                .execute();

        List<Meeting> meetings = new ArrayList<>();
        for (Event event : events.getItems()) {
            // Skip declined events and events with no time (e.g. all-day placeholders you didn't accept)
            if (isDeclinedByMe(event)) {
                continue;
            }
            meetings.add(toMeeting(event, zone));
        }

        meetings.sort(Comparator.comparing(Meeting::getStart, Comparator.nullsLast(Comparator.naturalOrder())));
        return meetings;
    }

    private boolean isDeclinedByMe(Event event) {
        if (event.getAttendees() == null) {
            return false;
        }
        return event.getAttendees().stream()
                .anyMatch(a -> Boolean.TRUE.equals(a.getSelf()) && "declined".equals(a.getResponseStatus()));
    }

    private Meeting toMeeting(Event event, ZoneId zone) {
        ZonedDateTime start = toZonedDateTime(event.getStart() != null ? event.getStart().getDateTime() : null, zone);
        ZonedDateTime end = toZonedDateTime(event.getEnd() != null ? event.getEnd().getDateTime() : null, zone);

        List<String> attendeeEmails = new ArrayList<>();
        if (event.getAttendees() != null) {
            for (EventAttendee attendee : event.getAttendees()) {
                // Exclude the organizer and any resource/room calendars from the "people" count
                if (Boolean.TRUE.equals(attendee.getResource())) {
                    continue;
                }
                if (attendee.getEmail() != null) {
                    attendeeEmails.add(attendee.getEmail());
                }
            }
        }

        String organizerEmail = event.getOrganizer() != null ? event.getOrganizer().getEmail() : null;
        // If organizer is in the attendee list, don't double count them as "the other person"
        if (organizerEmail != null) {
            attendeeEmails.removeIf(email -> email.equalsIgnoreCase(organizerEmail));
        }

        return new Meeting(
                event.getId(),
                Objects.requireNonNullElse(event.getSummary(), "(No title)"),
                event.getDescription(),
                event.getLocation(),
                organizerEmail,
                start,
                end,
                attendeeEmails
        );
    }

    private ZonedDateTime toZonedDateTime(DateTime dateTime, ZoneId zone) {
        if (dateTime == null) {
            return null;
        }
        return Instant.ofEpochMilli(dateTime.getValue()).atZone(zone);
    }
}
