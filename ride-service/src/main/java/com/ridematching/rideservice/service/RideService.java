package com.ridematching.rideservice.service;

import com.ridematching.rideservice.dto.RideRequest;
import com.ridematching.rideservice.dto.RideResponse;
import com.ridematching.rideservice.entity.Ride;
import com.ridematching.rideservice.entity.RideStatus;
import com.ridematching.rideservice.event.RideRequestedEvent;
import com.ridematching.rideservice.repository.RideRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class RideService {

    private final RideRepository rideRepository;
    private final KafkaTemplate<String, RideRequestedEvent> kafkaTemplate;
    private static final String RIDE_REQUESTED_TOPIC = "ride.requested";

    private RideResponse mapToResponse(Ride ride) {
        RideResponse response = new RideResponse();

        response.setId(ride.getId());
        response.setRiderId(ride.getRiderId());
        response.setDriverId(ride.getDriverId());

        response.setPickupLatitude(ride.getPickupLatitude());
        response.setPickupLongitude(ride.getPickupLongitude());
        response.setPickupAddress(ride.getPickupAddress());

        response.setDropLatitude(ride.getDropLatitude());
        response.setDropLongitude(ride.getDropLongitude());
        response.setDropAddress(ride.getDropAddress());

        response.setStatus(ride.getStatus());

        response.setEstimatedFare(ride.getEstimatedFare());
        response.setActualFare(ride.getActualFare());

        response.setCreatedAt(ride.getCreatedAt());
        response.setStartedAt(ride.getStartedAt());
        response.setCompletedAt(ride.getCompletedAt());

        return response;
    }

    private double calculateEstimateFare(RideRequest request) {
        //haversine calculation
        double lat1 = Math.toRadians(request.getPickupLatitude());
        double lat2 = Math.toRadians(request.getDropLatitude());

        double lon1 = Math.toRadians(request.getPickupLongitude());
        double lon2 = Math.toRadians(request.getDropLongitude());

        double dLat = lat2 - lat1;
        double dLon = lon2 - lon1;

        double a = Math.pow(Math.sin(dLat/2),2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon/2),2);
        double c = 2 * Math.asin(Math.sqrt(a));

        double distanceInKm = 6371 * c;

        //Base fare = 50 + 12 per km
        double fare = 50 + (12 * distanceInKm);
        return Math.round(fare*100.0)/100.0;

    }

    /**
     * create ride in db with status = REQUESTED
     */
    @Transactional
    public RideResponse requestRide(RideRequest request) {
        log.info("New ride request from rider: {}", request.getRiderId());

        //save ride to db
        Ride ride = Ride.builder()
                .riderId(request.getRiderId())
                .pickupLongitude(request.getPickupLongitude())
                .pickupLatitude(request.getPickupLatitude())
                .pickupAddress(request.getPickupAddress())
                .dropLatitude(request.getDropLatitude())
                .dropLongitude(request.getDropLongitude())
                .dropAddress(request.getDropAddress())
                .status(RideStatus.REQUESTED)
                .estimatedFare(calculateEstimateFare(request))
                .build();

        Ride savedRide = rideRepository.save(ride);

        //publish event to kafka, matching service consumes and finds nearest driver

        RideRequestedEvent event = new RideRequestedEvent(
                savedRide.getId(),
                savedRide.getRiderId(),
                savedRide.getPickupLatitude(),
                savedRide.getPickupLongitude(),
                savedRide.getPickupAddress(),
                savedRide.getDropLatitude(),
                savedRide.getDropLongitude(),
                savedRide.getDropAddress()
        );

        kafkaTemplate.send(RIDE_REQUESTED_TOPIC,savedRide.getId(),event);
        log.info("RideRequestedEvent published to Kafka for ride: {}",savedRide.getId());

        //update status to matching
        savedRide.setStatus(RideStatus.MATCHING);
        rideRepository.save(savedRide);

        return mapToResponse(savedRide);
    }

    //called by matching service to assign driver and set status=ACCEPTED
    public void updateRideWithDriver(String rideId, String driverId){
        Ride ride = rideRepository.findById(rideId).orElseThrow(()-> new RuntimeException("Ride Not Found"));

        ride.setDriverId(driverId);
        ride.setStatus(RideStatus.ACCEPTED);
        rideRepository.save(ride);
    }

    public RideResponse startRide(String rideId){
        Ride ride = rideRepository.findById(rideId).orElseThrow(()-> new RuntimeException("Ride Not Found"));
        if (ride.getStatus() != RideStatus.ACCEPTED){
            throw new RuntimeException("Ride cannot be started. Current status: " + ride.getStatus());
        }
        ride.setStatus(RideStatus.RIDE_STARTED);
        ride.setStartedAt(LocalDateTime.now());
        rideRepository.save(ride);
        return mapToResponse(ride);
    }

    public RideResponse completeRide(String rideId){
        Ride ride = rideRepository.findById(rideId).orElseThrow(()-> new RuntimeException("Ride Not Found"));
        if (ride.getStatus() != RideStatus.RIDE_STARTED){
            throw new RuntimeException("Ride cannot be completed. Current status: " + ride.getStatus());
        }
        ride.setStatus(RideStatus.COMPLETED);
        ride.setCompletedAt(LocalDateTime.now());
        ride.setActualFare(ride.getEstimatedFare());
        rideRepository.save(ride);
        return mapToResponse(ride);
    }

    public RideResponse cancelRide(String rideId){
        Ride ride = rideRepository.findById(rideId).orElseThrow(()-> new RuntimeException("Ride Not Found"));

        ride.setStatus(RideStatus.CANCELLED);
        rideRepository.save(ride);
        return mapToResponse(ride);
    }

    public RideResponse getRideById(String rideId) {
        Ride ride = rideRepository.findById(rideId).orElseThrow(()-> new RuntimeException("Ride Not Found"));
        return mapToResponse(ride);
    }

    public List<RideResponse> getRidesByRider(String riderId) {
        return rideRepository.findByRiderIdOrderByCreatedAtDesc(riderId).stream().map(this::mapToResponse).toList();
    }
}
