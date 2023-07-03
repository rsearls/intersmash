package org.jboss.intersmash.testsuite.demo.shrinkwrap;

import org.assertj.core.api.Assertions;
import org.jboss.intersmash.tools.annotations.Intersmash;
import org.jboss.intersmash.tools.annotations.Service;
import org.jboss.intersmash.tools.annotations.ServiceProvisioner;
import org.jboss.intersmash.tools.annotations.ServiceUrl;
import org.jboss.intersmash.tools.provision.openshift.OpenShiftProvisioner;
import org.junit.jupiter.api.Test;

import cz.xtf.core.http.Https;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Intersmash({
		@Service(EAP7ShrinkWrapImageOpenShiftApplication.class)
})
public class ShrinkWrapEAP7ArchiveTest {
	@ServiceUrl(EAP7ShrinkWrapImageOpenShiftApplication.class)
	private String appOpenShiftUrl;

	@ServiceProvisioner(EAP7ShrinkWrapImageOpenShiftApplication.class)
	private OpenShiftProvisioner appOpenShiftProvisioner;

	@Test
	public void testShrinkWrapServlet() {
		log.info("Verify that service is available.");
		String appUrl = appOpenShiftUrl + "/HelloWorld";
		Https.doesUrlReturnOK(appUrl).waitFor();
		String content = Https.getContent(appUrl);
		Assertions.assertThat(content).contains("Hello World!");
	}
}
