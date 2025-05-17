package com.BP.setlistaggregator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;

@SpringBootApplication
public class SetlistaggregatorApplication {


	public static void main(String[] args) {

		SpringApplication.run(SetlistaggregatorApplication.class, args);
	}

	//inject webclient
	@Bean
	public WebClient.Builder webClientBuilder() {
		return WebClient.builder();

	}

	@PostConstruct
	public void logDatasourceEnvVars() {
		System.out.println("🔍 SPRING_DATASOURCE_URL: " + System.getenv("SPRING_DATASOURCE_URL"));
		System.out.println("🔍 SPRING_DATASOURCE_USERNAME: " + System.getenv("SPRING_DATASOURCE_USERNAME"));
		System.out.println("🔍 SPRING_DATASOURCE_PASSWORD: " + System.getenv("SPRING_DATASOURCE_PASSWORD"));
	}
}
