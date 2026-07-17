package com.ridematching.rideservice.service;


import com.ridematching.rideservice.event.RideMatchedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * listens to ride.matched kafka topic
 * triggered every time Matching Service publishes best matched nearby driver
 */

@Service
@Slf4j
@RequiredArgsConstructor
public class MatchedEventConsumer {
    private final RideService rideService;

    @KafkaListener(topics = "ride.matched",groupId = "ride-service-group")
    public void consumeRideMatchedEvent(RideMatchedEvent event){
        try {
            rideService.updateRideWithDriver(event.getRideId(), event.getDriverId());
        } catch (Exception e) {
            log.info("Error processing Driver Update for ride: {} - {}", event.getRideId(),e.getMessage());
        }
    }
}
