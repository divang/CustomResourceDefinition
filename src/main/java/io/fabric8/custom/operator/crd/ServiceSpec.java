package io.fabric8.custom.operator.crd;

public class ServiceSpec {

   //ApplianceManagment
   private Object testmgmt;


   public Object getTestmgmt() {
      return testmgmt;
   }

   public void setTestmgmt(Object testmgmt) {
      this.testmgmt = testmgmt;
   }

   @Override
   public String toString() {
      // TODO Auto-generated method stub
      return testmgmt.toString();
   }
}
