package io.fabric8.custom.operator.authcrd;

public class ServiceSpec {

   //ApplianceManagment
   private Object authmgmt;

   public Object getAuthmgmt() {
      return authmgmt;
   }

   public void setAuthmgmt(Object authmgmt) {
      this.authmgmt = authmgmt;
   }

   @Override
   public String toString() {
      return authmgmt.toString();
   }
}
