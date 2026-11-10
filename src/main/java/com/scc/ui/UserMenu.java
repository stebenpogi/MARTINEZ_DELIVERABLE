package com.scc.ui;

import com.scc.dao.EbikeDao;
import com.scc.dao.LoanDao;
import com.scc.dao.LoanDao.LoanView;
import com.scc.dao.LoanDao.Payment;
import com.scc.model.Ebike;
import com.scc.model.User;

import java.util.List;
import java.util.Scanner;

public class UserMenu {

    private final Scanner scanner;
    private final EbikeDao ebikeDao = new EbikeDao();
    private final LoanDao loanDao = new LoanDao();

    public UserMenu(Scanner scanner) {
        this.scanner = scanner;
    }

    public void display(User user) {
        boolean loggedIn = true;
        while (loggedIn) {
            System.out.println("\n==========================================");
            System.out.println("              USER DASHBOARD              ");
            System.out.println("==========================================");
            System.out.println("1. View My Account");
            System.out.println("2. Browse Available E-bikes");
            System.out.println("3. Apply for Credit (E-bike Loan)");
            System.out.println("4. My Loans and Balances");
            System.out.println("5. Make a Payment");
            System.out.println("6. Payment History");
            System.out.println("7. Logout");
            System.out.print("Choose an option (1-7): ");

            switch (scanner.nextLine().trim()) {
                case "1" -> System.out.println(user);
                case "2" -> browseEbikes();
                case "3" -> applyForCredit(user);
                case "4" -> viewMyLoans(user);
                case "5" -> makePayment(user);
                case "6" -> viewPaymentHistory(user);
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

    private static String money(double value) {
        return String.format("%,.2f", value);
    }

    private void printEbikeTable(List<Ebike> bikes) {
        String border = "+-------+--------------+-----------------+------------+-------+------------+";
        String row = "| %-5s | %-12s | %-15s | %10s | %-5s | %10s |%n";

        System.out.println("\nAVAILABLE E-BIKES");
        System.out.println(border);
        System.out.printf(row, "ID", "BRAND", "MODEL", "PRICE", "STOCK", "MIN DOWN");
        System.out.println(border);
        for (Ebike b : bikes) {
            System.out.printf(row, b.id(), cut(b.brand(), 12), cut(b.model(), 15),
                    money(b.price()), b.stock(), money(LoanDao.minimumDownPayment(b.price())));
        }
        System.out.println(border);
    }

    private void browseEbikes() {
        List<Ebike> bikes = ebikeDao.getAvailableEbikes();
        if (bikes.isEmpty()) {
            System.out.println("No e-bikes are available right now.");
            return;
        }
        printEbikeTable(bikes);
    }

    private void applyForCredit(User user) {
        List<Ebike> bikes = ebikeDao.getAvailableEbikes();
        if (bikes.isEmpty()) {
            System.out.println("No e-bikes are available right now.");
            return;
        }

        int customerId = loanDao.findCustomerId(user.Id());
        if (customerId == 0) {
            customerId = setUpCustomerProfile(user);
            if (customerId == 0) return;
        }

        printEbikeTable(bikes);
        int ebikeId = readIntInput("Enter E-bike ID to apply for (0 to cancel): ");
        if (ebikeId == 0) {
            System.out.println("Application cancelled.");
            return;
        }
        Ebike bike = bikes.stream().filter(b -> b.id() == ebikeId).findFirst().orElse(null);
        if (bike == null) {
            System.out.println("That e-bike is not in the available list.");
            return;
        }

        double minDown = LoanDao.minimumDownPayment(bike.price());
        double down;
        while (true) {
            down = readMoney(String.format("Down payment (minimum %s, 0 to cancel): ", money(minDown)));
            if (down == 0) {
                System.out.println("Application cancelled.");
                return;
            }
            if (down < minDown) {
                System.out.println("Down payment must be at least " + money(minDown) + ".");
            } else if (down >= bike.price()) {
                System.out.println("Down payment must be less than the price (" + money(bike.price()) + ").");
            } else {
                break;
            }
        }

        int term;
        while (true) {
            term = readIntInput("Term in months " + LoanDao.TERMS + " (0 to cancel): ");
            if (term == 0) {
                System.out.println("Application cancelled.");
                return;
            }
            if (LoanDao.TERMS.contains(term)) break;
            System.out.println("Choose one of: " + LoanDao.TERMS);
        }

        double total = LoanDao.totalPayable(bike.price(), down, term);
        double monthly = LoanDao.monthlyAmount(bike.price(), down, term);

        System.out.println("\n------ CREDIT SUMMARY ------");
        System.out.println("E-bike          : " + bike.brand() + " " + bike.model());
        System.out.println("Cash price      : P" + money(bike.price()));
        System.out.println("Down payment    : P" + money(down));
        System.out.println("Amount financed : P" + money(bike.price() - down));
        System.out.println("Interest        : " + (LoanDao.MONTHLY_INTEREST_RATE * 100) + "% per month (flat)");
        System.out.println("Term            : " + term + " months");
        System.out.println("Total payable   : P" + money(total));
        System.out.println("Monthly payment : P" + money(monthly));
        System.out.print("Type YES to submit this application: ");
        if (!scanner.nextLine().trim().equalsIgnoreCase("YES")) {
            System.out.println("Application cancelled.");
            return;
        }

        int loanId = loanDao.applyForLoan(user.Id(), customerId, bike.id(), down, term, monthly);
        if (loanId > 0) {
            System.out.println("Application submitted (Loan #" + loanId + "). Status: PENDING admin approval.");
        } else {
            System.out.println("Application failed. Please try again.");
        }
    }

    private int setUpCustomerProfile(User user) {
        System.out.println("\nFirst, complete your customer profile to apply for credit.");
        System.out.print("Full name (0 to cancel): ");
        String fullname = scanner.nextLine().trim();
        if (fullname.equals("0")) {
            System.out.println("Cancelled.");
            return 0;
        }
        if (fullname.isEmpty()) {
            System.out.println("Full name cannot be empty.");
            return 0;
        }
        System.out.print("Contact number: ");
        String contact = scanner.nextLine().trim();
        System.out.print("Address: ");
        String address = scanner.nextLine().trim();
        System.out.print("Email: ");
        String email = scanner.nextLine().trim();

        int customerId = loanDao.createCustomer(user.Id(), fullname, contact, address, email);
        if (customerId == 0) {
            System.out.println("Could not save your profile. Please try again.");
        } else {
            System.out.println("Profile saved.");
        }
        return customerId;
    }

    private void printLoanTable(List<LoanView> loans) {
        String border = "+------+----------------------+------------+------+------------+------------+------------+----------+";
        String row = "| %-4s | %-20s | %10s | %-4s | %10s | %10s | %10s | %-8s |%n";

        System.out.println("\nMY LOANS");
        System.out.println(border);
        System.out.printf(row, "LOAN", "E-BIKE", "DOWN", "TERM", "MONTHLY", "PAID", "BALANCE", "STATUS");
        System.out.println(border);
        for (LoanView l : loans) {
            boolean running = l.status().equalsIgnoreCase("ACTIVE") || l.status().equalsIgnoreCase("PAID");
            System.out.printf(row, l.loanId(), cut(l.ebike(), 20), money(l.downPayment()),
                    l.termMonths(), money(l.monthlyAmount()),
                    running ? money(l.paid()) : "-",
                    running ? money(l.balance()) : "-",
                    cut(l.status(), 8));
        }
        System.out.println(border);
    }

    private void viewMyLoans(User user) {
        List<LoanView> loans = loanDao.getLoansByUser(user.Id());
        if (loans.isEmpty()) {
            System.out.println("You have no loan applications yet.");
            return;
        }
        printLoanTable(loans);
    }

    private void makePayment(User user) {
        List<LoanView> active = loanDao.getLoansByUser(user.Id()).stream()
                .filter(l -> l.status().equalsIgnoreCase("ACTIVE"))
                .toList();
        if (active.isEmpty()) {
            System.out.println("You have no active loans to pay. Pending applications need admin approval first.");
            return;
        }
        printLoanTable(active);

        int loanId = readIntInput("Enter Loan ID to pay (0 to cancel): ");
        if (loanId == 0) {
            System.out.println("Payment cancelled.");
            return;
        }
        LoanView loan = active.stream().filter(l -> l.loanId() == loanId).findFirst().orElse(null);
        if (loan == null) {
            System.out.println("That loan is not in your active list.");
            return;
        }

        double balance = loan.balance();
        double amount;
        while (true) {
            amount = readMoney(String.format("Payment amount (monthly %s, balance %s, 0 to cancel): ",
                    money(loan.monthlyAmount()), money(balance)));
            if (amount == 0) {
                System.out.println("Payment cancelled.");
                return;
            }
            if (amount > balance + 0.005) {
                System.out.println("Amount cannot be more than your balance (" + money(balance) + ").");
            } else {
                break;
            }
        }

        String receipt = loanDao.makePayment(user.Id(), loanId, amount);
        if (receipt == null) {
            System.out.println("Payment failed. Please try again.");
            return;
        }
        System.out.println("Payment of P" + money(amount) + " recorded. Receipt: " + receipt);
        if (LoanDao.round2(balance - amount) <= 0.005) {
            System.out.println("Congratulations! Loan #" + loanId + " is fully paid.");
        } else {
            System.out.println("Remaining balance: P" + money(balance - amount));
        }
    }

    private void viewPaymentHistory(User user) {
        List<Payment> payments = loanDao.getPaymentsByUser(user.Id());
        if (payments.isEmpty()) {
            System.out.println("No payments recorded yet.");
            return;
        }
        String border = "+------+------+------------+---------------------+--------------------------+";
        String row = "| %-4s | %-4s | %10s | %-19s | %-24s |%n";

        System.out.println("\nPAYMENT HISTORY");
        System.out.println(border);
        System.out.printf(row, "ID", "LOAN", "AMOUNT", "DATE", "RECEIPT");
        System.out.println(border);
        for (Payment p : payments) {
            System.out.printf(row, p.paymentId(), p.loanId(), money(p.amountPaid()),
                    cut(p.paymentDate(), 19), cut(p.receiptNumber(), 24));
        }
        System.out.println(border);
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

    private double readMoney(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim().replace(",", "");
            try {
                double value = Double.parseDouble(input);
                if (value >= 0 && !Double.isInfinite(value) && !Double.isNaN(value)) return value;
                System.out.println("Please enter a positive amount, or 0 to cancel.");
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter an amount, or 0 to cancel.");
            }
        }
    }
}