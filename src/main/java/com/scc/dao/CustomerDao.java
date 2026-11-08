package com.scc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CustomerDao {

    public int findCustomerId(int userId) {
        String sql = "SELECT CustomerID FROM Customer WHERE UserID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt("CustomerID");
            }
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
        }
        return 0;
    }

    public int createCustomer(int userId, String fullname, String contactNumber, String address, String email) {
        String sql = "INSERT INTO Customer (UserID, Fullname, Contact_Number, Address, Email) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.setString(2, fullname);
            pstmt.setString(3, contactNumber);
            pstmt.setString(4, address);
            pstmt.setString(5, email);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
            return 0;
        }
        return findCustomerId(userId);
    }
}