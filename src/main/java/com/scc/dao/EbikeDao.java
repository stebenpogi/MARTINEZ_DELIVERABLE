package com.scc.dao;

import com.scc.model.Ebike;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class EbikeDao {

    private final ActivityLogDao logDao = new ActivityLogDao();

    public boolean addEbike(int adminId, String brand, String model, double price, int stock) {
        String sql = "INSERT INTO Ebike (Brand, Model, Price, Stock, Status) VALUES (?, ?, ?, ?, 'AVAILABLE')";
        boolean added;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, brand);
            pstmt.setString(2, model);
            pstmt.setDouble(3, price);
            pstmt.setInt(4, stock);
            added = pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
            return false;
        }
        if (added) {
            logDao.logAction(adminId, "EBIKE_ADDED", String.format(
                    "Admin %d added e-bike %s %s (Price: P%.2f, Stock: %d)",
                    adminId, brand, model, price, stock));
        }
        return added;
    }

    public List<Ebike> getAllEbikes() {
        String sql = "SELECT * FROM Ebike ORDER BY EbikeID";
        List<Ebike> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                list.add(new Ebike(rs.getInt("EbikeID"), rs.getString("Brand"), rs.getString("Model"),
                        rs.getDouble("Price"), rs.getInt("Stock"), rs.getString("Status")));
            }
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
        }
        return list;
    }

    public List<Ebike> getAvailableEbikes() {
        String sql = "SELECT * FROM Ebike WHERE UPPER(Status) = 'AVAILABLE' AND Stock > 0 ORDER BY EbikeID";
        List<Ebike> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                list.add(new Ebike(rs.getInt("EbikeID"), rs.getString("Brand"), rs.getString("Model"),
                        rs.getDouble("Price"), rs.getInt("Stock"), rs.getString("Status")));
            }
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
        }
        return list;
    }

    public boolean updateEbikeStatus(int adminId, int ebikeId, String newStatus) {
        String select = "SELECT Status FROM Ebike WHERE EbikeID = ?";
        String update = "UPDATE Ebike SET Status = ? WHERE EbikeID = ?";
        String oldStatus;
        boolean updated;
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(select)) {
                ps.setInt(1, ebikeId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return false;
                    oldStatus = rs.getString("Status");
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(update)) {
                ps.setString(1, newStatus.toUpperCase());
                ps.setInt(2, ebikeId);
                updated = ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
            return false;
        }
        if (updated) {
            logDao.logAction(adminId, "EBIKE_UPDATED", String.format(
                    "Admin %d changed E-bike #%d status from %s to %s",
                    adminId, ebikeId, oldStatus.toUpperCase(), newStatus.toUpperCase()));
        }
        return updated;
    }
}