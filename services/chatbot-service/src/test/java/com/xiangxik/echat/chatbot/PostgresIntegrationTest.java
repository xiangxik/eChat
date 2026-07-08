package com.xiangxik.echat.chatbot;

import com.xiangxik.echat.chatbot.config.DefaultDataInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.flywaydb.core.Flyway;

public abstract class PostgresIntegrationTest {

	@Autowired
	private Flyway flyway;

	@Autowired
	private DefaultDataInitializer defaultDataInitializer;

	@BeforeEach
	void resetPostgresTestData() throws Exception {
		flyway.clean();
		flyway.migrate();
		defaultDataInitializer.initializeDefaults();
	}
}