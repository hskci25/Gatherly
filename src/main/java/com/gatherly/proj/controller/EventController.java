package com.gatherly.proj.controller;

import com.gatherly.proj.model.Event;
import com.gatherly.proj.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/events")
public class EventController {
    @Autowired
    private EventRepository eventRepository;

    @PostMapping
    public ResponseEntity<Event> createEvent(@RequestBody Event event) {
        Event savedEvent = eventRepository.save(event);
        return ResponseEntity.ok(savedEvent);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Event> updateEvent(@PathVariable String id, @RequestBody Event event) {
        Optional<Event> existingEventOpt = eventRepository.findById(id);
        if (existingEventOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Event existingEvent = existingEventOpt.get();
        existingEvent.setName(event.getName());
        existingEvent.setDescription(event.getDescription());
        existingEvent.setLongDescription(event.getLongDescription());
        existingEvent.setLocation(event.getLocation());
        existingEvent.setVenue(event.getVenue());
        existingEvent.setAddress(event.getAddress());
        existingEvent.setStartDate(event.getStartDate());
        existingEvent.setEndDate(event.getEndDate());
        existingEvent.setPrice(event.getPrice());
        existingEvent.setMaxCapacity(event.getMaxCapacity());
        existingEvent.setCurrentBookings(event.getCurrentBookings());
        existingEvent.setChefName(event.getChefName());
        existingEvent.setChefBio(event.getChefBio());
        existingEvent.setMenuItems(event.getMenuItems());
        existingEvent.setImageUrls(event.getImageUrls());
        existingEvent.setCuisineType(event.getCuisineType());
        existingEvent.setEventType(event.getEventType());
        existingEvent.setStatus(event.getStatus());
        Event updatedEvent = eventRepository.save(existingEvent);
        return ResponseEntity.ok(updatedEvent);
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllEvents() {
        return ResponseEntity.ok(eventRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getEventById(@PathVariable String id) {
        return eventRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
} 