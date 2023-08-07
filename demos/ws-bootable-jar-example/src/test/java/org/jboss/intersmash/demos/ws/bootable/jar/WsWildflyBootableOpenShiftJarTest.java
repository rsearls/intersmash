package org.jboss.intersmash.demos.ws.bootable.jar;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.core.Response;

import org.assertj.core.api.Assertions;
import org.jboss.intersmash.tools.annotations.Intersmash;
import org.jboss.intersmash.tools.annotations.Service;
import org.jboss.intersmash.tools.annotations.ServiceUrl;
import org.junit.jupiter.api.Test;

import cz.xtf.core.http.Https;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Intersmash({
		@Service(WsWildflyBootableOpenShiftJarApplication.class)
})
public class WsWildflyBootableOpenShiftJarTest {
	@ServiceUrl(WsWildflyBootableOpenShiftJarApplication.class)
	private String appOpenShiftUrl;

	@Test
	public void testSoap() {
		log.info("Verify that service is available.");
		String appUrl = appOpenShiftUrl + "/HelloWorld";
		Https.doesUrlReturnOK(appUrl).waitFor();
		String content = Https.getContent(appUrl);
		Assertions.assertThat(content).contains("Hello World!");
	}

	@Test
	public void testRest() {
		log.info("Verify that service is available.");
		String appUrl = appOpenShiftUrl + "/hello";
		Https.doesUrlReturnOK(appUrl).waitFor();
		String content = Https.getContent(appUrl);
		Assertions.assertThat(content).contains("Hello from WildFly bootable jar!");
	}

	@Test
	public void testRestClient() {
		log.info("Verify that service is available.");
		try (Client client = ClientBuilder.newClient()) {
			final Response response = client.target(appOpenShiftUrl + "/hello")
					.request()
					.get();
			Assertions.assertThat(response.getStatusInfo()).isEqualTo(Response.Status.OK);
			String content = response.readEntity(String.class);
			Assertions.assertThat(content).contains("Hello from WildFly bootable jar!");
		}
	}
}
