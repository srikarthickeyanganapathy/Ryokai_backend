package com.example.taskflow.dto;

import java.util.List;

public class WorkloadDTOs {

    public static class UserWorkloadDTO {
        private UserSummaryDTO user;
        private long todoCount;
        private long inProgressCount;
        private long submittedCount;
        private long approvedCount;
        private long rejectedCount;
        private long totalActiveCount; // todo + inProgress + submitted + rejected

        public UserWorkloadDTO() {}

        public UserWorkloadDTO(UserSummaryDTO user, long todoCount, long inProgressCount,
                                long submittedCount, long approvedCount, long rejectedCount) {
            this.user = user;
            this.todoCount = todoCount;
            this.inProgressCount = inProgressCount;
            this.submittedCount = submittedCount;
            this.approvedCount = approvedCount;
            this.rejectedCount = rejectedCount;
            this.totalActiveCount = todoCount + inProgressCount + submittedCount + rejectedCount;
        }

        public UserSummaryDTO getUser() { return user; }
        public long getTodoCount() { return todoCount; }
        public long getInProgressCount() { return inProgressCount; }
        public long getSubmittedCount() { return submittedCount; }
        public long getApprovedCount() { return approvedCount; }
        public long getRejectedCount() { return rejectedCount; }
        public long getTotalActiveCount() { return totalActiveCount; }
    }
}
