package com.example.Uber.service;

import com.example.Uber.dto.DriverRequest;
import com.example.Uber.dto.DriverResponse;
import com.example.Uber.entity.Driver;
import com.example.Uber.mapper.DriverMapper;
import com.example.Uber.repository.DriverRepository;
import com.example.Uber.service.impl.DriverServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DriverServiceImplTest {

    @Mock
    private DriverRepository driverRepository;

    @Spy
    private DriverMapper driverMapper = new DriverMapper();

    @InjectMocks
    private DriverServiceImpl driverService;

    private Driver driver;

    @BeforeEach
    void setUp() {
        driver = Driver.builder()
                .id(1L)
                .name("John")
                .email("john@example.com")
                .phoneNumber("1234567890")
                .licenseNumber("LIC123")
                .isAvailable(true)
                .build();
    }

    @Test
    void testCreateDriver_Success() {
        DriverRequest request = DriverRequest.builder()
                .name("John")
                .email("john@example.com")
                .phoneNumber("1234567890")
                .licenseNumber("LIC123")
                .build();

        when(driverRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(driverRepository.existsByLicenseNumber("LIC123")).thenReturn(false);
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> {
            Driver d = inv.getArgument(0);
            d.setId(1L);
            return d;
        });

        DriverResponse response = driverService.create(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("john@example.com", response.getEmail());
        assertTrue(response.getIsAvailable());
    }

    @Test
    void testCreateDriver_DuplicateEmailThrowsException() {
        DriverRequest request = DriverRequest.builder()
                .name("John")
                .email("john@example.com")
                .phoneNumber("1234567890")
                .licenseNumber("LIC123")
                .build();

        when(driverRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> driverService.create(request));
    }

    @Test
    void testFindAvailableDrivers_UsesRepositoryQuery() {
        when(driverRepository.findByIsAvailableTrue()).thenReturn(List.of(driver));

        List<DriverResponse> available = driverService.findAvailableDrivers();

        assertEquals(1, available.size());
        assertEquals("John", available.get(0).getName());
        verify(driverRepository).findByIsAvailableTrue();
    }

    @Test
    void testUpdateDriver_Success() {
        DriverRequest request = DriverRequest.builder()
                .name("John Updated")
                .email("john@example.com")
                .phoneNumber("9999999999")
                .licenseNumber("LIC123")
                .build();

        when(driverRepository.findById(1L)).thenReturn(Optional.of(driver));
        when(driverRepository.save(any(Driver.class))).thenAnswer(inv -> inv.getArgument(0));

        DriverResponse response = driverService.update(1L, request);

        assertEquals("John Updated", response.getName());
        assertEquals("9999999999", response.getPhoneNumber());
    }
}
