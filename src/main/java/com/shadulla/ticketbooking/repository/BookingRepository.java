package com.shadulla.ticketbooking.repository;

import com.shadulla.ticketbooking.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByShowId(Long showId);

    // NEW: all bookings of one user, newest first
    List<Booking> findByUserNameOrderByBookedAtDesc(String userName);
}