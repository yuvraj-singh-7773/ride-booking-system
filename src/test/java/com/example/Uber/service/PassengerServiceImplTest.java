package com.example.Uber.service;

import com.example.Uber.dto.PassengerRequest;
import com.example.Uber.dto.PassengerResponse;
import com.example.Uber.entity.Passenger;
import com.example.Uber.mapper.PassengerMapper;
import com.example.Uber.repository.PassengerRepository;
import com.example.Uber.service.impl.PassengerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PassengerServiceImplTest {

    @Mock
    private PassengerRepository passengerRepository;

    @Spy
    private PassengerMapper passengerMapper = new PassengerMapper();

    @InjectMocks
    private PassengerServiceImpl passengerService;

    private Passenger passenger;

    @BeforeEach
    void setUp() {
        passenger = Passenger.builder()
                .id(1L)
                .name("Jane")
                .email("jane@example.com")
                .phoneNumber("1234567890")
                .build();
    }

    @Test
    void testCreatePassenger_Success() {
        PassengerRequest request = PassengerRequest.builder()
                .name("Jane")
                .email("jane@example.com")
                .phoneNumber("1234567890")
                .build();

        when(passengerRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passengerRepository.save(any(Passenger.class))).thenAnswer(inv -> {
            Passenger p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        PassengerResponse response = passengerService.create(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("jane@example.com", response.getEmail());
    }

    @Test
    void testCreatePassenger_DuplicateEmailThrowsException() {
        PassengerRequest request = PassengerRequest.builder()
                .name("Jane")
                .email("jane@example.com")
                .phoneNumber("1234567890")
                .build();

        when(passengerRepository.existsByEmail("jane@example.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> passengerService.create(request));
    }

    @Test
    void testUpdatePassenger_Success() {
        PassengerRequest request = PassengerRequest.builder()
                .name("Jane Updated")
                .email("jane@example.com")
                .phoneNumber("9876543210")
                .build();

        when(passengerRepository.findById(1L)).thenReturn(Optional.of(passenger));
        when(passengerRepository.save(any(Passenger.class))).thenAnswer(inv -> inv.getArgument(0));

        PassengerResponse response = passengerService.update(1L, request);

        assertEquals("Jane Updated", response.getName());
        assertEquals("9876543210", response.getPhoneNumber());
    }
}
