package com.shadulla.ticketbooking.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "seats")
@Getter
@Setter
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long showId;
    private String seatNumber;
    private String status = "AVAILABLE";   // AVAILABLE or BOOKED
    private String bookedBy;
}