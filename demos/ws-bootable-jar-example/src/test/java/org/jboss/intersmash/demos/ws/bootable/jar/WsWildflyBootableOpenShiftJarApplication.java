package org.jboss.intersmash.demos.ws.bootable.jar;

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

public class WsWildflyBootableOpenShiftJarApplication implements BootableJarOpenShiftApplication {
	// private String GROUPID = IntersmashSharedDeploymentsProperties.groupID();
	private String GROUPID = "org.jboss.ws.cloud";
	private String ARTIFACTID = "hello";
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
		return new LocalBinarySource(file);
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
	class LocalBinarySource implements BinarySource {
		Path f;

		public LocalBinarySource(Path f) {
			this.f = f;
		}

		public Path getArchive() {
			return f;
		}
	}

}
