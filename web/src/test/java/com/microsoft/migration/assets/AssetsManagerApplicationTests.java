package com.microsoft.migration.assets;

import com.azure.spring.messaging.servicebus.core.ServiceBusTemplate;
import com.azure.storage.blob.BlobServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class AssetsManagerApplicationTests {

	@MockBean
	private ServiceBusTemplate serviceBusTemplate;
	
	@MockBean
	private BlobServiceClient blobServiceClient;

	@Test
	void contextLoads() {
	}

}
