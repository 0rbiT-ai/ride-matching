package com.ridematching.locationservice.controller;

import com.ridematching.locationservice.dto.DriverLocationRequest;
import com.ridematching.locationservice.dto.NearByDriverResponse;
import com.ridematching.locationservice.service.LocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
@Slf4j
public class LocationController {

    private final LocationService locationService;

    //update driver location every 3 seconds sent by driver's app
    @PostMapping("/drivers/update")
    public ResponseEntity<String> updateDriverLocation(@RequestBody DriverLocationRequest request){
        locationService.updateDriverLocation(request);
        return ResponseEntity.ok("Driver Location Updated");
    }

    //get nearby driver called by matching service upon ride request
    @GetMapping("/drivers/nearby")
    public ResponseEntity<List<NearByDriverResponse>> getNearByDrivers(@RequestParam double latitude,
                                                                       @RequestParam double longitude,
                                                                       @RequestParam(defaultValue = "5.0") double radius){
        return ResponseEntity.ok(locationService.findNearByDrivers(latitude,longitude,radius));
    }

    //remove driver when driver's app is offline
    @DeleteMapping("/drivers/{driverId}")
    public ResponseEntity<String> removeDriver(@PathVariable String driverId){
        locationService.removeDriver(driverId);
        return ResponseEntity.ok("Driver Removed Successfully");
    }

}
