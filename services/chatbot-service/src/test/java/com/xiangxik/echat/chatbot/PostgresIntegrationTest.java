package com.xiangxik.echat.chatbot;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.flywaydb.core.Flyway;

public abstract class PostgresIntegrationTest {

	@Autowired
	private Flyway flyway;

	@BeforeEach
	void resetPostgresTestData() throws Exception {
		flyway.clean();
		flyway.migrate();
	}
}