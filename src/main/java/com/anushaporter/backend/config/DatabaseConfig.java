package com.anushaporter.backend.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Value("${spring.datasource.url:}")
    private String configuredUrl;

    @Value("${spring.datasource.driver-class-name:}")
    private String configuredDriver;

    @Value("${spring.datasource.username:sa}")
    private String configuredUsername;

    @Value("${spring.datasource.password:password}")
    private String configuredPassword;

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariDataSource dataSource = new HikariDataSource();

        String url = configuredUrl;
        if (!StringUtils.hasText(url) || url.trim().isEmpty()) {
            url = "jdbc:h2:file:./data/anushadb;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";
            log.info("No valid RDS_URL provided. Defaulting to H2 database at {}", url);
        } else {
            url = url.trim();
            log.info("Configuring primary DataSource with URL: {}", url);
        }

        dataSource.setJdbcUrl(url);

        String driver = configuredDriver;
        if (StringUtils.hasText(driver) && !driver.trim().isEmpty()) {
            dataSource.setDriverClassName(driver.trim());
        } else {
            if (url.startsWith("jdbc:mysql:")) {
                dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
            } else if (url.startsWith("jdbc:postgresql:")) {
                dataSource.setDriverClassName("org.postgresql.Driver");
            } else {
                dataSource.setDriverClassName("org.h2.Driver");
            }
        }

        dataSource.setUsername(configuredUsername != null ? configuredUsername : "sa");
        dataSource.setPassword(configuredPassword != null ? configuredPassword : "password");
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(2);

        return dataSource;
    }
}
