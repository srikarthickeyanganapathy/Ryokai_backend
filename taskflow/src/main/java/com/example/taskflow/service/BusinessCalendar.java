package com.example.taskflow.service;

import java.time.LocalDate;

public interface BusinessCalendar {
    int calculateWorkingDays(LocalDate startDate, LocalDate endDate);
}
