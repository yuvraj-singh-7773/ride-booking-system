package com.example.Uber.service;

import com.example.Uber.dto.BookingRequest;
import com.example.Uber.dto.BookingResponse;
import com.example.Uber.entity.Booking;
import com.example.Uber.entity.Driver;
import com.example.Uber.entity.Passenger;
import com.example.Uber.mapper.BookingMapper;
import com.example.Uber.repository.BookingRepository;
import com.example.Uber.repository.DriverRepository;
import com.example.Uber.repository.PassengerRepository;
import com.example.Uber.service.impl.BookingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BookingServiceImplTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PassengerRepository passengerRepository;

    @Mock
    private DriverRepository driverRepository;

    @Spy
    private BookingMapper bookingMapper = new BookingMapper();

    @InjectMocks
    private BookingServiceImpl bookingService;

    private Passenger passenger;
    private Driver driver;

    @BeforeEach
    void setUp() {
        passenger = Passenger.builder()
                .id(1L)
                .name("Alice")
                .email("alice@example.com")
                .phoneNumber("1234567890")
                .build();

        driver = Driver.builder()
                .id(2L)
                .name("Bob")
                .email("bob@example.com")
                .phoneNumber("0987654321")
                .licenseNumber("DL999")
                .isAvailable(true)
                .build();
    }

    @Test
    void testCreateBooking_SuccessWithDriver() {
        BookingRequest request = BookingRequest.builder()
                .passengerId(1L)
                .driverId(2L)
                .pickupLocation("Point A")
                .dropoffLocation("Point B")
                .fare(new BigDecimal("25.50"))
                .build();

        when(passengerRepository.findById(1L)).thenReturn(Optional.of(passenger));
        when(driverRepository.findById(2L)).thenReturn(Optional.of(driver));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking b = invocation.getArgument(0);
            b.setId(10L);
            return b;
        });

        BookingResponse response = bookingService.create(request);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals(Booking.BookingStatus.CONFIRMED, response.getStatus());
        assertFalse(driver.getIsAvailable());
        verify(driverRepository).save(driver);
    }

    @Test
    void testCreateBooking_DriverUnavailableThrowsException() {
        driver.setIsAvailable(false);
        BookingRequest request = BookingRequest.builder()
                .passengerId(1L)
                .driverId(2L)
                .pickupLocation("Point A")
                .dropoffLocation("Point B")
                .fare(new BigDecimal("25.50"))
                .build();

        when(passengerRepository.findById(1L)).thenReturn(Optional.of(passenger));
        when(driverRepository.findById(2L)).thenReturn(Optional.of(driver));

        assertThrows(IllegalArgumentException.class, () -> bookingService.create(request));
    }

    @Test
    void testUpdateBooking_ChangeDriverUpdatesAvailability() {
        Driver driver2 = Driver.builder()
                .id(3L)
                .name("Charlie")
                .email("charlie@example.com")
                .phoneNumber("5555555555")
                .licenseNumber("DL555")
                .isAvailable(true)
                .build();

        Booking booking = Booking.builder()
                .id(10L)
                .passenger(passenger)
                .driver(driver)
                .pickupLocation("Point A")
                .dropoffLocation("Point B")
                .fare(new BigDecimal("25.50"))
                .status(Booking.BookingStatus.CONFIRMED)
                .build();

        BookingRequest request = BookingRequest.builder()
                .passengerId(1L)
                .driverId(3L)
                .pickupLocation("Point A")
                .dropoffLocation("Point C")
                .fare(new BigDecimal("30.00"))
                .build();

        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(passengerRepository.findById(1L)).thenReturn(Optional.of(passenger));
        when(driverRepository.findById(3L)).thenReturn(Optional.of(driver2));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = bookingService.update(10L, request);

        assertNotNull(response);
        assertTrue(driver.getIsAvailable());
        assertFalse(driver2.getIsAvailable());
        verify(driverRepository).save(driver);
        verify(driverRepository).save(driver2);
    }

    @Test
    void testUpdateStatus_CompleteRidesReleasesDriver() {
        Booking booking = Booking.builder()
                .id(10L)
                .passenger(passenger)
                .driver(driver)
                .pickupLocation("Point A")
                .dropoffLocation("Point B")
                .fare(new BigDecimal("25.50"))
                .status(Booking.BookingStatus.IN_PROGRESS)
                .build();

        driver.setIsAvailable(false);

        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse response = bookingService.updateStatus(10L, Booking.BookingStatus.COMPLETED);

        assertEquals(Booking.BookingStatus.COMPLETED, response.getStatus());
        assertTrue(driver.getIsAvailable());
        verify(driverRepository).save(driver);
    }

    @Test
    void testUpdateStatus_InvalidTransitions() {
        Booking booking = Booking.builder()
                .id(10L)
                .passenger(passenger)
                .driver(null)
                .pickupLocation("Point A")
                .dropoffLocation("Point B")
                .fare(new BigDecimal("25.50"))
                .status(Booking.BookingStatus.PENDING)
                .build();

        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking));

        // Starting ride without driver should fail
        assertThrows(IllegalArgumentException.class, () ->
                bookingService.updateStatus(10L, Booking.BookingStatus.IN_PROGRESS));
    }

    @Test
    void testDeleteById_ReleasesDriverOnlyIfActive() {
        Booking completedBooking = Booking.builder()
                .id(10L)
                .passenger(passenger)
                .driver(driver)
                .status(Booking.BookingStatus.COMPLETED)
                .build();

        driver.setIsAvailable(false); // maybe driver is on another ride now

        when(bookingRepository.findById(10L)).thenReturn(Optional.of(completedBooking));

        bookingService.deleteById(10L);

        // Driver shouldn't be touched because booking was already completed!
        assertFalse(driver.getIsAvailable());
        verify(driverRepository, never()).save(any());
        verify(bookingRepository).deleteById(10L);
    }
}
