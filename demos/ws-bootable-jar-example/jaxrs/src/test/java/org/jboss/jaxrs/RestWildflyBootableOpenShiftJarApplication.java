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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.maven.settings.building.SettingsBuildingException;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.jboss.intersmash.deployments.util.maven.ArtifactProvider;
import org.jboss.intersmash.tools.application.openshift.BootableJarOpenShiftApplication;
import org.jboss.intersmash.tools.application.openshift.input.BinarySource;

import cz.xtf.builder.builders.SecretBuilder;
import cz.xtf.builder.builders.secret.SecretType;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.Secret;

public class RestWildflyBootableOpenShiftJarApplication implements BootableJarOpenShiftApplication {
	// private String GROUPID = IntersmashSharedDeploymentsProperties.groupID();
	private String GROUPID = "org.jboss.intersmash";
	private String ARTIFACTID = "jaxrs";
	// private String VERSION = IntersmashSharedDeploymentsProperties.version();
	private String VERSION = "0.0.1-SNAPSHOT";
	static final String BOOTABLE_JAR_ARTIFACT_PACKAGING = "jar";
	static final String ARTIFACT_CLASSIFIER = "bootable-openshift";

	static final EnvVar TEST_ENV_VAR = new EnvVarBuilder().withName("test-evn-key").withValue("test-evn-value").build();
	static final String TEST_SECRET_FOO = "foo";
	static final String TEST_SECRET_BAR = "bar";
	static final Secret TEST_SECRET = new SecretBuilder("test-secret")
			.setType(SecretType.OPAQUE).addData(TEST_SECRET_FOO, TEST_SECRET_BAR.getBytes()).build();

	@Override
	public BinarySource getBuildInput() {
		Path file = null;
		try {
			file = ArtifactProvider.resolveArtifact(
					GROUPID,
					ARTIFACTID,
					VERSION,
					BOOTABLE_JAR_ARTIFACT_PACKAGING,
					ARTIFACT_CLASSIFIER).toPath();
		} catch (SettingsBuildingException | ArtifactResolutionException e) {
			throw new RuntimeException("Can not get artifact", e);
		}
		return new BinarySourceImpl(file);
	}

	@Override
	public List<Secret> getSecrets() {
		List<Secret> secrets = new ArrayList<>();
		secrets.add(TEST_SECRET);
		return Collections.unmodifiableList(secrets);
	}

	@Override
	public List<EnvVar> getEnvVars() {
		List<EnvVar> list = new ArrayList<>();
		list.add(new EnvVarBuilder().withName(TEST_ENV_VAR.getName())
				.withValue(TEST_ENV_VAR.getValue()).build());
		return Collections.unmodifiableList(list);
	}

	@Override
	public String getName() {
		return "ws-bootable-openshift-jar";
	}

	//-----------------------------
	class BinarySourceImpl implements BinarySource {
		Path f;

		public BinarySourceImpl(Path f) {
			this.f = f;
		}

		public Path getArchive() {
			return f;
		}
	}

}
