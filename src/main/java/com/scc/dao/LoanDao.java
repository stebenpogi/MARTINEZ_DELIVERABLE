package com.scc.dao;

import com.scc.model.LoanView;
import com.scc.model.Payment;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class LoanDao {

    public static final double MIN_DOWN_RATE = 0.20;
    public static final double MONTHLY_INTEREST_RATE = 0.015;
    public static final List<Integer> TERMS = List.of(6, 12, 18, 24);

    private static final String LOAN_SELECT = """
        SELECT l.LoanID, c.Fullname, e.Brand || ' ' || e.Model AS Ebike, e.Price,
               l.DownPayment, l.TermMonths, l.MonthlyAmount,
               COALESCE((SELECT SUM(p.AmountPaid) FROM Payment p WHERE p.LoanID = l.LoanID), 0) AS Paid,
               l.Loan_Status
        FROM Loan l
        JOIN Customer c ON c.CustomerID = l.CustomerID
        JOIN Ebike e ON e.EbikeID = l.EbikeID
        """;

    private final ActivityLogDao logDao = new ActivityLogDao();

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public static double minimumDownPayment(double price) {
        return round2(price * MIN_DOWN_RATE);
    }

    public static double totalPayable(double price, double downPayment, int termMonths) {
        double financed = price - downPayment;
        return round2(financed * (1 + MONTHLY_INTEREST_RATE * termMonths));
    }

    public static double monthlyAmount(double price, double downPayment, int termMonths) {
        return round2(totalPayable(price, downPayment, termMonths) / termMonths);
    }

    private LoanView mapLoan(ResultSet rs) throws SQLException {
        return new LoanView(rs.getInt("LoanID"), rs.getString("Fullname"), rs.getString("Ebike"),
                rs.getDouble("Price"), rs.getDouble("DownPayment"), rs.getInt("TermMonths"),
                rs.getDouble("MonthlyAmount"), rs.getDouble("Paid"), rs.getString("Loan_Status"));
    }

    public int applyForLoan(int userId, int customerId, int ebikeId, double downPayment,
                            int termMonths, double monthly) {
        String insert = "INSERT INTO Loan (CustomerID, EbikeID, DownPayment, TermMonths, MonthlyAmount, Loan_Status) "
                + "VALUES (?, ?, ?, ?, ?, 'PENDING')";
        int loanId = 0;
        try (Connection conn = DatabaseConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                ps.setInt(1, customerId);
                ps.setInt(2, ebikeId);
                ps.setDouble(3, downPayment);
                ps.setInt(4, termMonths);
                ps.setDouble(5, monthly);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement("SELECT last_insert_rowid()");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) loanId = rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
            return 0;
        }
        if (loanId > 0) {
            logDao.logAction(userId, "LOAN_APPLIED", String.format(
                    "User %d applied for credit: Loan #%d, e-bike #%d, down P%.2f, %d months (P%.2f/month)",
                    userId, loanId, ebikeId, downPayment, termMonths, monthly));
        }
        return loanId;
    }

    public List<LoanView> getLoansByUser(int userId) {
        return queryLoans(LOAN_SELECT + " WHERE c.UserID = ? ORDER BY l.LoanID DESC", userId);
    }

    public List<LoanView> getPendingLoans() {
        return queryLoans(LOAN_SELECT + " WHERE l.Loan_Status = 'PENDING' ORDER BY l.LoanID", null);
    }

    private List<LoanView> queryLoans(String sql, Integer userId) {
        List<LoanView> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (userId != null) pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) list.add(mapLoan(rs));
            }
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
        }
        return list;
    }

    public List<Payment> getPaymentsByUser(int userId) {
        String sql = """
            SELECT p.PaymentID, p.LoanID, p.AmountPaid, p.PaymentDate, p.ReceiptNumber
            FROM Payment p
            JOIN Loan l ON l.LoanID = p.LoanID
            JOIN Customer c ON c.CustomerID = l.CustomerID
            WHERE c.UserID = ?
            ORDER BY p.PaymentID DESC
            """;
        List<Payment> list = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(new Payment(rs.getInt("PaymentID"), rs.getInt("LoanID"),
                            rs.getDouble("AmountPaid"), rs.getString("PaymentDate"),
                            rs.getString("ReceiptNumber")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
        }
        return list;
    }

    public String makePayment(int userId, int loanId, double amount) {
        String check = """
            SELECT l.MonthlyAmount * l.TermMonths
                   - COALESCE((SELECT SUM(p.AmountPaid) FROM Payment p WHERE p.LoanID = l.LoanID), 0) AS Balance,
                   l.Loan_Status
            FROM Loan l
            JOIN Customer c ON c.CustomerID = l.CustomerID
            WHERE l.LoanID = ? AND c.UserID = ?
            """;
        String insert = "INSERT INTO Payment (LoanID, AmountPaid, PaymentDate, ReceiptNumber) "
                + "VALUES (?, ?, datetime('now', 'localtime'), ?)";
        String receipt = "RCPT-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + loanId;
        boolean fullyPaid = false;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                double balance;
                try (PreparedStatement ps = conn.prepareStatement(check)) {
                    ps.setInt(1, loanId);
                    ps.setInt(2, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next() || !"ACTIVE".equalsIgnoreCase(rs.getString("Loan_Status"))) {
                            conn.rollback();
                            return null;
                        }
                        balance = round2(rs.getDouble("Balance"));
                    }
                }
                if (amount <= 0 || amount > balance + 0.005) {
                    conn.rollback();
                    return null;
                }
                try (PreparedStatement ps = conn.prepareStatement(insert)) {
                    ps.setInt(1, loanId);
                    ps.setDouble(2, round2(amount));
                    ps.setString(3, receipt);
                    ps.executeUpdate();
                }
                if (round2(balance - amount) <= 0.005) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE Loan SET Loan_Status = 'PAID' WHERE LoanID = ?")) {
                        ps.setInt(1, loanId);
                        ps.executeUpdate();
                    }
                    fullyPaid = true;
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
            return null;
        }

        logDao.logAction(userId, "PAYMENT_MADE", String.format(
                "User %d paid P%.2f for Loan #%d (Receipt: %s)", userId, amount, loanId, receipt));
        if (fullyPaid) {
            logDao.logAction(userId, "LOAN_PAID", "Loan #" + loanId + " fully paid by User " + userId);
        }
        return receipt;
    }

    public String approveLoan(int adminId, int loanId) {
        String find = """
            SELECT l.EbikeID, e.Stock
            FROM Loan l JOIN Ebike e ON e.EbikeID = l.EbikeID
            WHERE l.LoanID = ? AND l.Loan_Status = 'PENDING'
            """;
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int ebikeId;
                try (PreparedStatement ps = conn.prepareStatement(find)) {
                    ps.setInt(1, loanId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return "loan not found or no longer pending.";
                        }
                        if (rs.getInt("Stock") <= 0) {
                            conn.rollback();
                            return "the e-bike is out of stock.";
                        }
                        ebikeId = rs.getInt("EbikeID");
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE Ebike SET Stock = Stock - 1, "
                                + "Status = CASE WHEN Stock - 1 <= 0 THEN 'UNAVAILABLE' ELSE Status END "
                                + "WHERE EbikeID = ?")) {
                    ps.setInt(1, ebikeId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE Loan SET Loan_Status = 'ACTIVE' WHERE LoanID = ?")) {
                    ps.setInt(1, loanId);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
            return "database error.";
        }
        logDao.logAction(adminId, "LOAN_APPROVED", "Admin " + adminId + " approved Loan #" + loanId);
        return null;
    }

    public boolean rejectLoan(int adminId, int loanId) {
        String sql = "UPDATE Loan SET Loan_Status = 'REJECTED' WHERE LoanID = ? AND Loan_Status = 'PENDING'";
        boolean rejected;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, loanId);
            rejected = pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Failed: " + e.getMessage());
            return false;
        }
        if (rejected) {
            logDao.logAction(adminId, "LOAN_REJECTED", "Admin " + adminId + " rejected Loan #" + loanId);
        }
        return rejected;
    }
}