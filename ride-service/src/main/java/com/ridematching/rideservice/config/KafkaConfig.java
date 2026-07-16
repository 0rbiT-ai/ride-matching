package com.ridematching.rideservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    //Topic where ride service publishes ride request
    // matching service subscribes to this topic
    @Bean
    public NewTopic rideRequestedTopic(){
        return TopicBuilder.name("ride.requested")
                .partitions(3)
                .replicas(1)
                .build();
    }

    //Topic where matching service publishes match results
    //Ride Service subscribes to this topic
    @Bean
    public NewTopic rideMatchedTopic(){
        return TopicBuilder.name("ride.matched")
                .partitions(3)
                .replicas(1)
                .build();
    }
}
