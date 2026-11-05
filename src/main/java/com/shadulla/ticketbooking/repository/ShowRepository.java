package com.shadulla.ticketbooking.repository;

import com.shadulla.ticketbooking.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShowRepository extends JpaRepository<Show, Long> {
}