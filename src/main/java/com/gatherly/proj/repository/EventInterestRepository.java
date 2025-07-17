package com.gatherly.proj.repository;

import com.gatherly.proj.model.EventInterest;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EventInterestRepository extends MongoRepository<EventInterest, String> {
} 