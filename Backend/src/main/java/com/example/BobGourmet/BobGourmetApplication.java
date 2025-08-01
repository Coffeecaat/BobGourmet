package com.example.BobGourmet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BobGourmetApplication {

	public static void main(String[] args) {
		SpringApplication.run(BobGourmetApplication.class, args);
	}

}
