# Intersmash Operator provisioner How-to

This guide shows how to update Intersmash provisioners in order to add support for a new operator.

Intersmash operator based provisioning tooling leverages Java Operator SDK (JOSDK) which is generated by the Fabric8 
Kubernetes Client Java Generator Maven plugin, starting from a given operator CRDs. 
This means that generated sources are not under version control and a build is needed in order to use or to inspect them.

This guide will demonstrate the initial approach and will use the Keycloak operator as an 
example of a operator based provisioner.

## Implementation

**Checklist:**
 - [ ] Prepare resources
 - [ ] Generate the Java Operator SDK (JOSDK)
 - [ ] Create Application & Provisioner
 - [ ] Create builders
 - [ ] Create clients
 - [ ] Create model tests (verify client methods)
 - [ ] Implement methods from OpenShiftProvisioner interface
 - [ ] Register a new provisioner

### Prepare resources
* get operator sources - for OpenShift, see `Repository` section in Web console OperatorHub operator entry or 
similar information for community operators at Operatorhub.io
* get documentation (could contain some leads and feed for javadoc)
* get operator ID from operator catalog - e.g.: on OpenShift do `oc get packagemanifest -n openshift-marketplace`
* get provided Custom Resource Definitions (CRDs) - look into the operator sources or documentation. 

Put all of this collected info into the Intersmash issue which is tracking the operator addition works.

#### Example Outcome
* sources: https://github.com/keycloak/keycloak/tree/main/operator
* CRDs: https://github.com/keycloak/keycloak-k8s-resources/tree/22.0.4/kubernetes
* documentation: https://www.keycloak.org/guides#operator
* operator marketplace id = `keycloak-operator`
* provided CRs: [`keycloaks.k8s.keycloak.org`, `keycloakrealmimports.k8s.keycloak.org`]

**NOTE**: make sure to look into a correct branch for community project or product operator sources, e.g. the default branch containing the 
community project operator sources and CRDs could be a year away from the one supporting the related product! 

### Generate the Java Operator SDK (JOSDK)

* Copy the relevant operator CRDs in provisioners/src/main/resources/crds
* Build Intersmash
* Check provisioners/target/generated-sources and look for the expected JOSDK sources

### Create Application & Provisioner
* create org.jboss.intersmash.application.openshift.${PRODUCT}OperatorApplication, extending OperatorApplication
* create org.jboss.intersmash.provision.openshift.${PRODUCT}OperatorProvisioner, extending OperatorProvisioner<${PRODUCT}OperatorApplication>
	* get operator ID from openshift-marketplace (oc get packagemanifest -n openshift-marketplace) and create a constructor matching the one from abstract OperatorProvisioner class
    * use `throw new UnsupportedOperationException("TODO");` to implement the mandatory methods inherited from Provisioner interfaces for now
* add a new test to OperatorSubscriptionTestCase

### Create builders
It's useful to have two kinds of builders for `*Spec` model classes. 
The one for the ${CR_KIND}Spec class, which will act as an entry point for users to build the ${CR_KIND} objects, and the rest for the remaining model classes in `spec` package. Prefer using some plugin (e.g. https://plugins.jetbrains.com/plugin/6585-builder-generator) rather than creating builders manually.

###### Builder best practices

* It's useful to create methods also for a single addition of collection type resources. **Note**: _this example is based on outdated code but serves the goal._
```java
   	private List<String> args;	

    /**
	 *  Arguments to the entrypoint. Translates into Container CMD.
	 */
	public ExperimentalSpecBuilder args(List<String> args) {
		this.args = args;
		return this;
	}

	/**
	 *  Add argument to the entrypoint. Translates into Container CMD.
	 */
	public ExperimentalSpecBuilder args(String arg) {
		if (args == null) {
			args = new ArrayList<>();
		}
		args.add(arg);
		return this;
	}
```

#### Builder for ${CR_KIND}Spec
Create a builder for ${CR_KIND}SpecBuilder to the same package as ${CR_KIND}.
* class name: omit the `Spec` keyword - name it `${CR_KIND}Builder`
* method prefix: do not use any method prefix
* destination package: same as the ${CR_KIND} class (not `spec` package)
* add `private String name;` and `private Map<String, String> labels;` fields for resource metadata
* remove the `private` constructor
    * create constructors for `(String name)` & `(String name, Map<String, String> labels)` parameters
* remove static initializer
 * update the `build()` method
    * return `${CR_KIND}` instead of ${CR_KIND}Spec
    * init ${CR_KIND} metadata
    * set `name` field to metadata
    * set `labels` field to metadata
    * set ${CR_KIND}Spec as ${CR_KIND} spec field

#### Example Output
${CR_KIND}SpecBuilder example. **Note**: _this example is based on outdated code but serves the goal._
```java
public final class KeycloakUserBuilder {
	private String name;
	private Map<String, String> labels;
	private LabelSelector realmSelector;
	private KeycloakAPIUser user;

	/**
	 * Initialize the {@link KeycloakUserBuilder} with given resource name.
	 *
	 * @param name resource object name
	 */
	public KeycloakUserBuilder(String name) {
		this.name = name;
	}

	/**
	 * Initialize the {@link KeycloakUserBuilder} with given resource name and labels.
	 *
	 * @param name resource object name
	 * @param labels key/value pairs that are attached to objects
	 */
	public KeycloakUserBuilder(String name, Map<String, String> labels) {
		this.name = name;
		this.labels = labels;
	}

	/**
	 * Selector for looking up KeycloakRealm Custom Resources.
	 */
	public KeycloakUserBuilder realmSelector(LabelSelector realmSelector) {
		this.realmSelector = realmSelector;
		return this;
	}

	/**
	 * Keycloak User REST object.
	 */
	public KeycloakUserBuilder user(KeycloakAPIUser user) {
		this.user = user;
		return this;
	}

	public KeycloakUser build() {
		KeycloakUser keycloakUser = new KeycloakUser();
		keycloakUser.setMetadata(new ObjectMeta());
		keycloakUser.getMetadata().setName(name);
		keycloakUser.getMetadata().setLabels(labels);

		KeycloakUserSpec keycloakUserSpec = new KeycloakUserSpec();
		keycloakUserSpec.setRealmSelector(realmSelector);
		keycloakUserSpec.setUser(user);
		keycloakUser.setSpec(keycloakUserSpec);
		return keycloakUser;
	}
}
```

### Create clients
https://github.com/fabric8io/kubernetes-client/blob/master/kubernetes-examples/src/main/java/io/fabric8/kubernetes/examples/CRDExample.java shows a way how we work with custom resources. Create a ${CR_KIND}List (see the `listKind` field in CRD) and ${CR_KIND}Doneable support classes for all provided CRs.
* class ${CR_KIND}List extends CustomResourceList<{$CR}>
* class ${CR_KIND}Doneable extends CustomResourceDoneable<${CR_KIND}>

Update the ${PRODUCT}OperatorProvisioner. Create CR na name constant and NonNamespaceOperation for every custom resource provided by an operator (look into existing operator Provisioners for more details). Create method to initialize the client and to obtain a reference to custom resource instance running on OPC (this one will have to be parametrized in case there could be more than one resource of same kind managed by a single operator - e.g. ActiveMQ operator can have multiple addresses).

#### Example Output
`KeycloakOperatorProvisioner` client methods example. The methods and resources names are aligned with CR name and kind.
```java
    public class KeycloakOperatorProvisioner extends OperatorProvisioner<KeycloakOperatorApplication> {
    private static final String KEYCLOAK_RESOURCE = "keycloaks.k8s.keycloak.org";
    private static final String KEYCLOAK_REALM_IMPORT_RESOURCE = "keycloakrealmimports.k8s.keycloak.org";
    private static NonNamespaceOperation<Keycloak, KeycloakOperatorKeycloakList, Resource<Keycloak>> KEYCLOAK_CUSTOM_RESOURCE_CLIENT;
    private static NonNamespaceOperation<KeycloakRealmImport, KeycloakOperatorRealmImportList, Resource<KeycloakRealmImport>> KEYCLOAK_REALM_IMPORT_CUSTOM_RESOURCE_CLIENT;

    public NonNamespaceOperation<Keycloak, KeycloakOperatorKeycloakList, Resource<Keycloak>> keycloakClient() {
        if (KEYCLOAK_CUSTOM_RESOURCE_CLIENT == null) {
            CustomResourceDefinition crd = OpenShifts.admin().apiextensions().v1().customResourceDefinitions()
                    .withName(KEYCLOAK_RESOURCE).get();
            CustomResourceDefinitionContext crdc = CustomResourceDefinitionContext.fromCrd(crd);
            if (!getCustomResourceDefinitions().contains(KEYCLOAK_RESOURCE)) {
                throw new RuntimeException(String.format("[%s] custom resource is not provided by [%s] operator.",
                        KEYCLOAK_RESOURCE, OPERATOR_ID));
            }
            MixedOperation<Keycloak, KeycloakOperatorKeycloakList, Resource<Keycloak>> crClient = OpenShifts
                    .master().newHasMetadataOperation(crdc, Keycloak.class, KeycloakRealmImportOperatorKeycloakList.class);
            KEYCLOAK_CUSTOM_RESOURCE_CLIENT = crClient.inNamespace(OpenShiftConfig.namespace());
        }
        return KEYCLOAK_CUSTOM_RESOURCE_CLIENT;
    }

    public NonNamespaceOperation<KeycloakRealmImport, KeycloakRealmImportOperatorRealmImportList, Resource<KeycloakRealmImport>> keycloakRealmImportClient() {
        if (KEYCLOAK_REALM_IMPORT_CUSTOM_RESOURCE_CLIENT == null) {
            CustomResourceDefinition crd = OpenShifts.admin().apiextensions().v1().customResourceDefinitions()
                    .withName(KEYCLOAK_REALM_IMPORT_RESOURCE).get();
            CustomResourceDefinitionContext crdc = CustomResourceDefinitionContext.fromCrd(crd);
            if (!getCustomResourceDefinitions().contains(KEYCLOAK_REALM_IMPORT_RESOURCE)) {
                throw new RuntimeException(String.format("[%s] custom resource is not provided by [%s] operator.",
                        KEYCLOAK_REALM_IMPORT_RESOURCE, OPERATOR_ID));
            }
            MixedOperation<KeycloakRealmImport, KeycloakRealmImportOperatorRealmImportList, Resource<KeycloakRealmImport>> crClient = OpenShifts
                    .master()
                    .newHasMetadataOperation(crdc, KeycloakRealmImport.class, KeycloakRealmImportOperatorRealmImportList.class);
            KEYCLOAK_REALM_IMPORT_CUSTOM_RESOURCE_CLIENT = crClient.inNamespace(OpenShiftConfig.namespace());
        }
        return KEYCLOAK_REALM_IMPORT_CUSTOM_RESOURCE_CLIENT;
    }

    // ...

    /**
     * Get a reference to keycloak object. Use get() to get the actual object, or null in case it does not
     * exist on tested cluster.
     * @return A concrete {@link Resource} instance representing the {@link org.jboss.intersmash.provision.openshift.operator.keycloak.keycloak.Keycloak} resource definition
     */
    public Resource<Keycloak> keycloak() {
        return keycloakClient()
                .withName(getApplication().getKeycloak().getMetadata().getName());
    }

    public List<KeycloakRealmImport> keycloakRealmImports() {
        return keycloakRealmImportClient().list().getItems()
                .stream().filter(
                        realm -> getApplication().getKeycloakRealmImports().stream().map(
                                        ri -> ri.getMetadata().getName())
                                .anyMatch(riName -> riName.equalsIgnoreCase(realm.getMetadata().getName())))
                .collect(Collectors.toList());
    }

    // ...
}
```

### Create model tests (verify client methods)
Create a new ${PRODUCT}OperatorProvisionerTest to verify the model and client methods. The goal of these tests is to 
verify that Intersmash is able to work with custom resources provided by operator, not to verify the actual operator 
functionality.

#### Example Outcome
As said before, the goal of these tests is to verify the basic functionality of client methods, not to verify the 
actual operator provisioning workflow.

```java
	@Test
	public void exampleSso() {
		name = "example-sso";

		final Keycloak keycloak = new Keycloak();
		keycloak.getMetadata().setName(name);
		keycloak.getMetadata().setLabels(matchLabels);
		KeycloakSpec spec = new KeycloakSpec();
		spec.setInstances(1L);
		Ingress ingress = new Ingress();
		ingress.setEnabled(true);
		spec.setIngress(ingress);
		Hostname hostname = new Hostname();
		hostname.setHostname(OpenShifts.master().generateHostname(name));
		// create key, certificate and tls secret: Keycloak expects the secret to be created beforehand
		String tlsSecretName = name + "-tls-secret";
		CertificatesUtils.CertificateAndKey certificateAndKey = CertificatesUtils
				.generateSelfSignedCertificateAndKey(hostname.getHostname().replaceFirst("[.].*$", ""), tlsSecretName);
		// add TLS config to keycloak using the secret we just created
		Http http = new Http();
		http.setTlsSecret(certificateAndKey.tlsSecret.getMetadata().getName());
		spec.setHttp(http);
		spec.setHostname(hostname);
		keycloak.setSpec(spec);

		KEYCLOAK_OPERATOR_PROVISIONER = initializeOperatorProvisioner(keycloak, name);
		KEYCLOAK_OPERATOR_PROVISIONER.configure();
		try {
			KEYCLOAK_OPERATOR_PROVISIONER.subscribe();
			try {
				verifyKeycloak(keycloak, true);
			} finally {
				KEYCLOAK_OPERATOR_PROVISIONER.unsubscribe();
			}
		} finally {
			KEYCLOAK_OPERATOR_PROVISIONER.dismiss();
		}
	}
```

### Implement methods from OpenShiftProvisioner interface
Implement the missing methods from `OpenShiftProvisioner` interface (e.g. deploy, undeploy, scale, etc.). The method's implementation will vary based on the nature of the operator and custom resources the operator is providing. See the existing operator implementations for some inspiration.

In case that operator provides some general route to the service it provides, override the `getURL()` to return URL to such a route.

Throw `UnsupportedOperationException` for cases where implementation is not possible (e.g. `scale()` the provisioner without pods).

### Register a new provisioner
Create a new `class ${PRODUCT}OperatorProvisionerFactory implements ProvisionerFactory<${PRODUCT}OperatorProvisioner>` for the provisioner which would give Intersmash information about what kind of application is our new provisioner able to serve. Register a new provisioner factory for SPI in `provision.org.jboss.intersmash.ProvisionerFactory` file located within `META-INF/services` directory of `intersmash-provisioners` module, so it can be collected by a `ProvisionerManager` on next run.
As a part of service factory registration, update also the `ProvisionerManagerTestCase` with a new @Test method.

See the existing ProvisionerFactory implementations for more details.

Once the provisioner is registered, please add a new entry to `Mapping of implemented provisioners` section of Intersmash README file.

With a new provisioner ready to serve, it would be great if you'd add a new provisioner demonstration into `intersmash-demos-tests` module!