package com.vmware.ipm.plugins;

public interface Plugin {

   Object getCurrentDesiredState();

   Object validate(Object desiredState);

   Object apply(Object desiredState);

}
