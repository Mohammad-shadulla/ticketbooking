package com.shadulla.ticketbooking;

import com.shadulla.ticketbooking.entity.Show;
import com.shadulla.ticketbooking.service.TicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

// 20 users try to book the SAME seat at the same moment.
// Before the fix: the seat was sold 3 times.
// After the fix: exactly 1 success.
// MySQL and Redis must be running.
@SpringBootTest
class DoubleBookingTest {

    @Autowired
    private TicketService ticketService;

    @Test
    void onlyOneUserCanBookTheSameSeat() throws Exception {
        Show show = ticketService.createShow("Test Show", "Test Hall",
                LocalDateTime.now().plusDays(1), 5);

        int users = 20;
        ExecutorService pool = Executors.newFixedThreadPool(users);
        CountDownLatch ready = new CountDownLatch(users);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        for (int i = 0; i < users; i++) {
            final String user = "user" + i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    ticketService.bookSeat(show.getId(), "S1", user);
                    success.incrementAndGet();
                } catch (Exception e) {
                    failed.incrementAndGet();
                }
            });
        }

        ready.await();
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        System.out.println("Success = " + success.get() + ", Failed = " + failed.get());

        assertEquals(1, success.get(), "Exactly one user must get the seat");
        assertEquals(users - 1, failed.get());
        assertEquals(1L, ticketService.availability(show.getId()).get("booked"));
    }
}