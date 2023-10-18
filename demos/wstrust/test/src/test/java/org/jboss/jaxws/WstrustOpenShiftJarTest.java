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

import java.util.List;

import org.jboss.intersmash.tools.annotations.Intersmash;
import org.jboss.intersmash.tools.annotations.Service;
import org.jboss.intersmash.tools.annotations.ServiceUrl;
import org.junit.jupiter.api.Test;

import cz.xtf.core.openshift.OpenShifts;
import io.fabric8.kubernetes.api.model.Pod;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Intersmash({
		@Service(ServiceWstrustOpenShiftJarApplication.class)
})
/* rls      todo disable to simplify debugging
@Intersmash({
		@Service(STSWstrustOpenShiftJarApplication.class),
		@Service(ServiceWstrustOpenShiftJarApplication.class)
})
*/
public class WstrustOpenShiftJarTest {
	/* todo disable to simplify debugging
	@ServiceUrl(STSWstrustOpenShiftJarApplication.class)
	private String stsOpenShiftUrl;
	*/
	@ServiceUrl(ServiceWstrustOpenShiftJarApplication.class)
	private String serviceOpenShiftUrl;

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

}
