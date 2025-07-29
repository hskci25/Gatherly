package com.gatherly.proj;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class ProjApplication {

    @Value("${custom.app.name}")
    private String appName;

    @Value("${custom.app.version}")
    private String appVersion;

    @Value("${custom.app.owner}")
    private String appOwner;


	public static void main(String[] args) {
		SpringApplication.run(ProjApplication.class, args);
	}

}
