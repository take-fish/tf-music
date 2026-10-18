package com.fishmatrix.music;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"spring.cloud.nacos.discovery.enabled=false"})
class MusicApplicationTests {

    @Test
    void contextLoads() {
    }
}
