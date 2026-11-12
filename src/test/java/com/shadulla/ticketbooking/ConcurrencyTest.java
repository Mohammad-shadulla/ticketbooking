package com.shadulla.ticketbooking;

import com.shadulla.ticketbooking.entity.Seat;
import com.shadulla.ticketbooking.entity.Show;
import com.shadulla.ticketbooking.repository.SeatRepository;
import com.shadulla.ticketbooking.repository.ShowRepository;
import com.shadulla.ticketbooking.service.TicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
public class ConcurrencyTest {

    @Autowired
    private TicketService ticketService;

    @Autowired
    private ShowRepository showRepository;

    @Autowired
    private SeatRepository seatRepository;

    private Long testShowId;
    private final String testSeatNumber = "S1";

    @BeforeEach
    void setUp() {
        // Create a mock show and seat in the database before each test run
        Show show = new Show();
        show.setName("Concurrency Test Show");
        show.setVenue("Test Arena");
        show.setShowTime(LocalDateTime.now().plusDays(1));
        Show savedShow = showRepository.save(show);
        testShowId = savedShow.getId();

        Seat seat = new Seat();
        seat.setShowId(testShowId);
        seat.setSeatNumber(testSeatNumber);
        seat.setStatus("AVAILABLE");
        seatRepository.save(seat);
    }

    @Test
    public void testConcurrentBooking() throws InterruptedException {
        int numberOfThreads = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        System.out.println("=== STARTING CONCURRENT BOOKING TEST (10 Threads targeting seat S1) ===");

        for (int i = 0; i < numberOfThreads; i++) {
            final String user = "User-" + i;
            executorService.submit(() -> {
                try {
                    ticketService.bookSeat(testShowId, testSeatNumber, user);
                    successCount.incrementAndGet();
                    System.out.println(user + " -> SUCCESS: Booked the seat.");
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.out.println(user + " -> BLOCKED: " + e.getMessage());
                }
            });
        }

        executorService.shutdown();
        executorService.awaitTermination(15, TimeUnit.SECONDS);

        System.out.println("=== TEST RESULTS SUMMARY ===");
        System.out.println("Successful Bookings (Expected: 1)  -> " + successCount.get());
        System.out.println("Blocked / Failed Bookings (Expected: 9) -> " + failureCount.get());

        // Assertions to automatically verify correctness
        assert successCount.get() == 1 : "Error: More than one user booked the same seat! Lock failed.";
        assert failureCount.get() == 9 : "Error: Not all concurrent requests were blocked properly.";
        System.out.println("=== CONCURRENCY TEST PASSED SUCCESSFULLY! ===");
    }
}