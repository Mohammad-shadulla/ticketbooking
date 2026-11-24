# Ticket Booking System

A backend REST API for booking show tickets, built with **Spring Boot**, **MySQL** and **Redis**.
Its main goal: **no seat can ever be sold twice**, even when many users click the same seat at the same moment.

## Features

- Create shows with any number of seats
- Hold a seat for 2 minutes (Redis), then confirm it to finish the booking
- Direct booking protected by a Redis lock
- Release a hold early (only the user who holds it)
- Hold status with a live countdown (seconds left)
- Cancel a booking (only the owner can cancel)
- My Bookings: all bookings of one user, newest first
- Seat availability per show (total, available, booked)
- Clean error responses (404, 400, 403, 409, 503)

## Tech Stack

| Part | Technology |
|------|------------|
| Language | Java 21 |
| Framework | Spring Boot 4.1.2 (Web, Data JPA, Data Redis, Validation) |
| Database | MySQL 8 |
| Cache and locks | Redis (Memurai on Windows) |
| Build tool | Maven |
| Other | Lombok, JUnit 5 |

## How Double Booking Is Prevented

The problem: if 20 users click the same seat at the same time, a simple "check, then save" can sell the seat more than once.
This project uses three layers of protection:

1. **Redis lock**: only one request at a time can enter the booking step for a seat. Each lock has a random token, so a slow request never deletes someone else's lock.
2. **Database rules**: a unique constraint on `(show_id, seat_number)` in `bookings` and a `@Version` column on `seats`. Even if Redis fails, MySQL refuses the second booking.
3. **Transaction**: the seat update and the booking insert succeed or fail together.

**Proof:** `DoubleBookingTest` starts 20 users booking the same seat at once. Result: `Success = 1, Failed = 19`.

## Booking Flow

```
HOLD (2 min, Redis)  --->  CONFIRM (saved in MySQL)  --->  BOOKED
        |
        +--> RELEASE (give the seat back)   or   expires by itself after 2 minutes
```

## API Endpoints

Base URL: `http://localhost:8081/api/tickets`

| Method | URL | Description |
|--------|-----|-------------|
| POST | `/shows?name=&venue=&showTime=&seatCount=` | Create a show with seats S1...Sn |
| GET | `/shows` | List all shows |
| GET | `/shows/{showId}/seats` | List seats of a show |
| GET | `/shows/{showId}/free-seats` | Seats that can be booked right now (not booked, not on hold) |
| GET | `/shows/{showId}/bookings` | List bookings of a show |
| GET | `/shows/{showId}/availability` | Total, available and booked seats |
| GET | `/users/{userName}/bookings` | All bookings of one user (newest first) |
| POST | `/hold?showId=&seatNumber=&userName=` | Hold a seat for 2 minutes |
| GET | `/hold-status?showId=&seatNumber=` | Who holds the seat and seconds left |
| DELETE | `/hold?showId=&seatNumber=&userName=` | Release your hold |
| POST | `/confirm?showId=&seatNumber=&userName=` | Confirm a held seat (creates the booking) |
| POST | `/book?showId=&seatNumber=&userName=` | Book a seat directly (uses the Redis lock) |
| DELETE | `/cancel/{bookingId}?userName=` | Cancel your booking |

### Error codes

| Code | Meaning |
|------|---------|
| 400 | Missing or invalid input, or no active hold |
| 403 | The hold or booking belongs to another user |
| 404 | Show, seat or booking not found |
| 409 | Seat already booked, held, or just taken by someone else |
| 503 | Redis is not available |

### Example (PowerShell)

```powershell
# Create a show with 20 seats
Invoke-RestMethod -Method Post "http://localhost:8081/api/tickets/shows?name=Concert&venue=Hyderabad%20Hall&showTime=2026-12-30T18:00:00&seatCount=20"

# Hold seat S5, check the countdown, then confirm it
Invoke-RestMethod -Method Post "http://localhost:8081/api/tickets/hold?showId=1&seatNumber=S5&userName=Mohammad"
Invoke-RestMethod "http://localhost:8081/api/tickets/hold-status?showId=1&seatNumber=S5"
Invoke-RestMethod -Method Post "http://localhost:8081/api/tickets/confirm?showId=1&seatNumber=S5&userName=Mohammad"

# My bookings
Invoke-RestMethod "http://localhost:8081/api/tickets/users/Mohammad/bookings"
```

## Getting Started

### Requirements

- Java 21
- MySQL 8
- Redis (on Windows you can use [Memurai](https://www.memurai.com/))

### Setup

1. Create the database:
   ```sql
   CREATE DATABASE ticket_db;
   ```
2. Start MySQL and Redis (Redis runs on `localhost:6379`).
3. Create `src/main/resources/application-local.properties` (this file is ignored by git) and add your MySQL password:
   ```properties
   spring.datasource.password=your_mysql_password
   ```
4. Run the app:
   ```
   mvnw.cmd spring-boot:run
   ```
   The API starts on **http://localhost:8081**. Tables are created automatically.

### Run the tests

MySQL and Redis must be running.

```
mvnw.cmd test
```

## Project Structure

```
src/main/java/com/shadulla/ticketbooking
├── controller    REST endpoints (TicketController)
├── service       business logic (TicketService, BookingTxService)
├── repository    database access (Show, Seat, Booking)
├── entity        Show, Seat, Booking
└── exception     global error handling
```

## Roadmap

- [ ] RabbitMQ: send booking confirmation messages asynchronously
- [ ] Cancel history: keep cancelled bookings with a CANCELLED status
- [ ] User login (JWT) instead of passing `userName`
- [ ] Docker Compose for MySQL and Redis

## Author

**Mohammad Shadulla**
GitHub: [Mohammad-shadulla](https://github.com/Mohammad-shadulla)