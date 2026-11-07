package com.scc.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseInitializer {

    private static final String USERS = """
        CREATE TABLE IF NOT EXISTS Users (
            Id       INTEGER PRIMARY KEY AUTOINCREMENT,
            Username TEXT NOT NULL UNIQUE,
            Password TEXT NOT NULL,
            Roles    TEXT NOT NULL,
            Status   TEXT NOT NULL DEFAULT 'PENDING'
        )
        """;

    private static final String CUSTOMER = """
        CREATE TABLE IF NOT EXISTS Customer (
            CustomerID     INTEGER PRIMARY KEY AUTOINCREMENT,
            UserID         INTEGER NOT NULL UNIQUE,
            Fullname       TEXT NOT NULL,
            Contact_Number TEXT,
            Address        TEXT,
            Email          TEXT,
            Status         TEXT NOT NULL DEFAULT 'Active',
            FOREIGN KEY (UserID) REFERENCES Users(Id)
        )
        """;

    private static final String EBIKE = """
        CREATE TABLE IF NOT EXISTS Ebike (
            EbikeID INTEGER PRIMARY KEY AUTOINCREMENT,
            Brand   TEXT NOT NULL,
            Model   TEXT NOT NULL,
            Price   REAL NOT NULL,
            Stock   INTEGER NOT NULL DEFAULT 0,
            Status  TEXT NOT NULL DEFAULT 'Available'
        )
        """;

    private static final String LOAN = """
        CREATE TABLE IF NOT EXISTS Loan (
            LoanID        INTEGER PRIMARY KEY AUTOINCREMENT,
            CustomerID    INTEGER NOT NULL,
            EbikeID       INTEGER NOT NULL,
            DownPayment   REAL NOT NULL DEFAULT 0,
            TermMonths    INTEGER NOT NULL,
            MonthlyAmount REAL NOT NULL,
            Loan_Status   TEXT NOT NULL DEFAULT 'Pending',
            FOREIGN KEY (CustomerID) REFERENCES Customer(CustomerID),
            FOREIGN KEY (EbikeID)    REFERENCES Ebike(EbikeID)
        )
        """;

    private static final String PAYMENT = """
        CREATE TABLE IF NOT EXISTS Payment (
            PaymentID     INTEGER PRIMARY KEY AUTOINCREMENT,
            LoanID        INTEGER NOT NULL,
            AmountPaid    REAL NOT NULL,
            PaymentDate   TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            ReceiptNumber TEXT,
            FOREIGN KEY (LoanID) REFERENCES Loan(LoanID)
        )
        """;

    private static final String ACTIVITY_LOGS = """
        CREATE TABLE IF NOT EXISTS activity_logs (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            user_id     INTEGER NOT NULL,
            action_type TEXT NOT NULL,
            description TEXT,
            timestamp   TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
            FOREIGN KEY (user_id) REFERENCES Users(Id) ON DELETE CASCADE
        )
        """;

    private static final String DEFAULT_ADMIN = """
        INSERT OR IGNORE INTO Users (Username, Password, Roles, Status)
        VALUES (?, ?, ?, ?)
        """;

    private static final String MIGRATE_OLD_STATUS =
            "UPDATE Users SET Status = ? WHERE Status = ?";

    public static boolean initialize() {
        try (Connection conn = DatabaseConnection.getConnection()) {

            if (!foreignKeysEnabled(conn)) {
                System.err.println("Foreign key enforcement could not be enabled.");
                return false;
            }

            migrateOldActivityLogs(conn);

            for (String ddl : new String[]{USERS, CUSTOMER, EBIKE, LOAN, PAYMENT, ACTIVITY_LOGS}) {
                try (PreparedStatement ps = conn.prepareStatement(ddl)) {
                    ps.execute();
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(DEFAULT_ADMIN)) {
                ps.setString(1, "admin");
                ps.setString(2, "admin123");
                ps.setString(3, "ADMIN");
                ps.setString(4, "APPROVED");
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(MIGRATE_OLD_STATUS)) {
                ps.setString(1, "APPROVED");
                ps.setString(2, "ACTIVE");
                ps.executeUpdate();
            }
            return true;

        } catch (SQLException e) {
            System.err.println("Database setup failed: " + e.getMessage());
            return false;
        }
    }

    private static void migrateOldActivityLogs(Connection conn) throws SQLException {
        boolean oldLayout = false;
        try (PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(activity_logs)");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if ("LogID".equalsIgnoreCase(rs.getString("name"))) oldLayout = true;
            }
        }
        if (!oldLayout) return;

        int rows = 0;
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM activity_logs");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) rows = rs.getInt(1);
        }

        String sql = rows == 0
                ? "DROP TABLE activity_logs"
                : "ALTER TABLE activity_logs RENAME TO activity_logs_old";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        }
    }

    private static boolean foreignKeysEnabled(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("PRAGMA foreign_keys");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) == 1;
        }
    }
}