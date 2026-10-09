package com.example.Uber.service.impl;

import com.example.Uber.dto.DriverLocationDTO;
import com.example.Uber.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.connection.RedisGeoCommands.GeoLocation;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RedisLocationServiceImpl implements LocationService {

    private static final String DRIVER_GEO_OPS_KEY = "driver:geo"; // represent location of driver entity in redis

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Boolean saveDriverLocation(String driverId, Double latitude, Double longitude) {
        if (driverId == null || driverId.trim().isEmpty()) {
            throw new IllegalArgumentException("Driver ID cannot be null or empty");
        }
        if (latitude == null || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (longitude == null || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }

        GeoOperations<String, String> geoOperations = stringRedisTemplate.opsForGeo();

        // Spring Data Redis Point(x, y) expects x = longitude, y = latitude
        geoOperations.add(DRIVER_GEO_OPS_KEY,
                new RedisGeoCommands.GeoLocation<>(driverId, new Point(longitude, latitude))
        );

        return true;
    }

    @Override
    public List<DriverLocationDTO> getNearbyDrivers(Double latitude, Double longitude, Double radius) {
        if (latitude == null || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (longitude == null || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
        if (radius == null || radius <= 0) {
            throw new IllegalArgumentException("Radius must be greater than 0");
        }

        GeoOperations<String, String> geoOperations = stringRedisTemplate.opsForGeo();

        Distance circleRadius = new Distance(radius, Metrics.KILOMETERS);
        // Spring Data Redis Point(x, y) expects x = longitude, y = latitude
        Circle circle = new Circle(new Point(longitude, latitude), circleRadius);

        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                .newGeoRadiusArgs()
                .includeCoordinates();

        GeoResults<GeoLocation<String>> results = geoOperations.radius(DRIVER_GEO_OPS_KEY, circle, args);

        if (results == null) {
            return Collections.emptyList();
        }

        List<DriverLocationDTO> driverLocations = new ArrayList<>();

        for (GeoResult<GeoLocation<String>> result : results) {
            if (result == null || result.getContent() == null) {
                continue;
            }

            String driverId = result.getContent().getName();
            Point point = result.getContent().getPoint();

            // Fallback to position lookup if coordinate was not populated
            if (point == null) {
                List<Point> positions = geoOperations.position(DRIVER_GEO_OPS_KEY, driverId);
                if (positions != null && !positions.isEmpty()) {
                    point = positions.get(0);
                }
            }

            if (point != null) {
                DriverLocationDTO driverLocation = DriverLocationDTO.builder()
                        .driverId(driverId)
                        .latitude(point.getY())
                        .longitude(point.getX())
                        .build();

                driverLocations.add(driverLocation);
            }
        }

        return driverLocations;
    }
}