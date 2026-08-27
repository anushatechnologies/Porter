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

        String rawUrl = (configuredUrl != null) ? configuredUrl.trim() : "";
        String url;

        if (!StringUtils.hasText(rawUrl)) {
            url = "jdbc:h2:file:./data/anushadb;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";
            log.info("No RDS_URL provided. Defaulting to embedded H2 file database at {}", url);
        } else {
            url = rawUrl;
            // 1. Auto-prepend jdbc:mysql:// if user entered raw AWS RDS host without jdbc prefix
            if (!url.startsWith("jdbc:")) {
                if (url.contains("5432") || url.toLowerCase().contains("postgres")) {
                    url = "jdbc:postgresql://" + url;
                } else {
                    url = "jdbc:mysql://" + url;
                }
            }

            // 2. Ensure database name is present if only host was provided
            if (url.startsWith("jdbc:mysql://") && !url.substring("jdbc:mysql://".length()).contains("/")) {
                url = url + "/anushadb";
            }

            // 3. Add standard AWS RDS MySQL query parameters if missing
            if (url.startsWith("jdbc:mysql:") && !url.contains("?")) {
                url = url + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true";
            }

            log.info("Configuring primary DataSource with URL: {}", url);
        }

        dataSource.setJdbcUrl(url);

        // Driver class detection
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

        dataSource.setUsername(StringUtils.hasText(configuredUsername) ? configuredUsername.trim() : "sa");
        dataSource.setPassword(configuredPassword != null ? configuredPassword : "");
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(2);
        dataSource.setConnectionTimeout(20000);
        dataSource.setIdleTimeout(300000);
        dataSource.setMaxLifetime(600000);

        return dataSource;
    }
}
