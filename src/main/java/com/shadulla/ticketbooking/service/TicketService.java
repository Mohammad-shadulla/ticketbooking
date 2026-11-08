package com.shadulla.ticketbooking.service;

import com.shadulla.ticketbooking.entity.Booking;
import com.shadulla.ticketbooking.entity.Seat;
import com.shadulla.ticketbooking.entity.Show;
import com.shadulla.ticketbooking.repository.BookingRepository;
import com.shadulla.ticketbooking.repository.SeatRepository;
import com.shadulla.ticketbooking.repository.ShowRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TicketService {

    private final ShowRepository shows;
    private final SeatRepository seats;
    private final BookingRepository bookings;

    public TicketService(ShowRepository shows, SeatRepository seats, BookingRepository bookings) {
        this.shows = shows;
        this.seats = seats;
        this.bookings = bookings;
    }

    @Transactional
    public Show createShow(String name, String venue, LocalDateTime showTime, int seatCount) {
        if (name == null || name.isBlank() || seatCount < 1 || seatCount > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Name is required and seat count must be between 1 and 500");
        }
        Show show = new Show();
        show.setName(name);
        show.setVenue(venue);
        show.setShowTime(showTime);
        show = shows.save(show);

        List<Seat> newSeats = new ArrayList<>();
        for (int i = 1; i <= seatCount; i++) {
            Seat seat = new Seat();
            seat.setShowId(show.getId());
            seat.setSeatNumber("S" + i);
            newSeats.add(seat);
        }
        seats.saveAll(newSeats);
        return show;
    }

    public List<Show> allShows() {
        return shows.findAll();
    }

    public List<Seat> seatsOf(Long showId) {
        return seats.findByShowIdOrderById(showId);
    }

    public List<Booking> bookingsOf(Long showId) {
        return bookings.findByShowId(showId);
    }

    // Basic version: checks if the seat is free, then books it.
    // There is NO protection against two users booking at the same moment.
    @Transactional
    public Booking bookSeat(Long showId, String seatNumber, String userName) {
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

    @Transactional
    public void cancelBooking(Long bookingId) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        seats.findByShowIdAndSeatNumber(booking.getShowId(), booking.getSeatNumber())
                .ifPresent(seat -> {
                    seat.setStatus("AVAILABLE");
                    seat.setBookedBy(null);
                    seats.save(seat);
                });

        bookings.delete(booking);
    }

    public Map<String, Long> availability(Long showId) {
        long total = seats.countByShowId(showId);
        if (total == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found");
        }
        long available = seats.countByShowIdAndStatus(showId, "AVAILABLE");
        return Map.of("total", total, "available", available, "booked", total - available);
    }
}