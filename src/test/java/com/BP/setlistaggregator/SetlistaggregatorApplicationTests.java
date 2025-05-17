package com.BP.setlistaggregator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;


@SpringBootTest
//forces Spring to use application-test.properties from src/test/resources
//so we know if the app works independent of deployment config
@ActiveProfiles("test")
class SetlistaggregatorApplicationTests {

	@Test
	void contextLoads() {
	}

}
