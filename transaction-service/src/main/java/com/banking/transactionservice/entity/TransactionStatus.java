package com.banking.transactionservice.entity;

/*
 * Transaction Lifecycle Flow:
 * Pending -> Processing -> Completed (clean transaction)
 * Pending -> Processing -> Pending_Verification (suspicious detected)
 *                              If Verified(otp/phonecall) -> Completed(verified)
 *                              If Flagged -> (SAGA refund)
 *                         ->Failed
 *                         ->Flagged
 */
public enum TransactionStatus {
    PENDING,
    PROCESSING,
    PENDING_VERIFICATION,
    COMPLETED,
    FAILED,
    FLAGGED

}
