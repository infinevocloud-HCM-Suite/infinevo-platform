package com.phegondev.usersmanagementsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UsersmanagementsystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(UsersmanagementsystemApplication.class, args);
	}

}
