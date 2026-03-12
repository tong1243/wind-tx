package com.wut.screenapptx.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {
    @Bean("mysql")
    @Primary
    @ConfigurationProperties(prefix="spring.datasource.mysql-server")
    public DataSource mysqlDataSource() {
        return DataSourceBuilder.create().build();
    }

}
