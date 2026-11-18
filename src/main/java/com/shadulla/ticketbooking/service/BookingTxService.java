package com.shadulla.ticketbooking.service;

import com.shadulla.ticketbooking.entity.Booking;
import com.shadulla.ticketbooking.entity.Seat;
import com.shadulla.ticketbooking.repository.BookingRepository;
import com.shadulla.ticketbooking.repository.SeatRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingTxService {

    private final SeatRepository seats;
    private final BookingRepository bookings;

    public BookingTxService(SeatRepository seats, BookingRepository bookings) {
        this.seats = seats;
        this.bookings = bookings;
    }

    // Seat update + booking save happen together, or not at all
    @Transactional
    public Booking executeBooking(Long showId, String seatNumber, String userName) {
        Seat seat = seats.findByShowIdAndSeatNumber(showId, seatNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat not found"));

        if (!"AVAILABLE".equals(seat.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat already booked");
        }

        seat.setStatus("BOOKED");
        seat.setBookedBy(userName);
        seats.save(seat);

        Booking booking = new Booking();
        booking.setShowId(showId);
        booking.setSeatNumber(seatNumber);
        booking.setUserName(userName);
        return bookings.save(booking);
    }
}