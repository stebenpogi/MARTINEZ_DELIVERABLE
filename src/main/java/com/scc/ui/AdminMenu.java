package com.scc.ui;

import com.scc.dao.UserDao;
import com.scc.model.User;

import java.util.List;
import java.util.Scanner;

public class AdminMenu {

    private static final List<String> VALID_ROLES = List.of("USER", "ADMIN");
    private static final List<String> VALID_STATUSES = List.of("APPROVED", "PENDING");

    private final Scanner scanner;
    private final UserDao userDao = new UserDao();

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
            System.out.println("7. Logout");
            System.out.print("Choose an option (1-7): ");

            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1" -> viewAllUsers();
                case "2" -> viewPendingUsers();
                case "3" -> approveUser();
                case "4" -> addUser();
                case "5" -> updateUser(admin);
                case "6" -> deleteUser(admin);
                case "7" -> {
                    System.out.println("Logged out.");
                    loggedIn = false;
                }
                default -> System.out.println("Invalid option. Choose 1-7.");
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

    private void approveUser() {
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
            if (userDao.approveUser(id)) {
                System.out.println("User " + id + " has been APPROVED.");
            } else {
                System.out.println("Approval failed. Please try again.");
            }
            return;
        }
    }

    private void addUser() {
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
        if (userDao.addUser(username, password, role)) {
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

        System.out.println(userDao.updateUserRoleAndStatus(targetUserId, newRole, newStatus)
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
        System.out.println(userDao.deleteUser(id) ? "User deleted." : "Delete failed.");
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