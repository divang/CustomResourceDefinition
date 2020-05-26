package io.fabric8.custom.operator.crd;

public class ServiceSpec {
   //ApplianceManagment
   private Object desired_state;


   public Object getDesired_state() {
      return desired_state;
   }

   public void setDesired_state(Object desired_state) {
      this.desired_state = desired_state;
   }

   @Override
   public String toString() {
      return desired_state.toString();
   }
}