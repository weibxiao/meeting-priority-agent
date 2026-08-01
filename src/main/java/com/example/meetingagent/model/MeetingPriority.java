package com.example.meetingagent.model;

/**
 * Priority tiers for today's meetings.
 * Lower rank = higher priority (rank 1 is handled first).
 */
public enum MeetingPriority {

    SMALL_MEETING(1, "Small meeting (1:1, 2 people)"),
    GROUP_MEETING(2, "Group meeting (less than 5 people)"),
    DEPARTMENT_MEETING(3, "Department meeting(10+ people"),
    UNCLASSIFIED(4, "Unclassified / solo block");

    private final int rank;
    private final String label;

    MeetingPriority(int rank, String label) {
        this.rank = rank;
        this.label = label;
    }

    public int getRank() {
        return rank;
    }

    public String getLabel() {
        return label;
    }
}
