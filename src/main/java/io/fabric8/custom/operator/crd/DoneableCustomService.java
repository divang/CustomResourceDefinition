package io.fabric8.custom.operator.crd;

import io.fabric8.kubernetes.api.builder.Function;
import io.fabric8.kubernetes.client.CustomResourceDoneable;


public class DoneableCustomService
      extends CustomResourceDoneable<CustomService> {
   public DoneableCustomService(CustomService resource, Function function) {
      super(resource, function);
   }
}
