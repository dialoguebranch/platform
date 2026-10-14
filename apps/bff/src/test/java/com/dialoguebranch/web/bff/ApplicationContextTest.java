package com.dialoguebranch.web.bff;

import jakarta.servlet.MultipartConfigElement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.multipart.MultipartResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the Spring application context loads without errors.
 */
@SpringBootTest
class ApplicationContextTest {

	@Autowired
	ApplicationContext context;

	@Test
	void contextLoads() {
		// If the context fails to start, Spring throws before this body runs.
	}

	@Test
	void multipartHandlingIsOffSoUploadsAreProxiedUnchanged() {
		// With a multipart configuration, the servlet container would parse (and consume) an
		// upload's body as soon as anything reads a request parameter, and ApiProxyController
		// would forward it empty.
		assertThat(context.getBeansOfType(MultipartResolver.class)).isEmpty();
		assertThat(context.getBeansOfType(MultipartConfigElement.class)).isEmpty();
	}

}
