package com.gatherly.proj.repository;

import com.gatherly.proj.model.Event;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EventRepository extends MongoRepository<Event, String> {
} 