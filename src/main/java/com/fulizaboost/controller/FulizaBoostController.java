package com.fulizaboost.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    // PayHero settings from .env
    private final String PAYHERO_API = "https://backend.payhero.co.ke/api/v2/payments";
    private final String payHeroUsername = EnvConfig.dotenv.get("PAYHERO_API_USERNAME");
    private final String payHeroPassword = EnvConfig.dotenv.get("PAYHERO_API_PASSWORD");
    private final String payHeroChannelId = EnvConfig.dotenv.get("PAYHERO_CHANNEL_ID");
    private final String callbackUrl = EnvConfig.dotenv.get("PAYHERO_CALLBACK_URL");

    private RestTemplate restTemplate = new RestTemplate();

    // Build Basic Auth header from username:password
    private String getPayHeroBasicAuth() {
        String credentials = payHeroUsername + ":" + payHeroPassword;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes());
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

    @PostMapping("/pay")
    public ResponseEntity<Map<String, Object>> payBoostFee(@RequestBody Map<String, Object> payload) {
        try {
//            // Clean and format phone number
//            String phone = ((String) payload.get("phone")).replace("+", "");
//            if (phone.startsWith("0")) {
//                phone = "254" + phone.substring(1);
//            }
            String rawPhone = ((String) payload.get("phone")).replaceAll("\\D", "");
            String phone;

// Fix numbers wrongly sent as 25407XXXXXXXX
            if (rawPhone.startsWith("2540") && rawPhone.length() == 13) {
                rawPhone = "254" + rawPhone.substring(4);
            }

            if (rawPhone.startsWith("254") && rawPhone.length() == 12) {
                phone = rawPhone;
            } else if (
                    (rawPhone.startsWith("07") || rawPhone.startsWith("01")) &&
                            rawPhone.length() == 10
            ) {
                phone = "254" + rawPhone.substring(1);
            } else if (
                    (rawPhone.startsWith("7") || rawPhone.startsWith("1")) &&
                            rawPhone.length() == 9
            ) {
                phone = "254" + rawPhone;
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Invalid phone number"
                ));
            }

// Final Safaricom validation
            if (!phone.matches("^254(7|1)\\d{8}$")) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "Invalid Safaricom number"
                ));
            }



            Double amount = ((Number) payload.get("fee")).doubleValue();
            String customerName = (String) payload.getOrDefault("customer_name", "Customer");
            String externalRef = "BOOST-" + UUID.randomUUID();

            // Build PayHero payload
            Map<String, Object> payHeroPayload = new HashMap<>();
            payHeroPayload.put("amount", amount.intValue());
            payHeroPayload.put("phone_number", phone);
            payHeroPayload.put("channel_id", Integer.parseInt(payHeroChannelId));
            payHeroPayload.put("provider", "m-pesa");
            payHeroPayload.put("external_reference", externalRef);
            payHeroPayload.put("customer_name", customerName);
            payHeroPayload.put("callback_url", callbackUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", getPayHeroBasicAuth());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payHeroPayload, headers);

            System.out.println("Sending PayHero Request: " + payHeroPayload);

            ResponseEntity<String> response = restTemplate.postForEntity(PAYHERO_API, request, String.class);

            System.out.println("PayHero Response: " + response.getBody());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Payment initiated successfully",
                    "data", response.getBody(),
                    "reference", externalRef
            ));

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            System.err.println("PayHero Error Response: " + e.getResponseBodyAsString());
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of(
                            "success", false,
                            "error", "PayHero API error",
                            "details", e.getResponseBodyAsString()
                    ));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "error", e.getMessage()
                    ));
        }
    }

    // ------------------ PAYHERO CALLBACK ------------------

    @PostMapping("/pay/callback")
    public ResponseEntity<String> handlePayHeroCallback(@RequestBody Map<String, Object> callbackData) {
        Boolean success = (Boolean) callbackData.get("success");
        String reference = (String) callbackData.get("reference");

        if (Boolean.TRUE.equals(success)) {
            FulizaBoost boost = boostService.getBoostByReference(reference);
            if (boost != null) {
                boost.setPaid(true);
                boost.setPaymentDate(LocalDateTime.now());
                boostService.saveBoost(boost);
            }
        }
        return ResponseEntity.ok("Callback received");
    }

    // ------------------ REPORTING ENDPOINTS ------------------

    @GetMapping("/paid")
    public ResponseEntity<List<FulizaBoost>> getPaidBoosts(
            @RequestParam(required = false) String date
    ) {
        List<FulizaBoost> boosts;
        if (date != null) {
            boosts = boostService.getPaidBoostsByDate(date);
        } else {
            boosts = boostService.getAllPaidBoosts();
        }
        return ResponseEntity.ok(boosts);
    }

    @GetMapping("/paid/total")
    public ResponseEntity<Map<String, Object>> getTotalFees(
            @RequestParam(required = false) String date
    ) {
        double total = (date != null) ? boostService.getTotalFeesByDate(date) : boostService.getTotalFees();
        return ResponseEntity.ok(Map.of("total", total));
    }

    @GetMapping("/paid/count")
    public ResponseEntity<Map<String, Object>> getTotalCustomers(
            @RequestParam(required = false) String date
    ) {
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
