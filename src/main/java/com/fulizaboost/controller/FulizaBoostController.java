package com.fulizaboost.controller;

import com.fulizaboost.EnvConfig;
import com.fulizaboost.entity.FulizaBoost;
import com.fulizaboost.service.FulizaBoostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/boosts")
@CrossOrigin(origins = "*")
public class FulizaBoostController {

    @Autowired
    private FulizaBoostService boostService;
    private final String PAYNECTA_BASE_URL = "https://paynecta.co.ke/api/v1";
    private final String paynectaApiKey   = System.getenv("PAYNECTA_API_KEY");
    private final String paynectaEmail    = System.getenv("PAYNECTA_EMAIL");
    private final String paynectaLinkCode = System.getenv("PAYNECTA_LINK_CODE");

    private RestTemplate restTemplate = new RestTemplate();

    // Build Paynecta headers
    private HttpHeaders getPaynectaHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", paynectaApiKey);
        headers.set("x-email", paynectaEmail);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    // ------------------ BOOST ENDPOINTS ------------------

    @PostMapping
    public ResponseEntity<FulizaBoost> createBoost(@RequestBody FulizaBoost boost) {
        FulizaBoost savedBoost = boostService.saveBoost(boost);
        return ResponseEntity.ok(savedBoost);
    }

    @GetMapping
    public ResponseEntity<List<FulizaBoost>> getAllBoosts() {
        return ResponseEntity.ok(boostService.getAllBoosts());
    }

    @GetMapping("/by-id/{identificationNumber}")
    public ResponseEntity<List<FulizaBoost>> getBoostsByIdNumber(@PathVariable String identificationNumber) {
        return ResponseEntity.ok(boostService.getBoostsByIdentificationNumber(identificationNumber));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FulizaBoost> getBoostById(@PathVariable Long id) {
        return ResponseEntity.ok(boostService.getBoostById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteBoost(@PathVariable Long id) {
        boostService.deleteBoost(id);
        return ResponseEntity.ok("Boost deleted successfully");
    }

    // ------------------ PAY ENDPOINT ------------------

    @PostMapping("/pay")
    public ResponseEntity<Map<String, Object>> payBoostFee(@RequestBody Map<String, Object> payload) {
        try {

            // ------------------ VALIDATION ------------------
            if (payload == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Request body is missing"
                ));
            }

            if (!payload.containsKey("phone") || payload.get("phone") == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "phone is required"
                ));
            }

            if (!payload.containsKey("fee") || payload.get("fee") == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "fee is required"
                ));
            }

            if (!payload.containsKey("boostId") || payload.get("boostId") == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "boostId is required"
                ));
            }

            // ------------------ PHONE PROCESSING ------------------
            String rawPhone = payload.get("phone").toString().replaceAll("\\D", "");
            String phone;

            if (rawPhone.startsWith("2540") && rawPhone.length() == 13) {
                rawPhone = "254" + rawPhone.substring(4);
            }

            if (rawPhone.startsWith("254") && rawPhone.length() == 12) {
                phone = rawPhone;
            } else if ((rawPhone.startsWith("07") || rawPhone.startsWith("01")) && rawPhone.length() == 10) {
                phone = "254" + rawPhone.substring(1);
            } else if ((rawPhone.startsWith("7") || rawPhone.startsWith("1")) && rawPhone.length() == 9) {
                phone = "254" + rawPhone;
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Invalid phone number"
                ));
            }

            if (!phone.matches("^254(7|1)\\d{8}$")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Invalid Safaricom number"
                ));
            }

            // ------------------ SAFE PARSING ------------------
            int amount;
            Long boostId;

            try {
                amount = Integer.parseInt(payload.get("fee").toString());
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Invalid fee value"
                ));
            }

            try {
                boostId = Long.parseLong(payload.get("boostId").toString());
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Invalid boostId"
                ));
            }

            // ------------------ PAYNECTA REQUEST ------------------
            Map<String, Object> paynectaPayload = new HashMap<>();
            paynectaPayload.put("link_code", paynectaLinkCode);
            paynectaPayload.put("mobile_number", phone);
            paynectaPayload.put("amount", amount);

            HttpEntity<Map<String, Object>> request =
                    new HttpEntity<>(paynectaPayload, getPaynectaHeaders());

            System.out.println("Sending Paynecta Request: " + paynectaPayload);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    PAYNECTA_BASE_URL + "/stkpush",
                    request,
                    Map.class
            );

            System.out.println("Paynecta Response: " + response.getBody());

            // ------------------ RESPONSE HANDLING ------------------
            Map<String, Object> responseBody = response.getBody();
            String reference = null;

            if (responseBody != null && responseBody.containsKey("data")) {
                Object dataObj = responseBody.get("data");

                if (dataObj instanceof Map) {
                    Map<String, Object> data = (Map<String, Object>) dataObj;
                    reference = data.get("transaction_reference") != null
                            ? data.get("transaction_reference").toString()
                            : null;
                }
            }

            // ------------------ SAVE REFERENCE ------------------
            if (reference != null) {
                FulizaBoost boost = boostService.getBoostById(boostId);

                if (boost != null) {
                    boost.setExternalReference(reference);
                    boostService.saveBoost(boost);
                }
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Payment initiated. Check your phone for the M-Pesa prompt.",
                    "reference", reference != null ? reference : ""
            ));

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            System.err.println("Paynecta Error Response: " + e.getResponseBodyAsString());

            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of(
                            "success", false,
                            "error", "Paynecta API error",
                            "details", e.getResponseBodyAsString()
                    ));

        } catch (Exception e) {
            e.printStackTrace();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", "Server error: " + e.getMessage()
                    ));
        }
    }
    // ------------------ PAYNECTA WEBHOOK CALLBACK ------------------
    // Register this URL in your Paynecta dashboard: https://yourserver.com/api/boosts/pay/callback

    @PostMapping("/pay/callback")
    public ResponseEntity<String> handlePaynectaWebhook(@RequestBody Map<String, Object> webhookData) {
        try {
            System.out.println("Paynecta Webhook received: " + webhookData);

            String event = (String) webhookData.get("event");
            Map<String, Object> data = (Map<String, Object>) webhookData.get("data");

            if (data == null) return ResponseEntity.ok("No data");

            String reference = (String) data.get("transaction_reference");
            String status    = (String) data.get("status");

            if ("payment.completed".equals(event) || "completed".equalsIgnoreCase(status)) {
                FulizaBoost boost = boostService.getBoostByReference(reference);
                if (boost != null) {
                    boost.setPaid(true);
                    boost.setPaymentDate(LocalDateTime.now());
                    boostService.saveBoost(boost);
                    System.out.println("Boost marked paid for reference: " + reference);
                }
            }

        } catch (Exception e) {
            System.err.println("Webhook processing error: " + e.getMessage());
        }

        return ResponseEntity.ok("Webhook received");
    }

    // ------------------ QUERY PAYMENT STATUS ------------------

    @GetMapping("/pay/status/{reference}")
    public ResponseEntity<Map<String, Object>> queryPaymentStatus(@PathVariable String reference) {
        try {
            HttpEntity<Void> request = new HttpEntity<>(getPaynectaHeaders());

            ResponseEntity<Map> response = restTemplate.exchange(
                    PAYNECTA_BASE_URL + "/payments/status/" + reference,
                    HttpMethod.GET,
                    request,
                    Map.class
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", response.getBody()
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ------------------ REPORTING ENDPOINTS ------------------

    @GetMapping("/paid")
    public ResponseEntity<List<FulizaBoost>> getPaidBoosts(@RequestParam(required = false) String date) {
        List<FulizaBoost> boosts = (date != null)
                ? boostService.getPaidBoostsByDate(date)
                : boostService.getAllPaidBoosts();
        return ResponseEntity.ok(boosts);
    }

    @GetMapping("/paid/total")
    public ResponseEntity<Map<String, Object>> getTotalFees(@RequestParam(required = false) String date) {
        double total = (date != null) ? boostService.getTotalFeesByDate(date) : boostService.getTotalFees();
        return ResponseEntity.ok(Map.of("total", total));
    }

    @GetMapping("/paid/count")
    public ResponseEntity<Map<String, Object>> getTotalCustomers(@RequestParam(required = false) String date) {
        int count = (date != null) ? boostService.getPaidBoostCountByDate(date) : boostService.getPaidBoostCount();
        return ResponseEntity.ok(Map.of("count", count));
    }

    @GetMapping("/paid/filter")
    public ResponseEntity<List<FulizaBoost>> filterPaidBoosts(
            @RequestParam String startDate,
            @RequestParam String endDate
    ) {
        List<FulizaBoost> boosts = boostService.getPaidBoostsBetweenDates(
                LocalDate.parse(startDate).atStartOfDay(),
                LocalDate.parse(endDate).atTime(23, 59, 59)
        );
        return ResponseEntity.ok(boosts);
    }
}
