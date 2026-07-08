package com.example.taskflow.domain;

public enum OrgRole {
    ADMIN,      // Organization Admin — creator of the org, full control
    DIRECTOR,   // Senior role — can review across teams, assign across teams
    MANAGER,    // Can assign/review within their team(s)
    EMPLOYEE    // Receives and executes tasks
}
