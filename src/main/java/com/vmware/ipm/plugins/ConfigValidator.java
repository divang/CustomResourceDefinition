/* ************************************************************************
 * Copyright 2019 VMware, Inc.  All rights reserved. -- VMware Confidential
 * ************************************************************************/
package com.vmware.ipm.plugins;

import java.util.List;

import com.vmware.appliance.infraprofile.Notification;

@FunctionalInterface
public interface ConfigValidator {

   List<Notification> validate(Object configValue);

}
