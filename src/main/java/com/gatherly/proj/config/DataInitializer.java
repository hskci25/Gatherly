package com.gatherly.proj.config;

import com.gatherly.proj.model.Event;
import com.gatherly.proj.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private EventRepository eventRepository;

    @Override
    public void run(String... args) throws Exception {
        // Only initialize if database is empty
        if (eventRepository.count() == 0) {
            List<Event> sampleEvents = Arrays.asList(
                createEvent(
                    "Italian Night Supper Club",
                    "Experience authentic Italian cuisine with handmade pasta, fresh ingredients, and carefully selected wine pairings in an intimate dining setting.",
                    "Mumbai",
                    LocalDateTime.now().plusDays(7).withHour(19).withMinute(0),
                    LocalDateTime.now().plusDays(7).withHour(22).withMinute(0)
                ),
                createEvent(
                    "Farm-to-Table Experience",
                    "Join us for a unique dining experience featuring locally sourced, organic ingredients prepared by renowned chefs in a cozy, rustic atmosphere.",
                    "Mumbai",
                    LocalDateTime.now().plusDays(10).withHour(18).withMinute(30),
                    LocalDateTime.now().plusDays(10).withHour(21).withMinute(30)
                ),
                createEvent(
                    "Asian Fusion Dinner",
                    "Modern Asian cuisine with creative presentations, featuring fusion flavors and premium sake pairings in an elegant setting.",
                    "Mumbai",
                    LocalDateTime.now().plusDays(14).withHour(20).withMinute(0),
                    LocalDateTime.now().plusDays(14).withHour(23).withMinute(0)
                ),
                createEvent(
                    "Mediterranean Mezze Evening",
                    "Discover the flavors of the Mediterranean with an array of mezze dishes, fresh seafood, and regional wines in a warm, welcoming atmosphere.",
                    "Delhi",
                    LocalDateTime.now().plusDays(12).withHour(19).withMinute(30),
                    LocalDateTime.now().plusDays(12).withHour(22).withMinute(30)
                ),
                createEvent(
                    "French Bistro Night",
                    "Classic French bistro fare with a modern twist, featuring seasonal ingredients and expertly paired wines in an intimate setting.",
                    "Delhi",
                    LocalDateTime.now().plusDays(16).withHour(18).withMinute(0),
                    LocalDateTime.now().plusDays(16).withHour(21).withMinute(0)
                ),
                createEvent(
                    "Spice Route Journey",
                    "Embark on a culinary journey through India's spice routes with traditional recipes, modern interpretations, and aromatic spice blends.",
                    "Bangalore",
                    LocalDateTime.now().plusDays(9).withHour(19).withMinute(0),
                    LocalDateTime.now().plusDays(9).withHour(22).withMinute(0)
                ),
                createEvent(
                    "Chef's Table Experience",
                    "An exclusive chef's table experience with a multi-course tasting menu, wine pairings, and interaction with the chef throughout the evening.",
                    "Bangalore",
                    LocalDateTime.now().plusDays(18).withHour(20).withMinute(30),
                    LocalDateTime.now().plusDays(18).withHour(23).withMinute(30)
                ),
                createEvent(
                    "Vegan Garden Party",
                    "Celebrate plant-based cuisine with fresh, innovative vegan dishes, organic wines, and a beautiful garden setting for a memorable evening.",
                    "Pune",
                    LocalDateTime.now().plusDays(8).withHour(18).withMinute(30),
                    LocalDateTime.now().plusDays(8).withHour(21).withMinute(30)
                )
            );

            eventRepository.saveAll(sampleEvents);
            System.out.println("Sample events initialized successfully!");
        }
    }

    private Event createEvent(String name, String description, String location, LocalDateTime startDate, LocalDateTime endDate) {
        Event event = new Event();
        event.setName(name);
        event.setDescription(description);
        event.setLocation(location);
        event.setStartDate(startDate);
        event.setEndDate(endDate);
        return event;
    }
} 