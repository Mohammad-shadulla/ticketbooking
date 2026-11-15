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
        Show show = ticketService.createShow(name, venue, showTime, seatCount);
        return ResponseEntity.ok(show);
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

    @GetMapping("/shows/{showId}/availability")
    public ResponseEntity availability(@PathVariable Long showId) {
        return ResponseEntity.ok(ticketService.availability(showId));
    }

    @PostMapping("/hold")
    public ResponseEntity<String> holdSeat(@RequestParam Long showId,
                                           @RequestParam String seatNumber,
                                           @RequestParam String userName) {
        String response = ticketService.holdSeat(showId, seatNumber, userName);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/confirm")
    public ResponseEntity<Booking> confirmBooking(@RequestParam Long showId,
                                                  @RequestParam String seatNumber,
                                                  @RequestParam String userName) {
        Booking booking = ticketService.confirmBooking(showId, seatNumber, userName);
        return ResponseEntity.ok(booking);
    }

    @PostMapping("/book")
    public ResponseEntity<Booking> bookSeat(@RequestParam Long showId,
                                            @RequestParam String seatNumber,
                                            @RequestParam String userName) {
        Booking booking = ticketService.bookSeat(showId, seatNumber, userName);
        return ResponseEntity.ok(booking);
    }

    @DeleteMapping("/cancel/{bookingId}")
    public ResponseEntity<String> cancelBooking(@PathVariable Long bookingId) {
        ticketService.cancelBooking(bookingId);
        return ResponseEntity.ok("Booking canceled successfully and seat is now available.");
    }
}