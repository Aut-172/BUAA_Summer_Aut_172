package com.example.demo;

import com.example.demo.common.feign.FeignGovernanceConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@MapperScan("com.example.demo.**.mapper")
@EnableDiscoveryClient
@EnableFeignClients(defaultConfiguration = FeignGovernanceConfig.class)
@SpringBootApplication
public class MerchantServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MerchantServiceApplication.class, args);
    }
}
