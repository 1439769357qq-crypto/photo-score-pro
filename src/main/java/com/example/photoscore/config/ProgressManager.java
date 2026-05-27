package com.example.photoscore.config;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProgressManager {
    private final Map<String, ProgressInfo> taskProgress = new ConcurrentHashMap<>();

    public void initTask(String taskId, int total) {
        taskProgress.put(taskId, new ProgressInfo(total, 0, false, null));
    }

    public void updateProgress(String taskId, int processed) {
        ProgressInfo info = taskProgress.get(taskId);
        if (info != null) {
            info.setProcessed(processed);
        }
    }

    public void completeTask(String taskId, Object result) {
        ProgressInfo info = taskProgress.get(taskId);
        if (info != null) {
            info.setCompleted(true);
            info.setResult(result);
        }
    }

    public ProgressInfo getProgress(String taskId) {
        return taskProgress.get(taskId);
    }

    public void cleanTask(String taskId) {
        taskProgress.remove(taskId);
    }

    public static class ProgressInfo {
        private int total;
        private int processed;
        private boolean completed;
        private Object result;

        public ProgressInfo(int total, int processed, boolean completed, Object result) {
            this.total = total;
            this.processed = processed;
            this.completed = completed;
            this.result = result;
        }
        // getters and setters
        public int getTotal() { return total; }
        public void setTotal(int total) { this.total = total; }
        public int getProcessed() { return processed; }
        public void setProcessed(int processed) { this.processed = processed; }
        public boolean isCompleted() { return completed; }
        public void setCompleted(boolean completed) { this.completed = completed; }
        public Object getResult() { return result; }
        public void setResult(Object result) { this.result = result; }
    }
}