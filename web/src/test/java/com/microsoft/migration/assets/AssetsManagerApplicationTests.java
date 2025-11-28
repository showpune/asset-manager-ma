package com.microsoft.migration.assets;

import com.microsoft.migration.assets.config.TestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestConfig.class)
class AssetsManagerApplicationTests {

	@Test
	void contextLoads() {
	}

}
