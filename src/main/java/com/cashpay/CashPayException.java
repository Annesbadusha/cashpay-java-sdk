package com.cashpay;

import com.google.gson.JsonElement;

/**
 * Exception thrown for CashPay API errors
 */
public class CashPayException extends Exception {
    private final int statusCode;
    private final String errorCode;
    private final JsonElement details;

    public CashPayException(String message, int statusCode, String errorCode, JsonElement details) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
        this.details = details;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public JsonElement getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return String.format("CashPayException(%s): %s [status=%d]", errorCode, getMessage(), statusCode);
    }
}
