package com.BP.setlistaggregator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

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
}
