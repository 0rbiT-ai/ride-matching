package com.ridematching.rideservice.repository;

import com.ridematching.rideservice.entity.Ride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RideRepository extends JpaRepository<Ride,String> {
    List<Ride> findByRiderIdOrderByCreatedAtDesc(String riderId);
}
