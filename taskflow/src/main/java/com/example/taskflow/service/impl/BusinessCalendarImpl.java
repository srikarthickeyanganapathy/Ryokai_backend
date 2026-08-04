package com.example.taskflow.service.impl;

import com.example.taskflow.service.BusinessCalendar;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;

@Service
public class BusinessCalendarImpl implements BusinessCalendar {

    @Override
    public int calculateWorkingDays(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start date and end date must not be null");
        }
        
        if (startDate.isAfter(endDate)) {
            return 0;
        }

        int workingDays = 0;
        LocalDate date = startDate;
        
        while (!date.isAfter(endDate)) {
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                // extensible structure: we can add holiday checks here in the future
                workingDays++;
            }
            date = date.plusDays(1);
        }

        return workingDays;
    }
}
