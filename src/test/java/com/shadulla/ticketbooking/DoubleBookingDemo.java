package com.shadulla.ticketbooking;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Run this while the app is running. It sends many booking requests for the SAME seat at the same moment.
public class DoubleBookingDemo {

    private static final String BASE = "http://localhost:8081/api";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    public static void main(String[] args) throws Exception {
        int users = 20;

        // 1. Create a fresh show
        String showJson = send(HttpRequest.newBuilder(URI.create(BASE + "/shows"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"name\":\"Race Test\",\"venue\":\"Test Hall\","
                                + "\"showTime\":\"2026-12-25T20:00:00\",\"seatCount\":5}"))
                .build()).body();
        Matcher m = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(showJson);
        if (!m.find()) {
            System.out.println("Could not create the show. Is the app running? Response: " + showJson);
            return;
        }
        String showId = m.group(1);

        // 2. Many users try to book seat S1 at the same moment
        CountDownLatch ready = new CountDownLatch(users);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();

        for (int i = 1; i <= users; i++) {
            String user = "user" + i;
            Thread t = new Thread(() -> {
                try {
                    HttpRequest req = HttpRequest.newBuilder(URI.create(BASE + "/shows/" + showId + "/book"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(
                                    "{\"seatNumber\":\"S1\",\"userName\":\"" + user + "\"}"))
                            .build();
                    ready.countDown();
                    go.await();                       // all threads wait here, then start together
                    HttpResponse<String> resp = send(req);
                    if (resp.statusCode() == 200) {
                        accepted.incrementAndGet();
                    } else {
                        rejected.incrementAndGet();
                    }
                } catch (Exception e) {
                    rejected.incrementAndGet();
                }
            });
            threads.add(t);
            t.start();
        }
        ready.await();
        go.countDown();                               // start!
        for (Thread t : threads) {
            t.join();
        }

        // 3. Count how many bookings exist for the show (all of them are for seat S1)
        String bookings = send(HttpRequest.newBuilder(URI.create(BASE + "/shows/" + showId + "/bookings"))
                .GET().build()).body();
        Matcher seatMatcher = Pattern.compile("\"seatNumber\"").matcher(bookings);
        int bookingCount = 0;
        while (seatMatcher.find()) {
            bookingCount++;
        }

        System.out.println("-----------------------------------------");
        System.out.println("Users who tried to book seat S1 : " + users);
        System.out.println("Requests accepted (HTTP 200)     : " + accepted.get());
        System.out.println("Requests rejected                : " + rejected.get());
        System.out.println("Bookings saved for seat S1       : " + bookingCount);
        System.out.println(bookingCount > 1
                ? "DOUBLE BOOKING! The same seat was sold " + bookingCount + " times."
                : "Only 1 booking this time. Run it again.");
        System.out.println("-----------------------------------------");
    }

    private static HttpResponse<String> send(HttpRequest request) throws Exception {
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
}