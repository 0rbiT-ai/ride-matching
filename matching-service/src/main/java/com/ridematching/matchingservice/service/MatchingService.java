package com.ridematching.matchingservice.service;

import com.ridematching.matchingservice.client.LocationServiceClient;
import com.ridematching.matchingservice.dto.NearByDriverResponse;
import com.ridematching.matchingservice.event.RideMatchedEvent;
import com.ridematching.matchingservice.event.RideRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class MatchingService {
    private final LocationServiceClient locationServiceClient;
    private final KafkaTemplate<String, RideMatchedEvent> kafkaTemplate;
    private static final String RIDE_MATCHED_TOPIC = "ride.matched";
    private static final double DEFAULT_SEARCH_RADIUS_KM = 5.0;

    /**
     * driver scoring algorithm
     *
     * distance weightage: 70%
     * rating: 30%
     *
     * distance+=0.1 to avoid division by 0 in case distance=0
     * score = (1 / distance) * distanceWeight + rating * ratingWeight;
     */
    private Optional<NearByDriverResponse> findBestDriver(List<NearByDriverResponse> nearByDrivers) {
        double distanceWeight = 0.7;
        double ratingWeight = 0.3;

        return nearByDrivers.stream()
                .max(Comparator.comparingDouble(driver->{
                    double distanceScore = (1.0/driver.getDistanceInKm()+0.1);

                    //simulated rating between 4.0 and 5.0
                    double simulatedRating = 4.0 + Math.random();

                    return distanceScore * distanceWeight + simulatedRating * ratingWeight;
                }));

    }

    /**
     * main matching algorithm
     * called when ride requested event is consumed
     *
     * ask location service for nearby drivers
     * score each driver and pick the best one
     * publish ride matched event to kafka for ride service to consume and assign driver
     */

    public void matchDriverForRide(RideRequestedEvent event){
        List<NearByDriverResponse> nearByDrivers = locationServiceClient.getNearByDrivers(
                event.getPickupLatitude(),
                event.getPickupLongitude(),
                DEFAULT_SEARCH_RADIUS_KM
        );

        if (nearByDrivers.isEmpty()){
            log.warn("No drivers found near ride: {}",event.getRideId());
            return;
        }

        Optional<NearByDriverResponse> bestDriver = findBestDriver(nearByDrivers);

        if (bestDriver.isEmpty()){
            log.warn("Could not find best driver for ride: {}",event.getRideId());
            return;
        }

        NearByDriverResponse assignedDriver = bestDriver.get();

        RideMatchedEvent matchedEvent = new RideMatchedEvent(
                event.getRideId(),
                event.getRiderId(),
                assignedDriver.getDriverId(),
                assignedDriver.getLatitude(),
                assignedDriver.getLongitude(),
                assignedDriver.getDistanceInKm()
        );

        kafkaTemplate.send(RIDE_MATCHED_TOPIC,event.getRideId(),matchedEvent);
        log.info("Ride matched event published");
    }


}
