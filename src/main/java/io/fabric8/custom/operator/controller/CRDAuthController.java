package io.fabric8.custom.operator.controller;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.fabric8.custom.operator.authcrd.CustomService;
import io.fabric8.custom.operator.authcrd.CustomServiceList;
import io.fabric8.custom.operator.authcrd.DoneableCustomService;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import io.fabric8.kubernetes.client.informers.cache.Cache;
import io.fabric8.kubernetes.client.informers.cache.Lister;

public class CRDAuthController {
   private BlockingQueue<String> workqueue;
   private SharedIndexInformer<CustomService> customServiceInformer;
   private SharedIndexInformer<CustomResourceDefinition> crdInformer;
   private Lister<CustomService> customServiceLister;
   private Lister<CustomResourceDefinition> crdLister;
   private KubernetesClient kubernetesClient;
   private MixedOperation<CustomService, CustomServiceList, DoneableCustomService, Resource<CustomService, DoneableCustomService>> customServiceClient;
   public static Logger logger =
         Logger.getLogger(CRDAuthController.class.getName());
   public static String APP_LABEL = "app";

   public CRDAuthController(KubernetesClient kubernetesClient,
         MixedOperation<CustomService, CustomServiceList, DoneableCustomService, Resource<CustomService, DoneableCustomService>> customServiceClient,
         SharedIndexInformer<CustomResourceDefinition> crdInformer,
         SharedIndexInformer<CustomService> customServiceInformer,
         String namespace) {
      this.kubernetesClient = kubernetesClient;
      this.customServiceClient = customServiceClient;
      this.customServiceLister =
            new Lister<>(customServiceInformer.getIndexer(), namespace);
      this.customServiceInformer = customServiceInformer;
      this.crdLister = new Lister<>(crdInformer.getIndexer(), namespace);
      this.crdInformer = crdInformer;
      this.workqueue = new ArrayBlockingQueue<>(1024);
   }

   public void create() {
      customServiceInformer
            .addEventHandler(new ResourceEventHandler<CustomService>() {
               @Override
               public void onAdd(CustomService customService) {
                  enqueueCustomService(customService);
               }

               @Override
               public void onUpdate(CustomService customService,
                     CustomService newCustomService) {
                  enqueueCustomService(newCustomService);
               }

               @Override
               public void onDelete(CustomService customService, boolean b) {
               }
            });

      crdInformer.addEventHandler(
            new ResourceEventHandler<CustomResourceDefinition>() {
               @Override
               public void onAdd(CustomResourceDefinition crd) {
                  handleCRDObject(crd);
               }

               @Override
               public void onUpdate(CustomResourceDefinition oldPod,
                     CustomResourceDefinition newPod) {
                  if (oldPod.getMetadata().getResourceVersion() == newPod
                        .getMetadata().getResourceVersion()) {
                     return;
                  }
                  handleCRDObject(newPod);
               }

               @Override
               public void onDelete(CustomResourceDefinition crd, boolean b) {
               }
            });
   }

   public void run() {
      logger.log(Level.INFO, "Starting CustomService controller");

      while (true) {
         try {
            getCurrentObservedState();
            logger.log(Level.INFO, "trying to fetch item from workqueue...");
            if (workqueue.isEmpty()) {
               logger.log(Level.INFO, "Work Queue is empty");

            }
            String key = workqueue.take();
            logger.log(Level.INFO, "Got " + key);
            if (key == null || key.isEmpty() || (!key.contains("/"))) {
               logger.log(Level.WARNING, "invalid resource key: " + key);
            }

            // Get the CustomService resource's name from key which is in format namespace/name
            String name = key.split("/")[1];
            CustomService customService =
                  customServiceLister.get(key.split("/")[1]);
            if (customService == null) {
               logger.log(Level.SEVERE, "CustomService " + name
                     + " in workqueue no longer exists");
               return;
            }
            reconcile(customService);

         } catch (InterruptedException interruptedException) {
            logger.log(Level.SEVERE, "controller interrupted..");
         }
      }
   }

   /**
    * Tries to achieve the desired state for customservice.
    *
    * @param customService
    *           specified customservice
    */
   private void reconcile(CustomService customService) {
      //handle desire state changes
      System.out.println("****************************************");
      System.out.println("***** Handling Auth Config changes *****");
      System.out.println("Custom Service Object :" + customService);
      System.out.println("****************************************");
   }


   private void enqueueCustomService(CustomService customService) {
      logger.log(Level.INFO, "enqueueCustomService("
            + customService.getMetadata().getName() + ")");
      String key = Cache.metaNamespaceKeyFunc(customService);
      logger.log(Level.INFO, "Going to enqueue key " + key);
      if (key != null || !key.isEmpty()) {
         logger.log(Level.INFO, "Adding item to workqueue");
         workqueue.add(key);
      }
   }

   private void handleCRDObject(CustomResourceDefinition crd) {
      logger.log(Level.INFO,
            "handleCRDObject(" + crd.getMetadata().getName() + ")");
      OwnerReference ownerReference = getControllerOf(crd);
      if (!ownerReference.getKind().equalsIgnoreCase("CustomService")) {
         return;
      }
      CustomService customService =
            customServiceLister.get(ownerReference.getName());
      if (customService != null) {
         enqueueCustomService(customService);
      }
   }

   private OwnerReference getControllerOf(CustomResourceDefinition crd) {
      List<OwnerReference> ownerReferences =
            crd.getMetadata().getOwnerReferences();
      for (OwnerReference ownerReference : ownerReferences) {
         if (ownerReference.getController().equals(Boolean.TRUE)) {
            return ownerReference;
         }
      }
      return null;
   }

   private Object getCurrentObservedState() {
      CustomServiceList customServiceList =
            customServiceClient.inAnyNamespace().list();
      logger.log(Level.INFO,
            "Current Observed --->" + customServiceList.getItems().get(0));
      return null;
   }
}
