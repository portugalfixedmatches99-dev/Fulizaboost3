package com.fulizaboost.service;

import com.fulizaboost.entity.FulizaBoost;
import com.fulizaboost.repository.FulizaBoostRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FulizaBoostService {

	@Autowired
	private FulizaBoostRepository boostRepository;

	// Save a new boost
	public FulizaBoost saveBoost(FulizaBoost boost) {
		// Optional validation
		if (boost.getIdentificationNumber() == null || boost.getIdentificationNumber().isEmpty()) {
			throw new IllegalArgumentException("Identification number is required!");
		}
		return boostRepository.save(boost);
	}

	// Get all boosts
	public List<FulizaBoost> getAllBoosts() {
		return boostRepository.findAll();
	}

	// Get boosts by identification number
	public List<FulizaBoost> getBoostsByIdentificationNumber(String idNumber) {
		return boostRepository.findByIdentificationNumber(idNumber);
	}

	// Get a single boost by ID
	public FulizaBoost getBoostById(Long id) {
		return boostRepository.findById(id)
				.orElseThrow(() -> new RuntimeException("Boost not found with ID: " + id));
	}

	// Delete a boost
	public void deleteBoost(Long id) {
		boostRepository.deleteById(id);
	}


	// ----------------- ADMIN DASHBOARD METHODS -----------------

	// Get all paid boosts
	public List<FulizaBoost> getAllPaidBoosts() {
		return boostRepository.findByPaidTrue();
	}

	// Get paid boosts filtered by date (YYYY-MM-DD)
	public List<FulizaBoost> getPaidBoostsByDate(String date) {
		LocalDateTime start = LocalDateTime.parse(date + "T00:00:00");
		LocalDateTime end = LocalDateTime.parse(date + "T23:59:59");
		return boostRepository.findByPaidTrueAndPaymentDateBetween(start, end);
	}

	// Get total fees collected
	public double getTotalFees() {
		return boostRepository.findByPaidTrue()
				.stream()
				.mapToDouble(FulizaBoost::getFee)
				.sum();
	}

	// Total fees by date
	public double getTotalFeesByDate(String date) {
		LocalDateTime start = LocalDateTime.parse(date + "T00:00:00");
		LocalDateTime end = LocalDateTime.parse(date + "T23:59:59");
		return boostRepository.findByPaidTrueAndPaymentDateBetween(start, end)
				.stream()
				.mapToDouble(FulizaBoost::getFee)
				.sum();
	}

	// Total number of customers who paid
	public int getPaidBoostCount() {
		return boostRepository.findByPaidTrue().size();
	}

	public FulizaBoost getBoostByReference(String reference) {
		return boostRepository.findByExternalReference(reference);
	}


	// Total number of customers who paid on a specific date
	public int getPaidBoostCountByDate(String date) {
		LocalDateTime start = LocalDateTime.parse(date + "T00:00:00");
		LocalDateTime end = LocalDateTime.parse(date + "T23:59:59");
		return boostRepository.findByPaidTrueAndPaymentDateBetween(start, end).size();
	}

	// Filter paid boosts between two dates
	public List<FulizaBoost> getPaidBoostsBetweenDates(LocalDateTime start, LocalDateTime end) {
		return boostRepository.findByPaidTrueAndPaymentDateBetween(start, end);
	}
}
