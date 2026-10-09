package com.example.Uber.service;

import com.example.Uber.dto.DriverLocationDTO;
import com.example.Uber.service.impl.RedisLocationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RedisLocationServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private GeoOperations<String, String> geoOperations;

    private RedisLocationServiceImpl locationService;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForGeo()).thenReturn(geoOperations);
        locationService = new RedisLocationServiceImpl(stringRedisTemplate);
    }

    @Test
    void testSaveDriverLocation_CorrectCoordinatesOrdering() {
        double latitude = 37.7749;
        double longitude = -122.4194;
        String driverId = "driver-1";

        Boolean result = locationService.saveDriverLocation(driverId, latitude, longitude);

        assertTrue(result);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<GeoLocation<String>> captor = ArgumentCaptor.forClass(GeoLocation.class);
        verify(geoOperations).add(eq("driver:geo"), captor.capture());

        GeoLocation<String> captured = captor.getValue();
        assertEquals(driverId, captured.getName());
        // Verify x is longitude and y is latitude
        assertEquals(longitude, captured.getPoint().getX(), 0.0001);
        assertEquals(latitude, captured.getPoint().getY(), 0.0001);
    }

    @Test
    void testSaveDriverLocation_InvalidInputs() {
        assertThrows(IllegalArgumentException.class, () ->
                locationService.saveDriverLocation(null, 37.77, -122.41));
        assertThrows(IllegalArgumentException.class, () ->
                locationService.saveDriverLocation(" ", 37.77, -122.41));
        assertThrows(IllegalArgumentException.class, () ->
                locationService.saveDriverLocation("driver-1", 95.0, -122.41));
        assertThrows(IllegalArgumentException.class, () ->
                locationService.saveDriverLocation("driver-1", 37.77, 185.0));
    }

    @Test
    void testGetNearbyDrivers_Success() {
        double latitude = 37.7749;
        double longitude = -122.4194;
        double radius = 5.0;

        Point driverPoint = new Point(longitude, latitude);
        GeoLocation<String> location = new GeoLocation<>("driver-1", driverPoint);
        GeoResult<GeoLocation<String>> geoResult = new GeoResult<>(location, new org.springframework.data.geo.Distance(1.2));
        GeoResults<GeoLocation<String>> geoResults = new GeoResults<>(List.of(geoResult));

        when(geoOperations.radius(eq("driver:geo"), any(Circle.class), any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
                .thenReturn(geoResults);

        List<DriverLocationDTO> nearby = locationService.getNearbyDrivers(latitude, longitude, radius);

        assertNotNull(nearby);
        assertEquals(1, nearby.size());
        assertEquals("driver-1", nearby.get(0).getDriverId());
        assertEquals(latitude, nearby.get(0).getLatitude(), 0.0001);
        assertEquals(longitude, nearby.get(0).getLongitude(), 0.0001);
    }

    @Test
    void testGetNearbyDrivers_EmptyResults() {
        when(geoOperations.radius(eq("driver:geo"), any(Circle.class), any(RedisGeoCommands.GeoRadiusCommandArgs.class)))
                .thenReturn(null);

        List<DriverLocationDTO> nearby = locationService.getNearbyDrivers(37.77, -122.41, 5.0);

        assertNotNull(nearby);
        assertTrue(nearby.isEmpty());
    }

    @Test
    void testGetNearbyDrivers_InvalidRadius() {
        assertThrows(IllegalArgumentException.class, () ->
                locationService.getNearbyDrivers(37.77, -122.41, -1.0));
        assertThrows(IllegalArgumentException.class, () ->
                locationService.getNearbyDrivers(37.77, -122.41, 0.0));
    }
}
