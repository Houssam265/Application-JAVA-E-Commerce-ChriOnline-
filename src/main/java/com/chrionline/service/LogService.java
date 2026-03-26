package com.chrionline.service;

import com.chrionline.dao.LogDAO;
import com.chrionline.model.ServerLog;

import java.util.List;

public class LogService {

    private final LogDAO logDAO;

    public LogService(LogDAO logDAO) {
        this.logDAO = logDAO;
    }

    public void log(String action, Integer userId, ServerLog.Status status, String message) {
        try {
            ServerLog log = new ServerLog();
            log.setUserId(userId);
            log.setAction(action);
            log.setStatus(status.name());
            log.setMessage(message);
            logDAO.save(log);
        } catch (Exception e) {
            System.err.println("[LOG] Failed to persist log: " + e.getMessage());
        }
    }

    public void logSuccess(String action, Integer userId) {
        log(action, userId, ServerLog.Status.SUCCESS, null);
    }

    public void logSuccess(String action, Integer userId, String message) {
        log(action, userId, ServerLog.Status.SUCCESS, message);
    }

    public void logError(String action, Integer userId, String message) {
        log(action, userId, ServerLog.Status.ERROR, message);
    }

    public List<ServerLog> getRecentLogs(int limit) {
        return logDAO.findRecent(limit);
    }

    public List<ServerLog> getAllLogs() {
        return logDAO.findAll();
    }

    public List<ServerLog> getLogsByUser(int userId) {
        return logDAO.findByUserId(userId);
    }
}

