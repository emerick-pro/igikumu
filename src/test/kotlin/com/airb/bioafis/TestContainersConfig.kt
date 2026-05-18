package com.airb.bioafis

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestContainersConfig {
    @Bean
    @ServiceConnection
    fun mysqlContainer(): MySQLContainer<*> {
        return MySQLContainer(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("bioafis_test")
            .withUsername("bioafis_test")
            .withPassword("bioafis_test")
    }
}
