package com.scc.model;

public record LoanView(int loanId, String customerName, String ebike, double price,
                       double downPayment, int termMonths, double monthlyAmount,
                       double paid, String status) {

    public double totalPayable() {
        return Math.round(monthlyAmount * termMonths * 100.0) / 100.0;
    }

    public double balance() {
        return Math.max(0, Math.round((monthlyAmount * termMonths - paid) * 100.0) / 100.0);
    }
}