package com.anushaporter.backend;

import com.anushaporter.backend.config.DatabaseConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = BackendApplication.class)
public class DatabaseConfigTest {

    @Test
    void testDataSourceConfiguredProperly() {
        DatabaseConfig config = new DatabaseConfig();
        DataSource ds = config.dataSource();
        assertNotNull(ds, "DataSource should be successfully created and not null");
    }
}
