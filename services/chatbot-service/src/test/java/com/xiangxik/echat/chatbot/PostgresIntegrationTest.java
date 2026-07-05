package com.xiangxik.echat.chatbot;

import com.xiangxik.echat.chatbot.config.DefaultProviderDataInitializer;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class PostgresIntegrationTest {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private DefaultProviderDataInitializer defaultProviderDataInitializer;

	@BeforeEach
	void resetPostgresTestData() throws Exception {
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
			connection.setAutoCommit(true);
			statement.execute("""
					TRUNCATE TABLE
						messages,
						conversations,
						memory_items,
						chatbot_configs,
						context_policies,
						model_configs,
						provider_configs
					RESTART IDENTITY CASCADE
					""");
		}
		defaultProviderDataInitializer.run(null);
	}
}