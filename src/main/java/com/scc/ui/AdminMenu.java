package com.scc.ui;

import com.scc.dao.ActivityLogDao;
import com.scc.dao.EbikeDao;
import com.scc.dao.LoanDao;
import com.scc.dao.LoanDao.LoanView;
import com.scc.dao.UserDao;
import com.scc.model.ActivityLog;
import com.scc.model.Ebike;
import com.scc.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class AdminMenu {

    private static final List<String> VALID_ROLES = List.of("USER", "ADMIN");
    private static final List<String> VALID_STATUSES = List.of("APPROVED", "PENDING");
    private static final List<String> EBIKE_STATUSES = List.of("AVAILABLE", "UNAVAILABLE");

    private static final String LOG_ROW = "%-5s | %-8s | %-18s | %-35s | %-20s%n";
    private static final int DESCRIPTION_WIDTH = 35;

    private final Scanner scanner;
    private final UserDao userDao = new UserDao();
    private final EbikeDao ebikeDao = new EbikeDao();
    private final ActivityLogDao logDao = new ActivityLogDao();
    private final LoanDao loanDao = new LoanDao();

    public AdminMenu(Scanner scanner) {
        this.scanner = scanner;
    }

    public void display(User admin) {
        boolean loggedIn = true;
        while (loggedIn) {
            System.out.println("\n==========================================");
            System.out.println("            ADMIN DASHBOARD            ");
            System.out.println("==========================================");
            System.out.println("1. View All Users");
            System.out.println("2. View Pending User Approvals");
            System.out.println("3. Approve User Registration");
            System.out.println("4. Add New User");
            System.out.println("5. Update User Role / Status");
            System.out.println("6. Delete User Account");
            System.out.println("7. Add E-bike");
            System.out.println("8. Update E-bike Status");
            System.out.println("9. View System Audit Logs");
            System.out.println("10. Review Loan Applications");
            System.out.println("11. Logout");
            System.out.print("Choose an option (1-11): ");

            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1" -> viewAllUsers();
                case "2" -> viewPendingUsers();
                case "3" -> approveUser(admin);
                case "4" -> addUser(admin);
                case "5" -> updateUser(admin);
                case "6" -> deleteUser(admin);
                case "7" -> addEbike(admin);
                case "8" -> updateEbikeStatus(admin);
                case "9" -> auditLogMenu(admin);
                case "10" -> reviewLoans(admin);
                case "11" -> {
                    System.out.println("Logged out.");
                    loggedIn = false;
                }
                default -> System.out.println("Invalid option. Choose 1-11.");
            }
        }
    }

    private static String cut(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 1) + "~";
    }

    private void printUserTable(String title, List<User> users) {
        String border = "+-------+-----------------+--------------+------------+";
        String row = "| %-5s | %-15s | %-12s | %-10s |%n";

        System.out.println("\n" + title);
        System.out.println(border);
        System.out.printf(row, "ID", "USERNAME", "ROLE", "STATUS");
        System.out.println(border);
        for (User u : users) {
            System.out.printf(row,
                    u.Id(),
                    cut(u.Username(), 15),
                    cut(u.Roles(), 12),
                    cut(u.Status(), 10));
        }
        System.out.println(border);
        System.out.println("Total: " + users.size());
    }

    private void viewAllUsers() {
        List<User> users = userDao.getAllUsers();
        if (users.isEmpty()) {
            System.out.println("No users found.");
            return;
        }
        printUserTable("ALL SYSTEM USERS", users);
    }

    private void viewPendingUsers() {
        List<User> pending = userDao.getPendingUsers();
        if (pending.isEmpty()) {
            System.out.println("No pending registrations.");
            return;
        }
        printUserTable("PENDING REGISTRATIONS", pending);
    }

    private void approveUser(User admin) {
        List<User> pending = userDao.getPendingUsers();
        if (pending.isEmpty()) {
            System.out.println("No pending registrations to approve.");
            return;
        }
        printUserTable("PENDING REGISTRATIONS", pending);

        while (true) {
            int id = readIntInput("Enter User ID to approve (0 to cancel): ");
            if (id == 0) {
                System.out.println("Approval cancelled.");
                return;
            }
            boolean inPendingList = pending.stream().anyMatch(u -> u.Id() == id);
            if (!inPendingList) {
                System.out.println("ID " + id + " is not in the pending list. Try again.");
                continue;
            }
            if (userDao.approveUser(admin.Id(), id)) {
                System.out.println("User " + id + " has been APPROVED.");
            } else {
                System.out.println("Approval failed. Please try again.");
            }
            return;
        }
    }

    private void addUser(User admin) {
        System.out.print("Username (0 to cancel): ");
        String username = scanner.nextLine().trim();
        if (username.equals("0")) {
            System.out.println("Cancelled.");
            return;
        }
        System.out.print("Password: ");
        String password = scanner.nextLine().trim();
        String role = askRole();
        if (role == null) return;
        if (username.isEmpty() || password.isEmpty()) {
            System.out.println("Username and password cannot be empty.");
            return;
        }
        if (userDao.addUser(admin.Id(), username, password, role)) {
            System.out.println("User added (APPROVED).");
        }
    }

    private void updateUser(User admin) {
        List<User> all = userDao.getAllUsers();
        printUserTable("ALL SYSTEM USERS", all);

        int targetUserId = readIntInput("Enter User ID to update (0 to cancel): ");
        if (targetUserId == 0) {
            System.out.println("Update cancelled.");
            return;
        }
        if (targetUserId == admin.Id()) {
            System.out.println("WARNING: Modifying the currently active admin account is restricted.");
            return;
        }
        if (all.stream().noneMatch(u -> u.Id() == targetUserId)) {
            System.out.println("No user with that ID.");
            return;
        }

        String newRole = readValidRole();
        if (newRole == null) {
            System.out.println("Update cancelled.");
            return;
        }

        String newStatus = readValidStatus();
        if (newStatus == null) {
            System.out.println("Update cancelled.");
            return;
        }

        System.out.println(userDao.updateUserRoleAndStatus(admin.Id(), targetUserId, newRole, newStatus)
                ? "User updated." : "Update failed.");
    }

    private void deleteUser(User admin) {
        List<User> all = userDao.getAllUsers();
        printUserTable("ALL SYSTEM USERS", all);
        int id = readIntInput("Enter User ID to delete (0 to cancel): ");
        if (id == 0) {
            System.out.println("Cancelled.");
            return;
        }
        if (id == admin.Id()) {
            System.out.println("You cannot delete your own account.");
            return;
        }
        if (all.stream().noneMatch(u -> u.Id() == id)) {
            System.out.println("No user with that ID.");
            return;
        }
        System.out.print("Type YES to confirm delete: ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("YES")) {
            System.out.println("Cancelled.");
            return;
        }
        System.out.println(userDao.deleteUser(admin.Id(), id) ? "User deleted." : "Delete failed.");
    }

    private void printEbikeTable(List<Ebike> bikes) {
        String border = "+-------+--------------+-----------------+------------+-------+--------------+";
        String row = "| %-5s | %-12s | %-15s | %10s | %-5s | %-12s |%n";

        System.out.println("\nE-BIKE INVENTORY");
        System.out.println(border);
        System.out.printf(row, "ID", "BRAND", "MODEL", "PRICE", "STOCK", "STATUS");
        System.out.println(border);
        for (Ebike b : bikes) {
            System.out.printf(row, b.id(), cut(b.brand(), 12), cut(b.model(), 15),
                    String.format("%.2f", b.price()), b.stock(), cut(b.status(), 12));
        }
        System.out.println(border);
        System.out.println("Total: " + bikes.size());
    }

    private void addEbike(User admin) {
        System.out.print("Brand (0 to cancel): ");
        String brand = scanner.nextLine().trim();
        if (brand.equals("0")) {
            System.out.println("Cancelled.");
            return;
        }
        System.out.print("Model: ");
        String model = scanner.nextLine().trim();
        if (brand.isEmpty() || model.isEmpty()) {
            System.out.println("Brand and model cannot be empty.");
            return;
        }
        double price = readPositiveDouble("Price: ");
        int stock = readStock("Stock quantity: ");

        System.out.println(ebikeDao.addEbike(admin.Id(), brand, model, price, stock)
                ? "E-bike added." : "Failed to add e-bike.");
    }

    private void updateEbikeStatus(User admin) {
        List<Ebike> bikes = ebikeDao.getAllEbikes();
        if (bikes.isEmpty()) {
            System.out.println("No e-bikes recorded yet. Add one first (option 7).");
            return;
        }
        printEbikeTable(bikes);

        int id = readIntInput("Enter E-bike ID to update (0 to cancel): ");
        if (id == 0) {
            System.out.println("Update cancelled.");
            return;
        }
        if (bikes.stream().noneMatch(b -> b.id() == id)) {
            System.out.println("No e-bike with that ID.");
            return;
        }
        String status = readValidChoice("Enter status " + EBIKE_STATUSES + " (0 to cancel): ",
                EBIKE_STATUSES, "status");
        if (status == null) {
            System.out.println("Update cancelled.");
            return;
        }
        System.out.println(ebikeDao.updateEbikeStatus(admin.Id(), id, status)
                ? "E-bike status updated." : "Update failed.");
    }

    private void reviewLoans(User admin) {
        List<LoanView> pending = loanDao.getPendingLoans();
        if (pending.isEmpty()) {
            System.out.println("No pending loan applications.");
            return;
        }
        String border = "+------+-----------------+----------------------+------------+------+------------+";
        String row = "| %-4s | %-15s | %-20s | %10s | %-4s | %10s |%n";

        System.out.println("\nPENDING LOAN APPLICATIONS");
        System.out.println(border);
        System.out.printf(row, "LOAN", "CUSTOMER", "E-BIKE", "DOWN", "TERM", "MONTHLY");
        System.out.println(border);
        for (LoanView l : pending) {
            System.out.printf(row, l.loanId(), cut(l.customerName(), 15), cut(l.ebike(), 20),
                    String.format("%,.2f", l.downPayment()), l.termMonths(),
                    String.format("%,.2f", l.monthlyAmount()));
        }
        System.out.println(border);

        int id = readIntInput("Enter Loan ID to review (0 to cancel): ");
        if (id == 0) {
            System.out.println("Cancelled.");
            return;
        }
        if (pending.stream().noneMatch(l -> l.loanId() == id)) {
            System.out.println("That loan is not in the pending list.");
            return;
        }
        System.out.print("[1] Approve  [2] Reject  [0] Cancel: ");
        switch (scanner.nextLine().trim()) {
            case "1" -> {
                String error = loanDao.approveLoan(admin.Id(), id);
                System.out.println(error == null
                        ? "Loan " + id + " approved. It is now ACTIVE."
                        : "Cannot approve: " + error);
            }
            case "2" -> System.out.println(loanDao.rejectLoan(admin.Id(), id)
                    ? "Loan " + id + " rejected." : "Reject failed.");
            default -> System.out.println("Cancelled.");
        }
    }

    private void auditLogMenu(User admin) {
        boolean back = false;
        while (!back) {
            System.out.println("\n------ SYSTEM AUDIT LOGS ------");
            System.out.println("1. View All System Logs");
            System.out.println("2. Filter Logs by User ID");
            System.out.println("3. Clear All Logs");
            System.out.println("4. Back");
            System.out.print("Choose an option (1-4): ");

            switch (scanner.nextLine().trim()) {
                case "1" -> viewAllLogs();
                case "2" -> filterLogsByUser();
                case "3" -> clearLogs(admin);
                case "4" -> back = true;
                default -> System.out.println("Invalid option. Choose 1-4.");
            }
        }
    }

    private void viewAllLogs() {
        List<ActivityLog> logs = logDao.getAllLogs();
        if (logs.isEmpty()) {
            System.out.println("No activity logs recorded.");
            return;
        }
        printLogTable("ALL SYSTEM LOGS", logs);
    }

    private void filterLogsByUser() {
        int userId = readIntInput("Enter User ID to filter (0 to cancel): ");
        if (userId == 0) {
            System.out.println("Cancelled.");
            return;
        }
        List<ActivityLog> logs = logDao.getLogsByUserId(userId);
        if (logs.isEmpty()) {
            System.out.println("No logs found for User ID " + userId + ".");
            return;
        }
        printLogTable("LOGS FOR USER ID " + userId, logs);
    }

    private void clearLogs(User admin) {
        System.out.print("This permanently deletes ALL logs. Type YES to confirm: ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("YES")) {
            System.out.println("Cancelled.");
            return;
        }
        if (logDao.clearLogs()) {
            logDao.logAction(admin.Id(), "LOGS_CLEARED", "Admin " + admin.Id() + " cleared all system logs.");
            System.out.println("All logs cleared.");
        } else {
            System.out.println("Failed to clear logs.");
        }
    }

    private void printLogTable(String title, List<ActivityLog> logs) {
        String line = "-".repeat(5 + 8 + 18 + DESCRIPTION_WIDTH + 20 + 12);

        System.out.println("\n" + title);
        System.out.println(line);
        System.out.printf(LOG_ROW, "ID", "USER ID", "ACTION", "DESCRIPTION", "TIMESTAMP");
        System.out.println(line);
        for (ActivityLog log : logs) {
            List<String> parts = wrap(log.description(), DESCRIPTION_WIDTH);
            System.out.printf(LOG_ROW, log.id(), log.userId(), cut(log.actionType(), 18),
                    parts.get(0), cut(log.timestamp(), 20));
            for (int i = 1; i < parts.size(); i++) {
                System.out.printf(LOG_ROW, "", "", "", parts.get(i), "");
            }
        }
        System.out.println(line);
        System.out.println("Total: " + logs.size());
    }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            lines.add("");
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            while (word.length() > width) {
                if (current.length() > 0) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                lines.add(word.substring(0, width));
                word = word.substring(width);
            }
            if (current.length() == 0) {
                current.append(word);
            } else if (current.length() + 1 + word.length() <= width) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines;
    }

    private int readIntInput(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(input);
                if (value >= 0) return value;
                System.out.println("Please enter a positive number, or 0 to cancel.");
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number, or 0 to cancel.");
            }
        }
    }

    private int readStock(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(input);
                if (value >= 0) return value;
                System.out.println("Stock cannot be negative.");
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a whole number.");
            }
        }
    }

    private double readPositiveDouble(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                double value = Double.parseDouble(input);
                if (value > 0 && !Double.isInfinite(value)) return value;
                System.out.println("Price must be greater than 0.");
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
            }
        }
    }

    private String readValidRole() {
        return readValidChoice("Enter role " + VALID_ROLES + " (0 to cancel): ", VALID_ROLES, "role");
    }

    private String readValidStatus() {
        return readValidChoice("Enter status " + VALID_STATUSES + " (0 to cancel): ", VALID_STATUSES, "status");
    }

    private String readValidChoice(String prompt, List<String> allowed, String label) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim().toUpperCase();
            if (input.equals("0")) return null;
            if (allowed.contains(input)) return input;
            System.out.println("Invalid " + label + ". Choose one of: " + allowed);
        }
    }

    private String askRole() {
        System.out.print("Role [1] USER [2] ADMIN: ");
        String r = scanner.nextLine().trim();
        if (r.equals("1")) return "USER";
        if (r.equals("2")) return "ADMIN";
        System.out.println("Invalid role.");
        return null;
    }
}