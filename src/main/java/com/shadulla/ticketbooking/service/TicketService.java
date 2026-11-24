package com.shadulla.ticketbooking.service;

import com.shadulla.ticketbooking.entity.Booking;
import com.shadulla.ticketbooking.entity.Seat;
import com.shadulla.ticketbooking.entity.Show;
import com.shadulla.ticketbooking.repository.BookingRepository;
import com.shadulla.ticketbooking.repository.SeatRepository;
import com.shadulla.ticketbooking.repository.ShowRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TicketService {

    private static final Duration HOLD_TTL = Duration.ofMinutes(2);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    // Deletes a Redis key only if its value matches ours (used for locks and holds)
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final ShowRepository shows;
    private final SeatRepository seats;
    private final BookingRepository bookings;
    private final StringRedisTemplate redisTemplate;
    private final BookingTxService bookingTx;

    public TicketService(ShowRepository shows, SeatRepository seats,
                         BookingRepository bookings, StringRedisTemplate redisTemplate,
                         BookingTxService bookingTx) {
        this.shows = shows;
        this.seats = seats;
        this.bookings = bookings;
        this.redisTemplate = redisTemplate;
        this.bookingTx = bookingTx;
    }

    // ---------- helpers ----------

    private String holdKey(Long showId, String seatNumber) {
        return "hold:show:" + showId + ":seat:" + seatNumber;
    }

    private String lockKey(Long showId, String seatNumber) {
        return "lock:show:" + showId + ":seat:" + seatNumber;
    }

    private void requireUser(String userName) {
        if (userName == null || userName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userName is required");
        }
    }

    // ---------- shows ----------

    @Transactional
    public Show createShow(String name, String venue, LocalDateTime showTime, int seatCount) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        if (venue == null || venue.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Venue is required");
        }
        if (showTime == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Show time is required");
        }
        if (seatCount < 1 || seatCount > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seat count must be between 1 and 500");
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

    // NEW: seats that can be booked right now (not booked and not on hold)
    public Map<String, Object> freeSeats(Long showId) {
        List<Seat> allSeats = seats.findByShowIdOrderById(showId);
        if (allSeats.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found");
        }

        // step 1: keep only seats that are not booked
        List<Seat> notBooked = allSeats.stream()
                .filter(seat -> "AVAILABLE".equals(seat.getStatus()))
                .toList();

        List<String> freeSeatNumbers = new ArrayList<>();

        if (!notBooked.isEmpty()) {
            // step 2: ask Redis about all hold keys in one single call
            List<String> keys = notBooked.stream()
                    .map(seat -> holdKey(showId, seat.getSeatNumber()))
                    .toList();
            List<String> holders = redisTemplate.opsForValue().multiGet(keys);

            // step 3: a seat is free when nobody holds it
            for (int i = 0; i < notBooked.size(); i++) {
                boolean heldBySomeone = holders != null && holders.get(i) != null;
                if (!heldBySomeone) {
                    freeSeatNumbers.add(notBooked.get(i).getSeatNumber());
                }
            }
        }

        return Map.of(
                "showId", showId,
                "count", freeSeatNumbers.size(),
                "freeSeats", freeSeatNumbers);
    }

    public List<Booking> bookingsOf(Long showId) {
        return bookings.findByShowId(showId);
    }

    // all bookings of one user, newest first
    public List<Booking> bookingsOfUser(String userName) {
        requireUser(userName);
        return bookings.findByUserNameOrderByBookedAtDesc(userName);
    }

    // ---------- 1. hold a seat (Redis, 2 minutes) ----------

    public String holdSeat(Long showId, String seatNumber, String userName) {
        requireUser(userName);
        String holdKey = holdKey(showId, seatNumber);

        Seat seat = seats.findByShowIdAndSeatNumber(showId, seatNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat not found"));

        if (!"AVAILABLE".equals(seat.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat is already booked or unavailable");
        }

        Boolean success = redisTemplate.opsForValue().setIfAbsent(holdKey, userName, HOLD_TTL);

        if (Boolean.FALSE.equals(success)) {
            String holder = redisTemplate.opsForValue().get(holdKey);
            if (userName.equals(holder)) {
                // same user pressed hold again: just extend the time
                redisTemplate.expire(holdKey, HOLD_TTL);
                return "You already hold seat " + seatNumber + ". Hold extended for 2 minutes.";
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seat is currently on temporary hold by another user.");
        }

        return "Seat " + seatNumber + " successfully held for 2 minutes. Please proceed to payment confirmation.";
    }

    // shows who holds a seat and how many seconds are left
    public Map<String, Object> holdStatus(Long showId, String seatNumber) {
        Seat seat = seats.findByShowIdAndSeatNumber(showId, seatNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Seat not found"));

        String holdKey = holdKey(showId, seatNumber);
        String holder = redisTemplate.opsForValue().get(holdKey);

        // nobody is holding this seat
        if (holder == null) {
            return Map.of(
                    "seatNumber", seatNumber,
                    "seatStatus", seat.getStatus(),
                    "held", false);
        }

        // ask Redis how many seconds are left on the hold
        Long seconds = redisTemplate.getExpire(holdKey, TimeUnit.SECONDS);
        long secondsLeft = (seconds == null) ? 0L : Math.max(seconds, 0L);

        return Map.of(
                "seatNumber", seatNumber,
                "seatStatus", seat.getStatus(),
                "held", true,
                "heldBy", holder,
                "secondsLeft", secondsLeft);
    }

    // the person who holds a seat gives it back before the 2 minutes end
    public String releaseHold(Long showId, String seatNumber, String userName) {
        requireUser(userName);
        String holdKey = holdKey(showId, seatNumber);

        // Redis deletes the hold only if it belongs to this user (one safe step)
        Long deleted = redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(holdKey), userName);

        if (deleted != null && deleted == 1L) {
            return "Hold on seat " + seatNumber + " released. The seat is available again.";
        }

        // Nothing was deleted: find out why, to give a clear message
        String holder = redisTemplate.opsForValue().get(holdKey);
        if (holder == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "There is no active hold on this seat.");
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "This hold belongs to another user.");
    }

    // ---------- 2. confirm (finalize) a held seat ----------

    public Booking confirmBooking(Long showId, String seatNumber, String userName) {
        requireUser(userName);
        String holdKey = holdKey(showId, seatNumber);
        String heldBy = redisTemplate.opsForValue().get(holdKey);

        if (heldBy == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seat hold has expired or was never placed.");
        }
        if (!heldBy.equals(userName)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This hold belongs to another user.");
        }

        // The transaction is committed when this call returns
        Booking booking = bookingTx.executeBooking(showId, seatNumber, userName);

        // Delete the hold only AFTER the booking is saved in MySQL
        redisTemplate.delete(holdKey);
        return booking;
    }

    // ---------- 3. direct booking with Redis lock ----------

    public Booking bookSeat(Long showId, String seatNumber, String userName) {
        requireUser(userName);

        // A seat held by someone else cannot be booked directly
        String holder = redisTemplate.opsForValue().get(holdKey(showId, seatNumber));
        if (holder != null && !holder.equals(userName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seat is currently on temporary hold by another user.");
        }

        String lockKey = lockKey(showId, seatNumber);
        String token = UUID.randomUUID().toString();

        Boolean isLocked = redisTemplate.opsForValue().setIfAbsent(lockKey, token, LOCK_TTL);
        if (Boolean.FALSE.equals(isLocked)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Seat is currently being booked by another user. Please try again.");
        }

        try {
            Booking booking = bookingTx.executeBooking(showId, seatNumber, userName);
            if (userName.equals(holder)) {
                redisTemplate.delete(holdKey(showId, seatNumber));
            }
            return booking;
        } finally {
            // release only our own lock
            redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey), token);
        }
    }

    // ---------- cancel ----------

    @Transactional
    public void cancelBooking(Long bookingId, String userName) {
        requireUser(userName);

        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (!booking.getUserName().equals(userName)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only cancel your own booking.");
        }

        seats.findByShowIdAndSeatNumber(booking.getShowId(), booking.getSeatNumber())
                .ifPresent(seat -> {
                    seat.setStatus("AVAILABLE");
                    seat.setBookedBy(null);
                    seats.save(seat);
                });

        bookings.delete(booking);
    }

    // ---------- availability ----------

    public Map<String, Long> availability(Long showId) {
        long total = seats.countByShowId(showId);
        if (total == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found");
        }
        long available = seats.countByShowIdAndStatus(showId, "AVAILABLE");
        return Map.of("total", total, "available", available, "booked", total - available);
    }
}