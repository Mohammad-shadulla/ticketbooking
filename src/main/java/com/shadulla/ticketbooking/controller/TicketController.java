package com.shadulla.ticketbooking.controller;

import com.shadulla.ticketbooking.entity.Booking;
import com.shadulla.ticketbooking.entity.Seat;
import com.shadulla.ticketbooking.entity.Show;
import com.shadulla.ticketbooking.service.TicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/shows")
    public ResponseEntity<Show> createShow(@RequestParam String name,
                                           @RequestParam String venue,
                                           @RequestParam LocalDateTime showTime,
                                           @RequestParam int seatCount) {
        return ResponseEntity.ok(ticketService.createShow(name, venue, showTime, seatCount));
    }

    @GetMapping("/shows")
    public ResponseEntity<List<Show>> allShows() {
        return ResponseEntity.ok(ticketService.allShows());
    }

    @GetMapping("/shows/{showId}/seats")
    public ResponseEntity<List<Seat>> seatsOf(@PathVariable Long showId) {
        return ResponseEntity.ok(ticketService.seatsOf(showId));
    }

    @GetMapping("/shows/{showId}/bookings")
    public ResponseEntity<List<Booking>> bookingsOf(@PathVariable Long showId) {
        return ResponseEntity.ok(ticketService.bookingsOf(showId));
    }

    // all bookings of one user
    @GetMapping("/users/{userName}/bookings")
    public ResponseEntity<List<Booking>> bookingsOfUser(@PathVariable String userName) {
        return ResponseEntity.ok(ticketService.bookingsOfUser(userName));
    }

    @GetMapping("/shows/{showId}/availability")
    public ResponseEntity<Map<String, Long>> availability(@PathVariable Long showId) {
        return ResponseEntity.ok(ticketService.availability(showId));
    }

    @PostMapping("/hold")
    public ResponseEntity<String> holdSeat(@RequestParam Long showId,
                                           @RequestParam String seatNumber,
                                           @RequestParam String userName) {
        return ResponseEntity.ok(ticketService.holdSeat(showId, seatNumber, userName));
    }

    // NEW: who holds this seat and how many seconds are left
    @GetMapping("/hold-status")
    public ResponseEntity<Map<String, Object>> holdStatus(@RequestParam Long showId,
                                                          @RequestParam String seatNumber) {
        return ResponseEntity.ok(ticketService.holdStatus(showId, seatNumber));
    }

    @PostMapping("/confirm")
    public ResponseEntity<Booking> confirmBooking(@RequestParam Long showId,
                                                  @RequestParam String seatNumber,
                                                  @RequestParam String userName) {
        return ResponseEntity.ok(ticketService.confirmBooking(showId, seatNumber, userName));
    }

    @PostMapping("/book")
    public ResponseEntity<Booking> bookSeat(@RequestParam Long showId,
                                            @RequestParam String seatNumber,
                                            @RequestParam String userName) {
        return ResponseEntity.ok(ticketService.bookSeat(showId, seatNumber, userName));
    }

    // userName is required: only the owner can cancel
    @DeleteMapping("/cancel/{bookingId}")
    public ResponseEntity<String> cancelBooking(@PathVariable Long bookingId,
                                                @RequestParam String userName) {
        ticketService.cancelBooking(bookingId, userName);
        return ResponseEntity.ok("Booking canceled successfully and seat is now available.");
    }
}