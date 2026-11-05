package com.shadulla.ticketbooking.controller;

import com.shadulla.ticketbooking.entity.Booking;
import com.shadulla.ticketbooking.entity.Seat;
import com.shadulla.ticketbooking.entity.Show;
import com.shadulla.ticketbooking.service.TicketService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class TicketController {

    private final TicketService service;

    public TicketController(TicketService service) {
        this.service = service;
    }

    public record CreateShowRequest(String name, String venue, LocalDateTime showTime, int seatCount) {}
    public record BookRequest(String seatNumber, String userName) {}

    @PostMapping("/shows")
    public Show createShow(@RequestBody CreateShowRequest r) {
        return service.createShow(r.name(), r.venue(), r.showTime(), r.seatCount());
    }

    @GetMapping("/shows")
    public List<Show> shows() {
        return service.allShows();
    }

    @GetMapping("/shows/{id}/seats")
    public List<Seat> seats(@PathVariable Long id) {
        return service.seatsOf(id);
    }

    @PostMapping("/shows/{id}/book")
    public Booking book(@PathVariable Long id, @RequestBody BookRequest r) {
        return service.bookSeat(id, r.seatNumber(), r.userName());
    }

    @GetMapping("/shows/{id}/bookings")
    public List<Booking> bookings(@PathVariable Long id) {
        return service.bookingsOf(id);
    }
}