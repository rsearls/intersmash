/**
 * Copyright (C) 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.jaxws;

import jakarta.xml.ws.BindingProvider;

import java.net.URL;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.ws.security.SecurityConstants;
import org.apache.cxf.ws.security.trust.STSClient;
import org.assertj.core.api.Assertions;
import org.jboss.intersmash.tools.annotations.Intersmash;
import org.jboss.intersmash.tools.annotations.Service;
import org.jboss.intersmash.tools.annotations.ServiceUrl;
import org.jboss.test.ws.jaxws.samples.wsse.policy.trust.service.ServiceIface;
import org.jboss.test.ws.jaxws.samples.wsse.policy.trust.shared.ClientCallbackHandler;
import org.jboss.wsf.test.CryptoCheckHelper;
import org.junit.jupiter.api.Test;

import cz.xtf.core.openshift.OpenShifts;
import io.fabric8.kubernetes.api.model.Pod;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Intersmash({
		@Service(ServiceWstrustOpenShiftJarApplication.class),
		@Service(STSWstrustOpenShiftJarApplication.class)
})
public class WstrustOpenShiftJarTest {

	@ServiceUrl(STSWstrustOpenShiftJarApplication.class)
	private String stsOpenShiftUrl;

	@ServiceUrl(ServiceWstrustOpenShiftJarApplication.class)
	private String serviceOpenShiftUrl;

	@Test
	public void test() throws Exception {

		Bus bus = BusFactory.newInstance().createBus();
		try {
			BusFactory.setThreadDefaultBus(bus);

			final QName serviceName = new QName("http://www.jboss.org/jbossws/ws-extensions/wssecuritypolicy",
					"SecurityService");
			final URL wsdlURL = new URL(serviceOpenShiftUrl + "/service-ROOT/SecurityService?wsdl");
			jakarta.xml.ws.Service service = jakarta.xml.ws.Service.create(wsdlURL, serviceName);
			ServiceIface proxy = (ServiceIface) service.getPort(ServiceIface.class);
			//BindingProvider bp = (BindingProvider) proxy;
			String there = "";

			final QName stsServiceName = new QName("http://docs.oasis-open.org/ws-sx/ws-trust/200512/", "SecurityTokenService");
			final QName stsPortName = new QName("http://docs.oasis-open.org/ws-sx/ws-trust/200512/", "UT_Port");
			URL stsURL = new URL(stsOpenShiftUrl + "/sts-ROOT/SecurityTokenService?wsdl");

			setupWsseAndSTSClient(proxy, bus, stsURL.toString(), stsServiceName, stsPortName);

			try {
				Assertions.assertThat(proxy.sayHello()).isEqualTo("WS-Trust Hello World!");
			} catch (Exception e) {
				throw CryptoCheckHelper.checkAndWrapException(e);
			}

		} catch (Exception ee) { // rls debug
			String here = ""; // rls debug
			throw ee;
		} finally {
			bus.shutdown(true);
		}
	}

	private void setupWsseAndSTSClient(ServiceIface proxy, Bus bus, String stsWsdlLocation, QName stsService, QName stsPort) {
		Map<String, Object> ctx = ((BindingProvider) proxy).getRequestContext();
		setServiceContextAttributes(ctx);
		ctx.put(SecurityConstants.STS_CLIENT, createSTSClient(bus, stsWsdlLocation, stsService, stsPort));
	}

	private void setServiceContextAttributes(Map<String, Object> ctx) {
		ctx.put(SecurityConstants.CALLBACK_HANDLER, new ClientCallbackHandler());
		ctx.put(SecurityConstants.SIGNATURE_PROPERTIES,
				Thread.currentThread().getContextClassLoader().getResource("META-INF/clientKeystore.properties"));
		ctx.put(SecurityConstants.ENCRYPT_PROPERTIES,
				Thread.currentThread().getContextClassLoader().getResource("META-INF/clientKeystore.properties"));
		ctx.put(SecurityConstants.SIGNATURE_USERNAME, "myclientkey");
		ctx.put(SecurityConstants.ENCRYPT_USERNAME, "myservicekey");
	}

	private static STSClient createSTSClient(Bus bus, String stsWsdlLocation, QName stsService, QName stsPort) {
		STSClient stsClient = new STSClient(bus);
		if (stsWsdlLocation != null) {
			stsClient.setWsdlLocation(stsWsdlLocation);
			stsClient.setServiceQName(stsService);
			stsClient.setEndpointQName(stsPort);
		}
		Map<String, Object> props = stsClient.getProperties();
		props.put(SecurityConstants.USERNAME, "alice");
		props.put(SecurityConstants.CALLBACK_HANDLER, new ClientCallbackHandler());
		props.put(SecurityConstants.ENCRYPT_PROPERTIES,
				Thread.currentThread().getContextClassLoader().getResource("META-INF/clientKeystore.properties"));
		props.put(SecurityConstants.ENCRYPT_USERNAME, "mystskey");
		props.put(SecurityConstants.STS_TOKEN_USERNAME, "myclientkey");
		props.put(SecurityConstants.STS_TOKEN_PROPERTIES,
				Thread.currentThread().getContextClassLoader().getResource("META-INF/clientKeystore.properties"));
		props.put(SecurityConstants.STS_TOKEN_USE_CERT_FOR_KEYINFO, "true");
		return stsClient;
	}
	/********
		@Test
		public void aVerifyBasicConfig() throws Exception {
			// check that something was successfully deployed
			List<Pod> podList = cz.xtf.core.openshift.OpenShifts.master().getPods();
			String here = "";
		}

		public void aVerifySTS() throws Exception {
			// todo add test
		}

		@Test
		public void bVerifyService() throws Exception {
			// todo add test
		}

		@Test
		public void cCheckServiceWithSTS() throws Exception {
			// todo add test
		}
	***********/
}
