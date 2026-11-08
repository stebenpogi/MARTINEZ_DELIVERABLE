package com.scc.model;

public record Payment(int paymentId, int loanId, double amountPaid, String paymentDate, String receiptNumber) {
}