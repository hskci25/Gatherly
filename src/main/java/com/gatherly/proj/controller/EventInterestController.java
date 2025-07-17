package com.gatherly.proj.controller;

import com.gatherly.proj.model.EventInterest;
import com.gatherly.proj.repository.EventInterestRepository;
import com.gatherly.proj.model.Event;
import com.gatherly.proj.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/event-interest")
public class EventInterestController {
    @Autowired
    private EventInterestRepository eventInterestRepository;
    @Autowired
    private EventRepository eventRepository;

    @PostMapping
    public ResponseEntity<?> saveInterest(@RequestBody EventInterest interest) {
        interest.setTimestamp(LocalDateTime.now());
        EventInterest saved = eventInterestRepository.save(interest);

        // Optionally fetch event details for message
        String eventName = "the event";
        if (interest.getEventId() != null) {
            Optional<Event> eventOpt = eventRepository.findById(interest.getEventId());
            if (eventOpt.isPresent()) {
                eventName = eventOpt.get().getName();
            }
        }
        String message = String.format("Hi, I am interested in '%s'. My name is %s.", eventName, interest.getUserName());
        String whatsappUrl = "https://wa.me/?text=" + URLEncoder.encode(message, StandardCharsets.UTF_8);

        return ResponseEntity.ok(new WhatsAppResponse(saved, whatsappUrl));
    }

    static class WhatsAppResponse {
        public EventInterest interest;
        public String whatsappUrl;
        public WhatsAppResponse(EventInterest interest, String whatsappUrl) {
            this.interest = interest;
            this.whatsappUrl = whatsappUrl;
        }
        public EventInterest getInterest() { return interest; }
        public String getWhatsappUrl() { return whatsappUrl; }
    }
} 