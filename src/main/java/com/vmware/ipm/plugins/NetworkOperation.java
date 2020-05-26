/* ************************************************************************
 * Copyright 2020 VMware, Inc.  All rights reserved. -- VMware Confidential
 * ************************************************************************/
package com.vmware.ipm.plugins;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.vmware.appliance.infraprofile.core.ApplyEffect;
import com.vmware.appliance.infraprofile.pluginHelper.ImportConfigValue;
import com.vmware.appliance.infraprofile.pluginHelper.InvokeDataSourceApi;
import com.vmware.appliance.vcenter.settings.ConfigTypes;
import com.vmware.appliance.vcenter.settings.Notification;
import com.vmware.appliance.vcenter.settings.ServiceType;
import com.vmware.appliance.vcenter.settings.config.components.applmgmt.ApplianceNetwork;
import com.vmware.appliance.vcenter.settings.config.components.applmgmt.DnsServerConfiguration;
import com.vmware.appliance.vcenter.settings.config.components.applmgmt.FirewallRulePolicy;
import com.vmware.appliance.vcenter.settings.config.components.applmgmt.ProxyConfiguration;

public class NetworkOperation {
   private static Log _logger = LogFactory.getLog(NetworkOperation.class);
   private static final String DATA_SOURCE_DETAIL = "DATA_SOURCE_DETAIL";
   private static final String NETWORK_DATA_SOURCE_FILE =
         "ApplianceNetworkDataSource.json";
   private static final String SYSTEMD_NETWORKD = "systemd-networkd";
   private static final String SYSTEMD_RESOLVED = "systemd-resolved";

   /**
    * This method will collect and return current configuration of ApplianceNetwork.
    * 
    * @return : Current configuration of ApplianceNetwork in VC.
    */
   public ApplianceNetwork getCurrentDesiredState() {
      _logger.info("Start get current config ApplianceNetwork");
      ApplianceNetwork applianceNetworkSpec = new ApplianceNetwork();
      InvokeDataSourceApi invokeDataSourceApiNetwork =
            new InvokeDataSourceApi(applianceNetworkSpec);
      JsonObject dataSourceJsonObject =
            invokeDataSourceApiNetwork.getDataSourceFile(NETWORK_DATA_SOURCE_FILE);

      if (dataSourceJsonObject == null) {
         String error_message =
               String.format("DataSource file not found: %s", NETWORK_DATA_SOURCE_FILE);
         _logger.error(error_message);
         throw new RuntimeException(error_message);
      }
      JsonArray dataSourceDetailsArray =
            dataSourceJsonObject.get(DATA_SOURCE_DETAIL).getAsJsonArray();
      _logger.debug("Got dataSourceJsonObject ApplianceNetwork");
      for (int index = 0; index < dataSourceDetailsArray.size(); index++) {
         JsonObject dataSourceDetailObject =
               dataSourceDetailsArray.get(index).getAsJsonObject();
         invokeDataSourceApiNetwork.invokeApi(dataSourceDetailObject);
      }
      _logger.info("ApplianceNetwork get current config complete");
      _logger.debug(String.format("Final Plugin Spec: %s", applianceNetworkSpec));
      return applianceNetworkSpec;
   }

   /**
    * This method will write the desire state configuration in vCenter.
    * 
    * @param desiredState
    *           : The desired state
    * @return : Apply effect on vCenter.
    */
   public ApplyEffect apply(Object desiredState, ApplyEffect applyEffect) {
      _logger.info("Start apply ApplianceNetwork");
      if (desiredState == null) {
         _logger.error("Apply config spec of Appliance Network plugin is " + "null");
         applyEffect.setStatus(ConfigTypes.ApplyStatus.ERROR);
         return applyEffect;
      }
      if (!(desiredState instanceof ApplianceNetwork)) {
         String error_message = "Apply config spec of Appliance network "
               + "is not instance of ApplianceNetwork class.";
         _logger.error(error_message);
         throw new RuntimeException("error_message");
      }
      ApplianceNetwork applianceNetworkSpec = (ApplianceNetwork) desiredState;
      ImportConfigValue importConfigValue = new ImportConfigValue();
      InvokeDataSourceApi invokeDataSourceApi =
            new InvokeDataSourceApi(applianceNetworkSpec);
      importConfigValue.setConfigSpecObject(applianceNetworkSpec);

      JsonObject dataSourceJsonObject =
            invokeDataSourceApi.getDataSourceFile(NETWORK_DATA_SOURCE_FILE);
      JsonArray dataSourceDetailsArray =
            dataSourceJsonObject.get(DATA_SOURCE_DETAIL).getAsJsonArray();

      for (int index = 0; index < dataSourceDetailsArray.size(); index++) {
         JsonObject dataSourceDetailObject =
               dataSourceDetailsArray.get(index).getAsJsonObject();
         importConfigValue.importConfigValueFromVmodl(dataSourceDetailObject);
      }
      _logger.info("Complete apply ApplianceNetwork");
      applyEffect.setStatus(ConfigTypes.ApplyStatus.SUCCESS);
      return applyEffect;
   }

   /**
    * This method will validate the config value of network desiredState.
    * 
    * @param desiredState
    * @param errorNotfication
    */
   public void validate(Object desiredState, List<Notification> errorNotfication) {
      if (desiredState == null) {
         _logger.error("Validate config spec of Appliance network " + "plugin is null");
         return;
      }
      if (!(desiredState instanceof ApplianceNetwork)) {
         String error_message = "Validate config spec of Appliance network "
               + "is not instance of ApplianceNetwork class.";
         _logger.error(error_message);
         throw new RuntimeException(error_message);
      }
      _logger.debug(String.format("Started the validation of ApplianceNetwork " + "%s",
            desiredState.toString()));
      Set<String> systemdSet = new HashSet<>();
      Map<ServiceType, Set<String>> services = new HashMap<>();
      ConfigValidationLogicNetwork configvalidator = new ConfigValidationLogicNetwork();
      ApplianceNetwork network = (ApplianceNetwork) desiredState;

      List<FirewallRulePolicy> firewalConfList = network.getFirewallRulePolicies();
      if (firewalConfList != null) {
         List<Notification> firewallNotification =
               configvalidator.validatefirewall(firewalConfList);
         if (CollectionUtils.isNotEmpty(firewallNotification)) {
            errorNotfication.addAll(firewallNotification);
            _logger.debug(String.format("Firewall Notofications %s",
                  firewallNotification.toString()));
         }
      }

      List<ProxyConfiguration> proxyConfList = network.getProxyConfiguration();
      if (proxyConfList != null && !proxyConfList.isEmpty()) {
         List<Notification> proxyNotification = null;
         proxyNotification = configvalidator.validateproxy(proxyConfList);
         if (CollectionUtils.isNotEmpty(proxyNotification)) {
            errorNotfication.addAll(proxyNotification);
            _logger.debug(
                  String.format("Proxy Notofications %s", proxyNotification.toString()));
         }
      }
      DnsServerConfiguration dnsConf = network.getDnsServerConfiguration();
      if (dnsConf != null) {
         List<Notification> dnsNotification = configvalidator.validatedns(dnsConf);
         if (CollectionUtils.isNotEmpty(dnsNotification)) {
            errorNotfication.addAll(dnsNotification);
            _logger.debug(
                  String.format("Dns Notofications %s", dnsNotification.toString()));
         } else {
            systemdSet.add(SYSTEMD_NETWORKD);
            systemdSet.add(SYSTEMD_RESOLVED);
         }
      }
   }
}
