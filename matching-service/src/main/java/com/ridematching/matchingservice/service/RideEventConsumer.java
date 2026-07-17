package com.ridematching.matchingservice.service;

import com.ridematching.matchingservice.event.RideRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RideEventConsumer {
    private final MatchingService matchingService;

    /**
     * listens to ride.requested kafka topic
     * triggered every time Ride Service publishes ride request
     */

    @KafkaListener(topics = "ride.requested",groupId = "matching-service-group")
    public void consumeRideRequestedEvent(RideRequestedEvent event){
        try {
            matchingService.matchDriverForRide(event);
        }catch (Exception e){
            log.info("Error processing Ride Request: {} - {}", event.getRideId(),e.getMessage());
        }
    }
}
