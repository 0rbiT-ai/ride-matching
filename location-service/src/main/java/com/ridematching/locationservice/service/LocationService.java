package com.ridematching.locationservice.service;

import com.ridematching.locationservice.dto.DriverLocationRequest;
import com.ridematching.locationservice.dto.NearByDriverResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocationService {

    private final RedisTemplate<String,String> redisTemplate;

    //Redis key for driver locations
    private static final String DRIVERS_GEO_KEY = "drivers:locations";

    /**
     * update driver location in Redis
     * called every 3 seconds by driver's app
     * maps to Redis GEOADD command
     */
    public void updateDriverLocation(DriverLocationRequest request){
        log.info("Updating location for driver: {}",request.getDriverId());

        Point driverPoint = new Point(request.getLongitude(),request.getLatitude());

        redisTemplate.opsForGeo().add(DRIVERS_GEO_KEY,driverPoint,request.getDriverId());

        log.info("Location updated for driver: {}",request.getDriverId());
    }

    /**
     * finds nearby drivers within given radius
     * called by matching service on ride request
     * maps to Redis GEORADIUS command
     */
    public List<NearByDriverResponse> findNearByDrivers(double longitude, double latitude, double radiusInKm){
        log.info("Finding drivers near lat: {} long: {} radius: {}",latitude,longitude,radiusInKm);

        Circle searchArea = new Circle(new Point(latitude,longitude),new Distance(radiusInKm, Metrics.KILOMETERS));

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().radius(
                DRIVERS_GEO_KEY,
                searchArea,
                RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                        .includeCoordinates()
                        .includeDistance()
                        .sortAscending()
                        .limit(10)
        );

        List<NearByDriverResponse> nearByDrivers = new ArrayList<>();

        if (results!=null){
            results.getContent().forEach(result->{
                RedisGeoCommands.GeoLocation<String> location = result.getContent();
                nearByDrivers.add(new NearByDriverResponse(
                        location.getName(),
                        location.getPoint().getX(),
                        location.getPoint().getX(),
                        result.getDistance().getValue()
                ));
            });
        }

        log.info("Found {} drivers nearby",nearByDrivers.size());

        return nearByDrivers;
    }

    /**
     * remove driver when driver's app is offline
     * maps to Redis ZREM command
     */
    public void removeDriver(String driverId) {
        log.info("Removing driver: {}",driverId);

        redisTemplate.opsForGeo().remove(DRIVERS_GEO_KEY,driverId);

        log.info("Driver with id: {} removed successfully",driverId);
    }
}
