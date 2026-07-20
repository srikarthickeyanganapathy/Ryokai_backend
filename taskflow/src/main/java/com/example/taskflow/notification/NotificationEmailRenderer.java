package com.example.taskflow.notification;

import com.example.taskflow.domain.Task;
import com.example.taskflow.domain.User;

public interface NotificationEmailRenderer {
    boolean supports(NotificationEvent event);
    void renderAndSend(User recipient, User actor, Task task, String dedupKey);
}
