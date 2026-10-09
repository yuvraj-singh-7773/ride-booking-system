package com.example.Uber.service.impl;

import com.example.Uber.dto.BookingRequest;
import com.example.Uber.dto.BookingResponse;
import com.example.Uber.entity.Booking;
import com.example.Uber.entity.Driver;
import com.example.Uber.entity.Passenger;
import com.example.Uber.mapper.BookingMapper;
import com.example.Uber.repository.BookingRepository;
import com.example.Uber.repository.DriverRepository;
import com.example.Uber.repository.PassengerRepository;
import com.example.Uber.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BookingServiceImpl implements BookingService {
    
    private final BookingRepository bookingRepository;
    private final PassengerRepository passengerRepository;
    private final DriverRepository driverRepository;
    private final BookingMapper bookingMapper;
    
    @Override
    @Transactional(readOnly = true)
    public Optional<BookingResponse> findById(Long id) {
        return bookingRepository.findById(id)
                .map(bookingMapper::toResponse);
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> findAll() {
        return bookingRepository.findAll().stream()
                .map(bookingMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> findByPassengerId(Long passengerId) {
        Passenger passenger = passengerRepository.findById(passengerId)
                .orElseThrow(() -> new IllegalArgumentException("Passenger not found with id: " + passengerId));
        return bookingRepository.findByPassenger(passenger).stream()
                .map(bookingMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> findByDriverId(Long driverId) {
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new IllegalArgumentException("Driver not found with id: " + driverId));
        return bookingRepository.findByDriver(driver).stream()
                .map(bookingMapper::toResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    public BookingResponse create(BookingRequest request) {
        Passenger passenger = passengerRepository.findById(request.getPassengerId())
                .orElseThrow(() -> new IllegalArgumentException("Passenger not found with id: " + request.getPassengerId()));
        
        Driver driver = null;
        if (request.getDriverId() != null) {
            driver = driverRepository.findById(request.getDriverId())
                    .orElseThrow(() -> new IllegalArgumentException("Driver not found with id: " + request.getDriverId()));
            
            if (!Boolean.TRUE.equals(driver.getIsAvailable())) {
                throw new IllegalArgumentException("Driver with id " + request.getDriverId() + " is not available");
            }
        }
        
        Booking booking = bookingMapper.toEntity(request, passenger, driver);
        
        // If driver is assigned, mark as unavailable
        if (driver != null) {
            driver.setIsAvailable(false);
            driverRepository.save(driver);
        }
        
        Booking savedBooking = bookingRepository.save(booking);
        return bookingMapper.toResponse(savedBooking);
    }
    
    @Override
    public BookingResponse update(Long id, BookingRequest request) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found with id: " + id));
        
        Passenger passenger = passengerRepository.findById(request.getPassengerId())
                .orElseThrow(() -> new IllegalArgumentException("Passenger not found with id: " + request.getPassengerId()));
        
        Driver driver = null;
        if (request.getDriverId() != null) {
            driver = driverRepository.findById(request.getDriverId())
                    .orElseThrow(() -> new IllegalArgumentException("Driver not found with id: " + request.getDriverId()));
        }
        
        // Handle driver availability when updating by comparing driver IDs
        Driver previousDriver = booking.getDriver();
        Long previousDriverId = previousDriver != null ? previousDriver.getId() : null;
        Long newDriverId = driver != null ? driver.getId() : null;

        if (!Objects.equals(previousDriverId, newDriverId)) {
            if (previousDriver != null) {
                previousDriver.setIsAvailable(true);
                driverRepository.save(previousDriver);
            }
            if (driver != null) {
                if (!Boolean.TRUE.equals(driver.getIsAvailable())) {
                    throw new IllegalArgumentException("Driver with id " + request.getDriverId() + " is not available");
                }
                driver.setIsAvailable(false);
                driverRepository.save(driver);
            }
        }
        
        bookingMapper.updateEntity(booking, request, passenger, driver);
        Booking updatedBooking = bookingRepository.save(booking);
        return bookingMapper.toResponse(updatedBooking);
    }
    
    @Override
    public BookingResponse updateStatus(Long id, Booking.BookingStatus status) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found with id: " + id));
        
        if (status == null) {
            throw new IllegalArgumentException("Booking status cannot be null");
        }

        // Handle status-specific logic and validate status transitions
        if (status == Booking.BookingStatus.IN_PROGRESS) {
            if (booking.getDriver() == null) {
                throw new IllegalArgumentException("Cannot start ride without an assigned driver");
            }
            if (booking.getActualPickupTime() == null) {
                booking.setActualPickupTime(LocalDateTime.now());
            }
        } else if (status == Booking.BookingStatus.COMPLETED) {
            if (booking.getStatus() == Booking.BookingStatus.CANCELLED) {
                throw new IllegalArgumentException("Cannot complete a cancelled booking");
            }
            booking.setCompletedAt(LocalDateTime.now());
            // Release driver
            if (booking.getDriver() != null) {
                Driver driver = booking.getDriver();
                driver.setIsAvailable(true);
                driverRepository.save(driver);
            }
        } else if (status == Booking.BookingStatus.CANCELLED) {
            if (booking.getStatus() == Booking.BookingStatus.COMPLETED) {
                throw new IllegalArgumentException("Cannot cancel an already completed booking");
            }
            // Release driver
            if (booking.getDriver() != null) {
                Driver driver = booking.getDriver();
                driver.setIsAvailable(true);
                driverRepository.save(driver);
            }
        }
        
        booking.setStatus(status);
        Booking updatedBooking = bookingRepository.save(booking);
        return bookingMapper.toResponse(updatedBooking);
    }
    
    @Override
    public void deleteById(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found with id: " + id));
        
        // Release driver only if the booking is currently active (not already completed or cancelled)
        if (booking.getDriver() != null && 
                booking.getStatus() != Booking.BookingStatus.COMPLETED && 
                booking.getStatus() != Booking.BookingStatus.CANCELLED) {
            Driver driver = booking.getDriver();
            driver.setIsAvailable(true);
            driverRepository.save(driver);
        }
        
        bookingRepository.deleteById(id);
    }
}
