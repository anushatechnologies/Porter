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

    @Value("${spring.datasource.username:${RDS_USERNAME:sa}}")
    private String configuredUsername;

    @Value("${spring.datasource.password:${RDS_PASSWORD:}}")
    private String configuredPassword;

    @Bean
    @Primary
    public DataSource dataSource() {

        String rawUrl = configuredUrl != null
                ? configuredUrl.trim()
                : "";

        String url;

        /*
         * Database URL
         */
        if (!StringUtils.hasText(rawUrl)) {

            url = "jdbc:h2:file:./data/anushadb;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";

            log.warn(
                    "No MySQL database URL found. Using H2 fallback: {}",
                    url);

        } else {

            url = rawUrl;

            /*
             * Add jdbc prefix if required
             */
            if (!url.startsWith("jdbc:")) {

                if (url.contains("5432")
                        || url.toLowerCase().contains("postgres")) {

                    url = "jdbc:postgresql://" + url;

                } else {

                    url = "jdbc:mysql://" + url;
                }
            }

            /*
             * Add database name if missing
             */
            if (url.startsWith("jdbc:mysql://")) {

                String mysqlPart = url.substring("jdbc:mysql://".length());

                if (!mysqlPart.contains("/")) {
                    url = url + "/anushadb";
                }
            }

            /*
             * MySQL connection parameters
             */
            if (url.startsWith("jdbc:mysql:")
                    && !url.contains("?")) {

                url = url +
                        "?useSSL=true" +
                        "&allowPublicKeyRetrieval=true" +
                        "&serverTimezone=UTC";
            }

            log.info(
                    "Configuring primary DataSource with URL: {}",
                    url);
        }

        /*
         * Create Hikari DataSource
         */
        HikariDataSource dataSource = new HikariDataSource();

        dataSource.setJdbcUrl(url);

        /*
         * IMPORTANT:
         * Select driver based on JDBC URL.
         *
         * Never allow H2 driver to be used with MySQL URL.
         */
        if (url.startsWith("jdbc:mysql:")) {

            dataSource.setDriverClassName(
                    "com.mysql.cj.jdbc.Driver");

            log.info(
                    "Using MySQL JDBC driver: com.mysql.cj.jdbc.Driver");

        } else if (url.startsWith("jdbc:postgresql:")) {

            dataSource.setDriverClassName(
                    "org.postgresql.Driver");

            log.info(
                    "Using PostgreSQL JDBC driver");

        } else if (url.startsWith("jdbc:h2:")) {

            dataSource.setDriverClassName(
                    "org.h2.Driver");

            log.info(
                    "Using H2 JDBC driver");

        } else {

            throw new IllegalStateException(
                    "Unsupported database URL: " + url);
        }

        /*
         * Username / password
         */
        dataSource.setUsername(
                StringUtils.hasText(configuredUsername)
                        ? configuredUsername.trim()
                        : "sa");

        dataSource.setPassword(
                configuredPassword != null
                        ? configuredPassword
                        : "");

        /*
         * Hikari settings
         */
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(2);
        dataSource.setConnectionTimeout(20000);
        dataSource.setIdleTimeout(300000);
        dataSource.setMaxLifetime(600000);

        return dataSource;
    }
}