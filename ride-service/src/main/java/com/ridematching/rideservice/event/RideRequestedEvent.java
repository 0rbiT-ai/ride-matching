package com.ridematching.rideservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * event published to kafka when new ride is requested by rider
 * consumed by matching service
 * TOPIC : ride.requested
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RideRequestedEvent {
    private String rideId;
    private String riderId;

    private double pickupLatitude;
    private double pickupLongitude;
    private String pickupAddress;

    private double dropLatitude;
    private double dropLongitude;
    private String dropAddress;
}
