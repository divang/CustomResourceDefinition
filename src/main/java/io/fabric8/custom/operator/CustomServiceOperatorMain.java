package io.fabric8.custom.operator;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.fabric8.custom.operator.authcrd.CustomService;
import io.fabric8.custom.operator.authcrd.CustomServiceList;
import io.fabric8.custom.operator.authcrd.DoneableCustomService;
import io.fabric8.custom.operator.controller.CRDAuthController;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionBuilder;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionList;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.SharedInformerFactory;

/**
 * Main Class for Operator, you can run this sample using this command:
 *
 * mvn exec:java
 * -Dexec.mainClass=io.fabric8.custom.operator.CustomServiceOperatorMain
 */
public class CustomServiceOperatorMain {
   public static Logger logger =
         Logger.getLogger(CustomServiceOperatorMain.class.getName());

   public static void main(String args[]) {
      try (KubernetesClient client = new DefaultKubernetesClient()) {
         String namespace = client.getNamespace();
         if (namespace == null) {
            logger.log(Level.INFO,
                  "No namespace found via config, assuming default.");
            namespace = "default";
         }

         logger.log(Level.INFO, "Using namespace : " + namespace);

         CustomResourceDefinition customServiceCustomResourceDefinition =
               new CustomResourceDefinitionBuilder().withNewMetadata()
                     .withName("auths.v3.vmware.com").endMetadata()
                     .withNewSpec().withGroup("v3.vmware.com").withVersion("v1")
                     .withNewNames().withKind("Auth").withPlural("auths")
                     .endNames().withScope("Namespaced").endSpec().build();

         CustomResourceDefinitionContext customServiceCustomResourceDefinitionContext =
               new CustomResourceDefinitionContext.Builder().withVersion("v1")
                     .withScope("Namespaced").withGroup("v3.vmware.com")
                     .withPlural("auths").build();

         SharedInformerFactory informerFactory = client.informers();

         MixedOperation<CustomService, CustomServiceList, DoneableCustomService, Resource<CustomService, DoneableCustomService>> customServiceClient =
               client.customResources(customServiceCustomResourceDefinition,
                     CustomService.class, CustomServiceList.class,
                     DoneableCustomService.class);
         SharedIndexInformer<CustomResourceDefinition> crdSharedIndexInformer =
               informerFactory.sharedIndexInformerFor(
                     CustomResourceDefinition.class,
                     CustomResourceDefinitionList.class, 10 * 60 * 1000);
         SharedIndexInformer<CustomService> customServiceSharedIndexInformer =
               informerFactory.sharedIndexInformerForCustomResource(
                     customServiceCustomResourceDefinitionContext,
                     CustomService.class, CustomServiceList.class,
                     10 * 60 * 1000);
         CRDAuthController customServiceController = new CRDAuthController(
               client, customServiceClient, crdSharedIndexInformer,
               customServiceSharedIndexInformer, namespace);

         customServiceController.create();
         informerFactory.startAllRegisteredInformers();

         customServiceController.run();
      }
   }
}
