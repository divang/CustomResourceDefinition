/* ************************************************************************
 * Copyright 2020 VMware, Inc.  All rights reserved. -- VMware Confidential
 * ************************************************************************/
package com.vmware.ipm.plugins;

import static com.vmware.appliance.infraprofile.util.MessageConstants.INVALID_EMAIL_FORMAT_ACCOUNTS;
import static com.vmware.appliance.infraprofile.util.MessageConstants.INVALID_EMAIL_FORMAT_MESSAGE;
import static com.vmware.appliance.infraprofile.util.MessageConstants.INVALID_EMAIL_FORMAT_RESOLUTION;
import static com.vmware.appliance.infraprofile.util.MessageConstants.INVALID_PORT_SYSLOG;
import static com.vmware.appliance.infraprofile.util.MessageConstants.INVALID_URL;
import static com.vmware.appliance.infraprofile.util.MessageConstants.INVALID_URL_BACKUP;
import static com.vmware.appliance.infraprofile.util.MessageConstants.MALFORMED_URL_BACKUP;
import static com.vmware.appliance.infraprofile.util.MessageConstants.PORT_OUT_OF_RANGE_MESSAGE;
import static com.vmware.appliance.infraprofile.util.MessageConstants.PORT_OUT_OF_RANGE_RESOLUTION;
import static com.vmware.appliance.infraprofile.util.MessageConstants.URL_INVALID_MESSAGE_FORMAT;
import static com.vmware.appliance.infraprofile.util.MessageConstants.URL_INVALID_RESOLUTION;
import static com.vmware.appliance.infraprofile.util.ValidationUtils.isInPortRange;
import static com.vmware.appliance.infraprofile.util.ValidationUtils.isValidEmailId;
import static com.vmware.appliance.infraprofile.util.ValidationUtils.isValidURL;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.vmware.appliance.infraprofile.util.NotificationUtil;
import com.vmware.appliance.vcenter.settings.Notification;
import com.vmware.appliance.vcenter.settings.config.components.applmgmt.BackupSchedule;
import com.vmware.appliance.vcenter.settings.config.components.applmgmt.LocalAccounts;
import com.vmware.appliance.vcenter.settings.config.components.applmgmt.LogForwarding;

/**
 * This class is responsible to write the validation logic of different config properties .
 */
class ConfigValidationLogic {

   public static Log _logger = LogFactory.getLog(ConfigValidationLogic.class);

   /**
    * This method will validate local Accounts configurations.
    *
    * @param localAccounts
    *           : List of local Accounts
    * @return Notification if validation fail , else empty list.
    **/
   public static List<Notification> validateLocalAccounts(LocalAccounts localAccounts) {
      List<Notification> accountnotifications = new ArrayList<>();
      List<Notification> notificationList;
      String emailID = localAccounts.getEmail();
      if (emailID == null || emailID.isEmpty()) {
         return accountnotifications;
      }
      if (!isValidEmailId(emailID)) {
         notificationList = NotificationUtil.createSoftwareNotificationList(
               INVALID_EMAIL_FORMAT_ACCOUNTS, INVALID_EMAIL_FORMAT_MESSAGE,
               INVALID_EMAIL_FORMAT_RESOLUTION);
         accountnotifications.addAll(notificationList);
      }
      _logger.info("Accounts Notifications" + accountnotifications.toString());
      return accountnotifications;
   }

   /**
    * This method will validate syslog configurations.
    *
    * @param forwardingList
    *           : List of forwarding configurations
    * @return Notification if validation fail , else empty list.
    **/
   public static List<Notification> validateForwarding(
         List<LogForwarding> forwardingList) {

      List<Notification> syslognotifications = new ArrayList<>();
      List<Notification> notificationList;
      for (LogForwarding eachforwarding : forwardingList) {
         long eachforwardingPort = eachforwarding.getPort();
         Integer port = (int) eachforwardingPort;
         if (port != null) {
            if (!isInPortRange(port)) {
               notificationList =
                     NotificationUtil.createSoftwareNotificationList(INVALID_PORT_SYSLOG,
                           PORT_OUT_OF_RANGE_MESSAGE, PORT_OUT_OF_RANGE_RESOLUTION);
               syslognotifications.addAll(notificationList);
            }
         }
      }
      _logger.info("Syslog Notifications" + syslognotifications.toString());
      return syslognotifications;
   }

   /**
    * This method will validate backupschedule.
    *
    * @param backupScheduleList
    *           : List of backupschedule configurations
    * @return Notification if validation fail , else empty list.
    **/
   public static List<Notification> validateBackupSchedule(
         List<BackupSchedule> backupScheduleList) {

      List<Notification> backupSchedulenotifications = new ArrayList<>();
      List<Notification> notificationList = null;
      for (BackupSchedule backupShedule : backupScheduleList) {
         try {
            URI uri = backupShedule.getLocation();
            URL url = uri.toURL();
            if (!isValidURL(url.toString())) {
               notificationList =
                     NotificationUtil.createSoftwareNotificationList(INVALID_URL_BACKUP,
                           URL_INVALID_MESSAGE_FORMAT, URL_INVALID_RESOLUTION);
               backupSchedulenotifications.addAll(notificationList);
            }

         } catch (NullPointerException | MalformedURLException e) {

            notificationList =
                  NotificationUtil.createSoftwareNotificationList(MALFORMED_URL_BACKUP,
                        URL_INVALID_MESSAGE_FORMAT, URL_INVALID_RESOLUTION);
            backupSchedulenotifications.addAll(notificationList);
            _logger.info("APPLMGMT : Exception while validating URI in backupschedule"
                  + notificationList.toString());
         } catch (Exception e) {
            notificationList = NotificationUtil.createSoftwareNotificationList(
                  INVALID_URL, URL_INVALID_MESSAGE_FORMAT, URL_INVALID_RESOLUTION);
            backupSchedulenotifications.addAll(notificationList);
            _logger.info("APPLMGMT : Exception while validating URI in backupschedule"
                  + notificationList.toString());
         }
      }
      _logger.info(
            "BackupSchedule Notifications" + backupSchedulenotifications.toString());
      return backupSchedulenotifications;

   }
}
