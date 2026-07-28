package com.example.taskflow.notification.infrastructure.renderer;

import com.example.taskflow.task.domain.model.Task;
import com.example.taskflow.user.domain.User;
import com.example.taskflow.notification.event.NotificationEvent;

public interface NotificationEmailRenderer {
    boolean supports(NotificationEvent event);
    void renderAndSend(User recipient, User actor, Task task, String dedupKey);
}