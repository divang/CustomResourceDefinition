/* ************************************************************************
 * Copyright 2020 VMware, Inc.  All rights reserved. -- VMware Confidential
 * ************************************************************************/
package com.vmware.ipm.plugins;

import static com.vmware.appliance.infraprofile.commons.Constants.REGEX_SPACES;
import static com.vmware.appliance.infraprofile.util.MessageConstants.INVALID_IP_DNS;
import static com.vmware.appliance.infraprofile.util.MessageConstants.INVALID_IP_FIREWALL;
import static com.vmware.appliance.infraprofile.util.MessageConstants.INVALID_PORT_PROXY;
import static com.vmware.appliance.infraprofile.util.MessageConstants.INVALID_URL_PROXY;
import static com.vmware.appliance.infraprofile.util.MessageConstants.IP_INVALID_MESSAGE;
import static com.vmware.appliance.infraprofile.util.MessageConstants.IP_INVALID_RESOLUTION;
import static com.vmware.appliance.infraprofile.util.MessageConstants.PORT_OUT_OF_RANGE_MESSAGE;
import static com.vmware.appliance.infraprofile.util.MessageConstants.PORT_OUT_OF_RANGE_RESOLUTION;
import static com.vmware.appliance.infraprofile.util.MessageConstants.URL_INVALID_MESSAGE_FORMAT;
import static com.vmware.appliance.infraprofile.util.MessageConstants.URL_INVALID_RESOLUTION;
import static com.vmware.appliance.infraprofile.util.ValidationUtils.isInPortRange;
import static com.vmware.appliance.infraprofile.util.ValidationUtils.isValidIP;
import static com.vmware.appliance.infraprofile.util.ValidationUtils.isValidURL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.vmware.appliance.infraprofile.util.NotificationUtil;
import com.vmware.appliance.vcenter.settings.Notification;
import com.vmware.appliance.vcenter.settings.config.components.applmgmt.DnsServerConfiguration;
import com.vmware.appliance.vcenter.settings.config.components.applmgmt.FirewallRulePolicy;
import com.vmware.appliance.vcenter.settings.config.components.applmgmt.ProxyConfiguration;

public class ConfigValidationLogicNetwork {
   public static Log _logger = LogFactory.getLog(ConfigValidationLogicNetwork.class);

   /**
    * This method will validate firewall configurations.
    *
    * @param firewalList
    *           : List of firewall configurations
    * @return Notification if validation fail , else empty list.
    **/
   public static List<Notification> validatefirewall(
         List<FirewallRulePolicy> firewalList) {
      List<Notification> firewallnotifications = new ArrayList<>();
      for (FirewallRulePolicy firewallRulePolicy : firewalList) {
         String ipValue = firewallRulePolicy.getAddress();
         if (ipValue != null && !ipValue.isEmpty()) {
            List<String> ips = Arrays.asList((ipValue.split(REGEX_SPACES)));
            ips.forEach(ip -> {
               if (!isValidIP(ip)) {
                  List<Notification> notificationList = NotificationUtil
                        .createSoftwareNotificationList(INVALID_IP_FIREWALL,
                              IP_INVALID_MESSAGE, IP_INVALID_RESOLUTION);
                  firewallnotifications.addAll(notificationList);
               }
            });
         }
      }
      _logger.debug(String.format("Firewall notifications %s",
            firewallnotifications.toString()));
      return firewallnotifications;
   }

   /**
    * This method will validate proxy configurations.
    *
    * @param proxyList
    *           : List of proxy configurations
    * @return Notification if validation fail , else empty list.
    **/
   public static List<Notification> validateproxy(List<ProxyConfiguration> proxyList) {
      List<Notification> proxynotifications = new ArrayList<>();
      List<Notification> notificationList;
      for (ProxyConfiguration conf : proxyList) {
         try {
            long configValue = conf.getPort();
            Integer ConfigValue = (int) configValue;
            if (!isInPortRange(ConfigValue)) {
               notificationList =
                     NotificationUtil.createSoftwareNotificationList(INVALID_PORT_PROXY,
                           PORT_OUT_OF_RANGE_MESSAGE, PORT_OUT_OF_RANGE_RESOLUTION);
               proxynotifications.addAll(notificationList);
            }
         } catch (NullPointerException e) {
            _logger.error("Network : Got Null Pointer Exception for port value "
                  + "in firewall config");
            notificationList = NotificationUtil.createSoftwareNotificationList(
                  INVALID_PORT_PROXY, "Port is null", PORT_OUT_OF_RANGE_RESOLUTION);
            proxynotifications.addAll(notificationList);
         }

         String proxyServer = conf.getServer();
         if (proxyServer != null && !proxyServer.isEmpty()) {
            if (!isValidURL(proxyServer)) {
               notificationList =
                     NotificationUtil.createSoftwareNotificationList(INVALID_URL_PROXY,
                           URL_INVALID_MESSAGE_FORMAT, URL_INVALID_RESOLUTION);
               proxynotifications.addAll(notificationList);
            }
         }
      }
      _logger.debug(
            String.format("Proxy Notifications %s", proxynotifications.toString()));
      return proxynotifications;
   }

   /**
    * This method will validate dns configurations.
    *
    * @param dnsConf
    *           : Dns configurations
    * @return Notification if validation fail , else empty list.
    **/
   public static List<Notification> validatedns(DnsServerConfiguration dnsConf) {
      List<Notification> dnsnotifications = new ArrayList<>();
      List<String> dnsServers = dnsConf.getServers();
      if (dnsServers == null) {
         return dnsnotifications;
      }
      for (String eachServer : dnsServers) {
         List<String> ips = Arrays.asList((eachServer.split(REGEX_SPACES)));
         ips.forEach(ip -> {
            if (!isValidIP(eachServer.substring(1, eachServer.length() - 1))) {
               List<Notification> notificationList =
                     NotificationUtil.createSoftwareNotificationList(INVALID_IP_DNS,
                           IP_INVALID_MESSAGE, IP_INVALID_RESOLUTION);
               dnsnotifications.addAll(notificationList);
            }
         });

      }
      _logger.debug(String.format("Dns Notifications %s", dnsnotifications.toString()));
      return dnsnotifications;
   }
}
