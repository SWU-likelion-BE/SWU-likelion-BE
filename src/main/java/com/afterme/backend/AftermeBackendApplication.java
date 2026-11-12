package com.afterme.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AftermeBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(AftermeBackendApplication.class, args);
	}

}
