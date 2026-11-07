package com.scc.dao;

import com.scc.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UserDao {

    private final ActivityLogDao logDao = new ActivityLogDao();

    private User map(ResultSet rs) throws SQLException {
        return new User(rs.getInt("Id"), rs.getString("Username"),
                rs.getString("Password"), rs.getString("Roles"), rs.getString("Status"));
    }

    public boolean registerUser(String username, String password, String roles) {
        String sql = "INSERT INTO Users (Username, Password, Roles, Status) VALUES (?, ?, ?, 'PENDING')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setString(3, roles.toUpperCase());
            pstmt.execute();
            System.out.println("-> Successful Registration, Waiting For Approval!");
            return true;
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
            return false;
        }
    }

    public boolean addUser(String username, String password, String roles) {
        String sql = "INSERT INTO Users (Username, Password, Roles, Status) VALUES (?, ?, ?, 'APPROVED')";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setString(3, roles.toUpperCase());
            pstmt.execute();
            return true;
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
            return false;
        }
    }

    public boolean addUser(int adminId, String username, String password, String roles) {
        boolean added = addUser(username, password, roles);
        if (added) {
            logDao.logAction(adminId, "USER_ADDED", "Admin " + adminId + " added user '"
                    + username + "' as " + roles.toUpperCase());
        }
        return added;
    }

    public User findForLogin(String username, String password, String roles) {
        String sql = "SELECT * FROM Users WHERE Username = ? AND Password = ? AND Roles = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setString(3, roles.toUpperCase());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
        }
        return null;
    }

    public List<User> getAllUsers() {
        return query("SELECT * FROM Users ORDER BY Id");
    }

    public List<User> getPendingUsers() {
        return query("SELECT * FROM Users WHERE Status = 'PENDING' ORDER BY Id");
    }

    private List<User> query(String sql) {
        List<User> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
        }
        return list;
    }

    public boolean approveUser(int userId) {
        String sql = "UPDATE Users SET Status = 'APPROVED' WHERE Id = ? AND Status = 'PENDING'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
            return false;
        }
    }

    public boolean approveUser(int adminId, int userId) {
        boolean approved = approveUser(userId);
        if (approved) {
            logDao.logAction(adminId, "USER_APPROVED",
                    "Admin " + adminId + " approved account for User ID " + userId);
        }
        return approved;
    }

    public boolean updateUserRoleAndStatus(int userId, String newRole, String newStatus) {
        String sql = "UPDATE users SET roles = ?, status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newRole.toUpperCase());
            pstmt.setString(2, newStatus.toUpperCase());
            pstmt.setInt(3, userId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
            return false;
        }
    }

    public boolean updateUserRoleAndStatus(int adminId, int userId, String newRole, String newStatus) {
        boolean updated = updateUserRoleAndStatus(userId, newRole, newStatus);
        if (updated) {
            logDao.logAction(adminId, "USER_UPDATED", "Admin " + adminId + " changed role of User "
                    + userId + " to " + newRole.toUpperCase() + " and status to " + newStatus.toUpperCase());
        }
        return updated;
    }

    public boolean deleteUser(int id) {
        String sql = "DELETE FROM Users WHERE Id = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteUser(int adminId, int id) {
        boolean deleted = deleteUser(id);
        if (deleted) {
            logDao.logAction(adminId, "USER_DELETED",
                    "Admin " + adminId + " deleted account of User ID " + id);
        }
        return deleted;
    }
}