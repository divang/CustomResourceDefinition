package com.example.springboot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

public class DeploymentExamples {
   private static final Logger logger =
         LoggerFactory.getLogger(DeploymentExamples.class);

   public static void main(String[] args) throws InterruptedException {

      Config config = new ConfigBuilder().build();
      System.out.println("Config-" + config.getClientCertFile());
      System.out.println("Config-" + config.getClientKeyFile());
      System.out.println("Config-" + config.getUsername());
      System.out.println("Config-" + config.getUserAgent());

      int j = 0;
      while (j < 1) {

         KubernetesClient client = null;

         try {
            client = new DefaultKubernetesClient(config);
            // Create a namespace for all our stuff
            Namespace ns = new NamespaceBuilder().withNewMetadata().withName("default")
                  .addToLabels("this", "rocks-1").endMetadata().build();
            log("Created/Replace namespace", client.namespaces().createOrReplace(ns));

            //            ServiceAccount fabric8 = new ServiceAccountBuilder().withNewMetadata()
            //                  .withName("fabric8").endMetadata().build();
            //
            //            client.serviceAccounts().inNamespace("default").createOrReplace(fabric8);
            //            for (int i = 0; i < 1; i++) {
            //               System.err.println("Iteration:" + (i + 1));
            //               Deployment deployment = new DeploymentBuilder().withNewMetadata()
            //                     .withName("test2").endMetadata().withNewSpec().withReplicas(1)
            //                     .withNewTemplate().withNewMetadata().addToLabels("app", "test2")
            //                     .endMetadata().withNewSpec().addNewContainer().withName("test2")
            //                     .withImage("test2").addNewPort().withContainerPort(80).endPort()
            //                     .endContainer().endSpec().endTemplate().withNewSelector()
            //                     .addToMatchLabels("app", "test2").endSelector().endSpec().build();
            //
            //
            //               deployment = client.apps().deployments().inNamespace("default")
            //                     .create(deployment);
            //               log("Created deployment", deployment);
            //
            //               //System.err.println("Scaling up:" + deployment.getMetadata().getName());
            //               //               client.apps().deployments().inNamespace("default").withName("test")
            //               //                     .scale(2, true);
            //               //               log("Created replica sets:", client.apps().replicaSets()
            //               //                     .inNamespace("default").list().getItems());
            //               //               System.err.println("Deleting:" + deployment.getMetadata().getName());
            //               //client.resource(deployment).delete();
            //            }
            log("Done.");

         } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Retrying client connection.");
         } finally {
            //client.namespaces().withName("default").delete();
            client.close();
            System.out.println("Closing client connection.");

         }
         j++;
         Thread.sleep(2000);
         System.out.println("Trying again ... " + j);
      }
   }


   private static void log(String action, Object obj) {
      logger.info("{}: {}", action, obj);
   }

   private static void log(String action) {
      logger.info(action);
   }
}
