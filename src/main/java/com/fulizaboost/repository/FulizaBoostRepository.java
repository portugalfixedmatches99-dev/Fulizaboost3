package com.fulizaboost.repository;

import com.fulizaboost.entity.FulizaBoost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FulizaBoostRepository extends JpaRepository<FulizaBoost, Long> {

    // Find all boosts for a given identification number
    List<FulizaBoost> findByIdentificationNumber(String identificationNumber);

    FulizaBoost findByExternalReference(String reference);

    // Find all paid boosts
    List<FulizaBoost> findByPaidTrue();

    // Find paid boosts within a date range
    List<FulizaBoost> findByPaidTrueAndPaymentDateBetween(LocalDateTime start, LocalDateTime end);

    // Optional: find boosts by paid status (true/false)
    List<FulizaBoost> findByPaid(Boolean paid);
}
