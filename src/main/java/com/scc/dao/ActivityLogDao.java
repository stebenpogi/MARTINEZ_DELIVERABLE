package com.scc.dao;

import com.scc.model.ActivityLog;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ActivityLogDao {

    private ActivityLog map(ResultSet rs) throws SQLException {
        return new ActivityLog(rs.getInt("id"), rs.getInt("user_id"),
                rs.getString("action_type"), rs.getString("description"), rs.getString("timestamp"));
    }

    public boolean logAction(int userId, String actionType, String description) {
        String sql = "INSERT INTO activity_logs (user_id, action_type, description) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setString(2, actionType);
            pstmt.setString(3, description);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Failed to write activity log: " + e.getMessage());
            return false;
        }
    }

    public List<ActivityLog> getAllLogs() {
        String sql = "SELECT * FROM activity_logs ORDER BY timestamp DESC, id DESC";
        List<ActivityLog> logs = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) logs.add(map(rs));
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
        }
        return logs;
    }

    public List<ActivityLog> getLogsByUserId(int userId) {
        String sql = "SELECT * FROM activity_logs WHERE user_id = ? ORDER BY timestamp DESC, id DESC";
        List<ActivityLog> logs = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) logs.add(map(rs));
            }
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
        }
        return logs;
    }

    public boolean clearLogs() {
        String sql = "DELETE FROM activity_logs";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
            return false;
        }
    }
}