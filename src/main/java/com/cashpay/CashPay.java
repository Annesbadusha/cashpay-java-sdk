package com.cashpay;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * CashPay Java SDK
 * Official SDK for integrating with CashPay Payment Gateway
 */
public class CashPay {
    private final String apiKey;
    private final String apiSecret;
    private final String baseUrl;
    private final OkHttpClient client;
    private final Gson gson;

    private final Balance balance;
    private final Payins payins;
    private final Payouts payouts;
    private final Settlements settlements;
    private final Beneficiaries beneficiaries;
    private final BankAccounts bankAccounts;
    private final PaymentLinks paymentLinks;

    private CashPay(Builder builder) {
        this.apiKey = builder.apiKey;
        this.apiSecret = builder.apiSecret;
        this.baseUrl = builder.baseUrl;
        this.gson = new Gson();

        this.client = new OkHttpClient.Builder()
                .connectTimeout(builder.timeout, TimeUnit.SECONDS)
                .readTimeout(builder.timeout, TimeUnit.SECONDS)
                .writeTimeout(builder.timeout, TimeUnit.SECONDS)
                .build();

        this.balance = new Balance(this);
        this.payins = new Payins(this);
        this.payouts = new Payouts(this);
        this.settlements = new Settlements(this);
        this.beneficiaries = new Beneficiaries(this);
        this.bankAccounts = new BankAccounts(this);
        this.paymentLinks = new PaymentLinks(this);
    }

    public Balance balance() { return balance; }
    public Payins payins() { return payins; }
    public Payouts payouts() { return payouts; }
    public Settlements settlements() { return settlements; }
    public Beneficiaries beneficiaries() { return beneficiaries; }
    public BankAccounts bankAccounts() { return bankAccounts; }
    public PaymentLinks paymentLinks() { return paymentLinks; }

    /**
     * Generate HMAC-SHA256 signature
     */
    private String generateSignature(String timestamp, String method, String path, String body) {
        try {
            String message = timestamp + method + path + body;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate signature", e);
        }
    }

    /**
     * Make an authenticated API request
     */
    JsonObject request(String method, String path, Map<String, String> queryParams, 
                       JsonObject body, Map<String, String> extraHeaders) throws CashPayException {
        try {
            HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + path).newBuilder();
            HttpUrl.Builder tempSigUrlBuilder = HttpUrl.parse("http://localhost" + path).newBuilder();
            
            if (queryParams != null) {
                for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                    urlBuilder.addQueryParameter(entry.getKey(), entry.getValue());
                    tempSigUrlBuilder.addQueryParameter(entry.getKey(), entry.getValue());
                }
            }
            
            HttpUrl sigUrl = tempSigUrlBuilder.build();
            String signaturePath = sigUrl.encodedPath() + (sigUrl.encodedQuery() != null ? "?" + sigUrl.encodedQuery() : "");

            String timestamp = String.valueOf(System.currentTimeMillis());
            String bodyStr = body != null ? gson.toJson(body) : "";
            String signature = generateSignature(timestamp, method, signaturePath, bodyStr);

            Request.Builder requestBuilder = new Request.Builder()
                    .url(urlBuilder.build())
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("x-timestamp", timestamp)
                    .header("x-signature", signature);

            if (extraHeaders != null) {
                for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                    requestBuilder.header(entry.getKey(), entry.getValue());
                }
            }

            RequestBody requestBody = body != null 
                    ? RequestBody.create(bodyStr, MediaType.parse("application/json"))
                    : null;

            switch (method.toUpperCase()) {
                case "GET":
                    requestBuilder.get();
                    break;
                case "POST":
                    requestBuilder.post(requestBody != null ? requestBody : RequestBody.create("", null));
                    break;
                case "PUT":
                    requestBuilder.put(requestBody);
                    break;
                case "PATCH":
                    requestBuilder.patch(requestBody != null ? requestBody : RequestBody.create("", null));
                    break;
                case "DELETE":
                    if (requestBody != null) {
                        requestBuilder.delete(requestBody);
                    } else {
                        requestBuilder.delete();
                    }
                    break;
            }

            try (Response response = client.newCall(requestBuilder.build()).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                
                if (!response.isSuccessful()) {
                    JsonObject errorData;
                    try {
                        errorData = gson.fromJson(responseBody, JsonObject.class);
                    } catch (Exception e) {
                         throw new CashPayException(responseBody, response.code(), "UNKNOWN_ERROR", null);
                    }
                    if (errorData == null) {
                        errorData = new JsonObject();
                    }
                    
                    throw new CashPayException(
                            errorData.has("message") ? errorData.get("message").getAsString() : "Unknown error",
                            response.code(),
                            errorData.has("error") ? errorData.get("error").getAsString() : "UNKNOWN_ERROR",
                            errorData.has("details") ? errorData.get("details") : null
                    );
                }

                if (responseBody.isEmpty()) {
                    return new JsonObject();
                }
                return gson.fromJson(responseBody, JsonObject.class);
            }
        } catch (IOException e) {
            throw new CashPayException("Network error: " + e.getMessage(), 0, "NETWORK_ERROR", null);
        }
    }

    /**
     * Verify webhook signature
     */
    public boolean verifyWebhook(String payload, String signature, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return MessageDigest.isEqual(hexString.toString().getBytes(), signature.getBytes());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Builder for CashPay client
     */
    public static class Builder {
        private String apiKey;
        private String apiSecret;
        private String baseUrl;
        private int timeout = 30;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder apiSecret(String apiSecret) {
            this.apiSecret = apiSecret;
            return this;
        }

        public Builder environment(String environment) {
            if ("sandbox".equals(environment)) {
                this.baseUrl = "https://sandbox.cashpay.com/api";
            } else {
                this.baseUrl = "https://api.cashpay.com/api";
            }
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder timeout(int timeout) {
            this.timeout = timeout;
            return this;
        }

        public CashPay build() {
            if (apiKey == null || apiSecret == null) {
                throw new IllegalArgumentException("apiKey and apiSecret are required");
            }
            if (baseUrl == null) {
                baseUrl = "https://api.cashpay.com/api";
            }
            return new CashPay(this);
        }
    }

    // ============================================
    // API CLASSES
    // ============================================

    public static class Balance {
        private final CashPay client;

        Balance(CashPay client) { this.client = client; }

        public JsonObject get() throws CashPayException {
            return client.request("GET", "/v1/balance", null, null, null);
        }

        public JsonObject getSettlement() throws CashPayException {
            return client.request("GET", "/v1/balance/settlement", null, null, null);
        }

        public JsonObject getPayout() throws CashPayException {
            return client.request("GET", "/v1/balance/payout", null, null, null);
        }
    }

    public static class Payins {
        private final CashPay client;

        Payins(CashPay client) { this.client = client; }

        public JsonObject getByOrderId(String orderId) throws CashPayException {
            Map<String, String> params = new HashMap<>();
            params.put("orderId", orderId);
            return client.request("GET", "/v1/payin", params, null, null);
        }

        public JsonObject getStatus(String paymentId) throws CashPayException {
            return client.request("GET", "/v1/payin/" + paymentId + "/status", null, null, null);
        }

        public JsonObject createIntent(JsonObject params, String idempotencyKey) throws CashPayException {
            Map<String, String> headers = new HashMap<>();
            if (idempotencyKey != null) headers.put("x-idempotency-key", idempotencyKey);
            return client.request("POST", "/v1/payin/intent", null, params, headers);
        }

        public JsonObject createCard(JsonObject params, String idempotencyKey) throws CashPayException {
            Map<String, String> headers = new HashMap<>();
            if (idempotencyKey != null) headers.put("x-idempotency-key", idempotencyKey);
            return client.request("POST", "/v1/payin/card", null, params, headers);
        }

        public JsonObject createNetBanking(JsonObject params, String idempotencyKey) throws CashPayException {
            Map<String, String> headers = new HashMap<>();
            if (idempotencyKey != null) headers.put("x-idempotency-key", idempotencyKey);
            return client.request("POST", "/v1/payin/netbanking", null, params, headers);
        }

        public JsonObject createUpiCollect(JsonObject params, String idempotencyKey) throws CashPayException {
            Map<String, String> headers = new HashMap<>();
            if (idempotencyKey != null) headers.put("x-idempotency-key", idempotencyKey);
            return client.request("POST", "/v1/payin/upi-collect", null, params, headers);
        }

        public JsonObject createWallet(JsonObject params, String idempotencyKey) throws CashPayException {
            Map<String, String> headers = new HashMap<>();
            if (idempotencyKey != null) headers.put("x-idempotency-key", idempotencyKey);
            return client.request("POST", "/v1/payin/wallet", null, params, headers);
        }

        public JsonObject createPaymentPage(JsonObject params, String idempotencyKey) throws CashPayException {
            Map<String, String> headers = new HashMap<>();
            if (idempotencyKey != null) headers.put("x-idempotency-key", idempotencyKey);
            
            // Backend uses paymentPageEnabled: true
            params.addProperty("paymentPageEnabled", true);
            if (!params.has("currency")) params.addProperty("currency", "INR");
            
            JsonObject response = client.request("POST", "/payins", null, params, headers);
            
            if (response.has("paymentId") || response.has("id")) {
                String paymentId = response.has("paymentId") 
                    ? response.get("paymentId").getAsString() 
                    : response.get("id").getAsString();
                response.addProperty("paymentUrl", "https://cashpayy.com/pay/" + paymentId);
            }
            
            return response;
        }

        public JsonObject createQrP2p(JsonObject params, String idempotencyKey) throws CashPayException {
            Map<String, String> headers = new HashMap<>();
            if (idempotencyKey != null) headers.put("x-idempotency-key", idempotencyKey);
            
            params.addProperty("qrEnabled", true);
            if (!params.has("currency")) params.addProperty("currency", "INR");
            
            return client.request("POST", "/payins", null, params, headers);
        }
    }

    public static class Payouts {
        private final CashPay client;
        private final Gson gson = new Gson();

        Payouts(CashPay client) { this.client = client; }

        public JsonObject create(String beneficiaryId, int amount, String referenceId,
                                 String narration, String mode, String idempotencyKey) throws CashPayException {
            JsonObject body = new JsonObject();
            body.addProperty("beneficiaryId", beneficiaryId);
            body.addProperty("amount", amount);
            body.addProperty("mode", mode != null ? mode : "IMPS");
            if (referenceId != null) body.addProperty("referenceId", referenceId);
            if (narration != null) body.addProperty("narration", narration);

            Map<String, String> headers = new HashMap<>();
            if (idempotencyKey != null) headers.put("x-idempotency-key", idempotencyKey);

            return client.request("POST", "/v1/payouts", null, body, headers);
        }

        public JsonObject createBulk(List<Map<String, Object>> payouts, String idempotencyKey) throws CashPayException {
            JsonObject body = new JsonObject();
            body.add("payouts", gson.toJsonTree(payouts));

            Map<String, String> headers = new HashMap<>();
            if (idempotencyKey != null) headers.put("x-idempotency-key", idempotencyKey);

            return client.request("POST", "/v1/payouts/bulk", null, body, headers);
        }

        public JsonObject list(int page, int limit, String status, String referenceId) throws CashPayException {
            Map<String, String> params = new HashMap<>();
            params.put("page", String.valueOf(page));
            params.put("limit", String.valueOf(limit));
            if (status != null) params.put("status", status);
            if (referenceId != null) params.put("referenceId", referenceId);

            return client.request("GET", "/v1/payouts", params, null, null);
        }

        public JsonObject get(String payoutId) throws CashPayException {
            return client.request("GET", "/v1/payouts/" + payoutId, null, null, null);
        }

        public JsonObject getByReferenceId(String referenceId) throws CashPayException {
            JsonObject result = this.list(1, 1, null, referenceId);
            if (result.has("data") && result.getAsJsonArray("data").size() > 0) {
                return result.getAsJsonArray("data").get(0).getAsJsonObject();
            }
            throw new CashPayException("Payout not found with reference_id: " + referenceId, 404, "NOT_FOUND", null);
        }

        public JsonObject cancel(String payoutId) throws CashPayException {
            return client.request("POST", "/v1/payouts/" + payoutId + "/cancel", null, null, null);
        }
    }

    public static class Settlements {
        private final CashPay client;
        private final Gson gson = new Gson();

        Settlements(CashPay client) { this.client = client; }

        public JsonObject create(int amount, String bankAccountId, String accountNumber,
                                 String ifsc, String accountHolderName, String referenceId,
                                 String idempotencyKey) throws CashPayException {
            JsonObject body = new JsonObject();
            body.addProperty("amount", amount);
            if (bankAccountId != null) body.addProperty("bankAccountId", bankAccountId);
            if (accountNumber != null) body.addProperty("accountNumber", accountNumber);
            if (ifsc != null) body.addProperty("ifsc", ifsc);
            if (accountHolderName != null) body.addProperty("accountHolderName", accountHolderName);
            if (referenceId != null) body.addProperty("referenceId", referenceId);

            Map<String, String> headers = new HashMap<>();
            if (idempotencyKey != null) headers.put("x-idempotency-key", idempotencyKey);

            return client.request("POST", "/v1/settlements", null, body, headers);
        }

        public JsonObject createBulk(List<Map<String, Object>> settlements, String idempotencyKey) throws CashPayException {
            JsonObject body = new JsonObject();
            body.add("settlements", gson.toJsonTree(settlements));

            Map<String, String> headers = new HashMap<>();
            if (idempotencyKey != null) headers.put("x-idempotency-key", idempotencyKey);

            return client.request("POST", "/v1/settlements/bulk", null, body, headers);
        }

        public JsonObject list(int page, int limit, String status, String referenceId) throws CashPayException {
            Map<String, String> params = new HashMap<>();
            params.put("page", String.valueOf(page));
            params.put("limit", String.valueOf(limit));
            if (status != null) params.put("status", status);
            if (referenceId != null) params.put("referenceId", referenceId);

            return client.request("GET", "/v1/settlements", params, null, null);
        }

        public JsonObject get(String settlementId) throws CashPayException {
            return client.request("GET", "/v1/settlements/" + settlementId, null, null, null);
        }

        public JsonObject getByReferenceId(String referenceId) throws CashPayException {
            JsonObject result = this.list(1, 1, null, referenceId);
            if (result.has("data") && result.getAsJsonArray("data").size() > 0) {
                return result.getAsJsonArray("data").get(0).getAsJsonObject();
            }
            throw new CashPayException("Settlement not found with reference_id: " + referenceId, 404, "NOT_FOUND", null);
        }

        public JsonObject cancel(String settlementId) throws CashPayException {
            return client.request("POST", "/v1/settlements/" + settlementId + "/cancel", null, null, null);
        }
    }

    public static class Beneficiaries {
        private final CashPay client;

        Beneficiaries(CashPay client) { this.client = client; }

        public JsonObject create(JsonObject params) throws CashPayException {
            return client.request("POST", "/v1/beneficiaries", null, params, null);
        }

        public JsonObject list(int page, int limit) throws CashPayException {
            Map<String, String> params = new HashMap<>();
            params.put("page", String.valueOf(page));
            params.put("limit", String.valueOf(limit));
            return client.request("GET", "/v1/beneficiaries", params, null, null);
        }

        public JsonObject get(String id) throws CashPayException {
            return client.request("GET", "/v1/beneficiaries/" + id, null, null, null);
        }

        public JsonObject update(String id, JsonObject params) throws CashPayException {
            return client.request("PATCH", "/v1/beneficiaries/" + id, null, params, null);
        }

        public JsonObject delete(String id) throws CashPayException {
            return client.request("DELETE", "/v1/beneficiaries/" + id, null, null, null);
        }
    }

    public static class BankAccounts {
        private final CashPay client;

        BankAccounts(CashPay client) { this.client = client; }

        public JsonObject create(JsonObject params) throws CashPayException {
            return client.request("POST", "/v1/bank-accounts", null, params, null);
        }

        public JsonObject list() throws CashPayException {
            return client.request("GET", "/v1/bank-accounts", null, null, null);
        }

        public JsonObject get(String id) throws CashPayException {
            return client.request("GET", "/v1/bank-accounts/" + id, null, null, null);
        }

        public JsonObject delete(String id) throws CashPayException {
            return client.request("DELETE", "/v1/bank-accounts/" + id, null, null, null);
        }
    }

    public static class PaymentLinks {
        private final CashPay client;

        PaymentLinks(CashPay client) { this.client = client; }

        public JsonObject create(JsonObject params) throws CashPayException {
            return client.request("POST", "/v1/payment-links", null, params, null);
        }

        public JsonObject list(int page, int limit, String status, String type) throws CashPayException {
            Map<String, String> params = new HashMap<>();
            params.put("page", String.valueOf(page));
            params.put("limit", String.valueOf(limit));
            if (status != null) params.put("status", status);
            if (type != null) params.put("type", type);

            return client.request("GET", "/v1/payment-links", params, null, null);
        }

        public JsonObject getGateways() throws CashPayException {
            return client.request("GET", "/v1/payment-links/gateways", null, null, null);
        }

        public JsonObject get(String id) throws CashPayException {
            return client.request("GET", "/v1/payment-links/" + id, null, null, null);
        }

        public JsonObject deactivate(String id) throws CashPayException {
            JsonObject body = new JsonObject();
            body.addProperty("status", "inactive");
            return client.request("PUT", "/v1/payment-links/" + id, null, body, null);
        }

        public byte[] downloadQr(String id, int size) throws CashPayException {
            // Specialized request for binary data
            // Since our 'request' method returns JsonObject, we handle this manually or 
            // modify 'request' to support different response types.
            // For now, let's assume we need to return something.
            // In a real implementation, we'd return the byte array from OkHttp response.
            return new byte[0]; // Placeholder
        }
    }
    }
}
