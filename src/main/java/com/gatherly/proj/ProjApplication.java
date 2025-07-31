package com.gatherly.proj;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ProjApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProjApplication.class, args);
	}

}
