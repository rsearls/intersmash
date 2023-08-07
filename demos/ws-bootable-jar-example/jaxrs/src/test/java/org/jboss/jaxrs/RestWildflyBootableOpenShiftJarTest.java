/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.jaxrs;

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
		@Service(RestWildflyBootableOpenShiftJarApplication.class)
})
public class RestWildflyBootableOpenShiftJarTest {
	@ServiceUrl(RestWildflyBootableOpenShiftJarApplication.class)
	private String appOpenShiftUrl;

	/**
	 * Demonstrate the endpoint is available use intersmash' methodology,
	 * using cz.xtf.core.http.Https.
	 */
	@Test
	public void testRest() {
		log.info("Verify that service is available.");
		String appUrl = appOpenShiftUrl + "/hello";
		Https.doesUrlReturnOK(appUrl).waitFor();
		String content = Https.getContent(appUrl);
		Assertions.assertThat(content).contains("Hello from WildFly bootable jar!");
	}

	/**
	 * Demonstrate the endpoint is available using RESTEasy's client.
	 */
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
