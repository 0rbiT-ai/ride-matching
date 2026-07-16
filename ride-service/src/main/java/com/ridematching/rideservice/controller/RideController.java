package com.ridematching.rideservice.controller;

import com.ridematching.rideservice.dto.RideRequest;
import com.ridematching.rideservice.dto.RideResponse;
import com.ridematching.rideservice.service.RideService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rides")
@RequiredArgsConstructor
@Slf4j
public class RideController {

    private final RideService rideService;

    //Rider requests ride
    @PostMapping("/request")
    public ResponseEntity<RideResponse> requestRide(@Valid @RequestBody RideRequest request){
        log.info("Ride Request received from rider: {}",request.getRiderId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(rideService.requestRide(request));
    }

    @GetMapping("/{rideId}")
    public ResponseEntity<RideResponse> getRideById(@PathVariable String rideId){
        return ResponseEntity.ok(rideService.getRideById(rideId));
    }

    @GetMapping("/rider/{riderId}")
    public ResponseEntity<List<RideResponse>> getRidesByRider(@PathVariable String riderId){
        return ResponseEntity.ok(rideService.getRidesByRider(riderId));
    }

    // Driver Starts the ride
    @PutMapping("/{rideId}/start")
    public ResponseEntity<RideResponse> startRide(@PathVariable String rideId){
        return ResponseEntity.ok(rideService.startRide(rideId));
    }

    // Driver Completes Ride
    @PutMapping("/{rideId}/complete")
    public ResponseEntity<RideResponse> completeRide(@PathVariable String rideId){
        return ResponseEntity.ok(rideService.completeRide(rideId));
    }

    // Cancels Ride
    @PutMapping("/{rideId}/cancel")
    public ResponseEntity<RideResponse> cancelRide(@PathVariable String rideId){
        return ResponseEntity.ok(rideService.cancelRide(rideId));
    }
}
