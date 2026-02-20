# CashPay Java SDK

Official Java SDK for integrating with CashPay Payment Gateway.

## Installation

### Maven

```xml
## Installation

### Private GitHub Maven Repository

Add the following to your `pom.xml` to fetch the SDK from the private GitHub repository:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/Annesbadusha/cashpay</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.cashpay</groupId>
        <artifactId>cashpay-java</artifactId>
        <version>1.1.0</version>
    </dependency>
</dependencies>
```

> **Note**: You will need a GitHub Personal Access Token (PAT) with `read:packages` permissions configured in your `~/.m2/settings.xml` to download this package.

### Manual JAR Installation
If you prefer to include the JAR directly:
1.  Download the `cashpay-java-1.1.0.jar` from the GitHub Releases.
2.  Add it to your project's `libs` folder.
3.  Include it in your build configuration.

## Quick Start

```java
import com.cashpay.CashPay;

CashPay cashpay = new CashPay.Builder()
    .apiKey("cpk_live_xxx")
    .apiSecret("cps_live_xxx")
    .environment("production") // or "sandbox"
    .build();
```

## Usage Examples

### Check Balance

```java
import com.google.gson.JsonObject;

// Get unified balance
JsonObject balance = cashpay.balance().get();
long totalBalance = balance.get("totalBalance").getAsLong();
System.out.println("Total Balance: ₹" + (totalBalance / 100.0));

// Get settlement balance
JsonObject settlement = cashpay.balance().getSettlement();
System.out.println("Available: ₹" + (settlement.get("availableWithdrawalAmount").getAsLong() / 100.0));

// Get payout balance
JsonObject payout = cashpay.balance().getPayout();
System.out.println("Payout Balance: ₹" + (payout.get("payoutBalance").getAsLong() / 100.0));
```

### Payins

```java
import com.google.gson.JsonObject;

// 1. Create a Hosted Payment Page (Direct Redirect Flow)
JsonObject params = new JsonObject();
params.addProperty("amount", 10000); // ₹100
params.addProperty("orderId", "ORDER_123");
params.addProperty("customerName", "John Doe");
params.addProperty("returnUrl", "https://yoursite.com/payment/result");

JsonObject page = cashpay.payins().createPaymentPage(params, "unique-123");
System.out.println("Payment URL: " + page.get("paymentUrl").getAsString());

// 2. Create UPI Intent (for Mobile App redirects)
JsonObject intentParams = new JsonObject();
intentParams.addProperty("amount", 5000);
intentParams.addProperty("orderId", "ORDER_124");
JsonObject intent = cashpay.payins().createIntent(intentParams, "unique-124");
System.out.println("UPI DeepLink: " + intent.get("intentUrl").getAsString());

// 3. Create Card Payment
JsonObject cardParams = new JsonObject();
cardParams.addProperty("amount", 10000);
cardParams.addProperty("orderId", "ORDER_125");
cardParams.addProperty("cardNumber", "4111111111111111");
cardParams.addProperty("expiryMonth", "12");
cardParams.addProperty("expiryYear", "2025");
cardParams.addProperty("cvv", "123");
JsonObject card = cashpay.payins().createCard(cardParams, "unique-125");

// Get payin status by payment ID
JsonObject status = cashpay.payins().getStatus("payment-uuid");

// Get payin by order ID
JsonObject payin = cashpay.payins().getByOrderId("ORDER_124");
System.out.println("Status: " + payin.get("status").getAsString());
```

### Payment Links

```java
// Create a shareable payment link or QR
JsonObject linkParams = new JsonObject();
linkParams.addProperty("amount", 50000);
linkParams.addProperty("description", "Invoice #001");
linkParams.addProperty("type", "one-time");

JsonObject link = cashpay.paymentLinks().create(linkParams);
System.out.println("Short URL: " + link.get("shortUrl").getAsString());

// List payment links
JsonObject links = cashpay.paymentLinks().list(1, 10, "active", null);

// Deactivate a link
cashpay.paymentLinks().deactivate("link-uuid");
```

### Beneficiaries & Bank Accounts

```java
// Add a beneficiary for payouts
JsonObject benParams = new JsonObject();
benParams.addProperty("name", "John Doe");
benParams.addProperty("accountNumber", "50100123456789");
benParams.addProperty("ifsc", "HDFC0001234");
JsonObject beneficiary = cashpay.beneficiaries().create(benParams);

// Add a merchant bank account for settlements
JsonObject bankParams = new JsonObject();
bankParams.addProperty("accountNumber", "50100123456789");
bankParams.addProperty("ifsc", "HDFC0001234");
bankParams.addProperty("accountHolderName", "My Business");
JsonObject bank = cashpay.bankAccounts().create(bankParams);
```

### Payouts

```java
// Create a payout
JsonObject payout = cashpay.payouts().create(
    "ben_xxx",           // beneficiaryId
    10000,               // amount in paise (₹100)
    "PAY-001",           // referenceId
    "Salary payment",    // narration
    "IMPS",              // mode
    "unique-key"         // idempotencyKey
);
System.out.println("Payout ID: " + payout.get("id").getAsString());

// Create bulk payouts (max 100)
List<Map<String, Object>> payouts = Arrays.asList(
    Map.of("beneficiaryId", "ben_1", "amount", 10000, "referenceId", "PAY-001"),
    Map.of("beneficiaryId", "ben_2", "amount", 20000, "referenceId", "PAY-002")
);
JsonObject bulkResult = cashpay.payouts().createBulk(payouts, "bulk-key");
System.out.println("Success: " + bulkResult.get("successCount").getAsInt());

// List payouts
JsonObject payoutList = cashpay.payouts().list(1, 20, "COMPLETED", null);

// Get payout by ID
JsonObject payoutDetails = cashpay.payouts().get("payout-uuid");

// Cancel payout
JsonObject cancelled = cashpay.payouts().cancel("payout-uuid");
```

### Settlements

```java
// Create settlement with saved bank account
JsonObject settlement = cashpay.settlements().create(
    100000,              // amount in paise (₹1000)
    "bank_xxx",          // bankAccountId
    null, null, null,    // direct bank details (not used)
    "SET-001",           // referenceId
    "unique-key"         // idempotencyKey
);

// Create settlement with direct bank details
JsonObject directSettlement = cashpay.settlements().create(
    100000,              // amount
    null,                // bankAccountId (not used)
    "50100123456789",    // accountNumber
    "HDFC0001234",       // ifsc
    "John Doe",          // accountHolderName
    "SET-002",           // referenceId
    null                 // idempotencyKey
);

// Create bulk settlements
List<Map<String, Object>> settlements = Arrays.asList(
    Map.of("amount", 50000, "bankAccountId", "bank_1", "referenceId", "SET-001"),
    Map.of("amount", 75000, "bankAccountId", "bank_2", "referenceId", "SET-002")
);
JsonObject bulkSettlements = cashpay.settlements().createBulk(settlements, "bulk-key");

// List settlements
JsonObject settlementList = cashpay.settlements().list(1, 20, "COMPLETED", null);

// Get settlement by ID
JsonObject settlementDetails = cashpay.settlements().get("settlement-uuid");

// Cancel settlement
JsonObject cancelledSettlement = cashpay.settlements().cancel("settlement-uuid");
```

### Webhook Verification

```java
// In your webhook handler (e.g., Spring Boot)
@PostMapping("/webhook")
public ResponseEntity<String> handleWebhook(
    @RequestBody String payload,
    @RequestHeader("x-webhook-signature") String signature
) {
    boolean isValid = cashpay.verifyWebhook(payload, signature, "your-webhook-secret");
    
    if (!isValid) {
        return ResponseEntity.status(401).body("Invalid signature");
    }
    
    JsonObject event = JsonParser.parseString(payload).getAsJsonObject();
    String eventType = event.get("type").getAsString();
    
    switch (eventType) {
        case "payin.completed":
            // Handle payment completed
            break;
        case "payout.completed":
            // Handle payout completed
            break;
        case "settlement.completed":
            // Handle settlement completed
            break;
    }
    
    return ResponseEntity.ok("OK");
}
```

## Error Handling

```java
import com.cashpay.CashPayException;

try {
    JsonObject payout = cashpay.payouts().create(
        "invalid-id", 10000, null, null, null, null
    );
} catch (CashPayException e) {
    System.err.println("Error: " + e.getMessage());
    System.err.println("Status: " + e.getStatusCode());
    System.err.println("Code: " + e.getErrorCode());
    System.err.println("Details: " + e.getDetails());
}
```

## Configuration Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `apiKey` | String | required | Your API key |
| `apiSecret` | String | required | Your API secret |
| `environment` | String | "production" | "sandbox" or "production" |
| `baseUrl` | String | auto | Custom API base URL |
| `timeout` | int | 30 | Request timeout in seconds |

## Requirements

- Java 11+
- OkHttp 4.x
- Gson 2.x

## Support

- Documentation: https://docs.cashpay.com
- Email: support@cashpay.com
- GitHub Issues: https://github.com/cashpay/cashpay-java-sdk/issues
