package com.foodstore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
// Esta suite no levanta RabbitMQ: el listener no arranca y el puerto invalido garantiza que, pase lo que pase,
// nunca se conecte al RabbitMQ de desarrollo (declararia colas ni consumiria eventos reales).
@TestPropertySource(properties = {"spring.rabbitmq.listener.simple.auto-startup=false", "spring.rabbitmq.port=1"})
class AppProductosApplicationTests {

	@Test
	void contextLoads() {
	}

}
