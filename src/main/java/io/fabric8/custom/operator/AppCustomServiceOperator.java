package io.fabric8.custom.operator;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.fabric8.custom.operator.controller.CRDController;
import io.fabric8.custom.operator.crd.CustomService;
import io.fabric8.custom.operator.crd.CustomServiceList;
import io.fabric8.custom.operator.crd.DoneableCustomService;
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

public class AppCustomServiceOperator {

   public static Logger logger =
         Logger.getLogger(AppCustomServiceOperator.class.getName());

   private CRDController customServiceController;

   public static void main(String args[]) {
      // CustomServiceOperator customServiceOperatorMain = new CustomServiceOperator();
      // customServiceOperatorMain.start();
   }

   public void start() {
      try (KubernetesClient client = new DefaultKubernetesClient()) {
         String namespace = client.getNamespace();
         if (namespace == null) {
            logger.log(Level.INFO, "No namespace found via config, assuming default.");
            namespace = "default";
         }

         logger.log(Level.INFO, "Using namespace : " + namespace);

         CustomResourceDefinition customServiceCustomResourceDefinition =
               new CustomResourceDefinitionBuilder().withNewMetadata()
                     .withName("desiredstates.ipm.k8.vmware.com").endMetadata()
                     .withNewSpec().withGroup("ipm.k8.vmware.com").withVersion("v1")
                     .withNewNames().withKind("Desiredstate").withPlural("desiredstates")
                     .endNames().withScope("Namespaced").endSpec().build();

         CustomResourceDefinitionContext customServiceCustomResourceDefinitionContext =
               new CustomResourceDefinitionContext.Builder().withVersion("v1")
                     .withScope("Namespaced").withGroup("ipm.k8.vmware.com")
                     .withPlural("desiredstates").build();

         SharedInformerFactory informerFactory = client.informers();

         MixedOperation<CustomService, CustomServiceList, DoneableCustomService, Resource<CustomService, DoneableCustomService>> customServiceClient =
               client.customResources(customServiceCustomResourceDefinition,
                     CustomService.class, CustomServiceList.class,
                     DoneableCustomService.class);
         SharedIndexInformer<CustomResourceDefinition> crdSharedIndexInformer =
               informerFactory.sharedIndexInformerFor(CustomResourceDefinition.class,
                     CustomResourceDefinitionList.class, 10 * 60 * 1000);
         SharedIndexInformer<CustomService> customServiceSharedIndexInformer =
               informerFactory.sharedIndexInformerForCustomResource(
                     customServiceCustomResourceDefinitionContext, CustomService.class,
                     CustomServiceList.class, 10 * 60 * 1000);
         customServiceController = new CRDController(client, customServiceClient,
               crdSharedIndexInformer, customServiceSharedIndexInformer, namespace);

         customServiceController.create();
         informerFactory.startAllRegisteredInformers();

         customServiceController.run();
      }
   }

   public Object getCurrentState() {
      return customServiceController.getCurrentObservedState();
   }
}
