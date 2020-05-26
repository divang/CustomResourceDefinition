/* ************************************************************************
 * Copyright 2020 VMware, Inc.  All rights reserved. -- VMware Confidential
 * ************************************************************************/
package com.vmware.ipm.plugins;

import static com.vmware.appliance.infraprofile.util.MessageConstants.APPLMGMT_VALID;
import static com.vmware.appliance.infraprofile.util.MessageConstants.MESSAGE;
import static com.vmware.appliance.infraprofile.util.MessageConstants.NO_RESOLUTION;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.vmware.appliance.infraprofile.core.ApplyEffect;
import com.vmware.appliance.infraprofile.core.ValidationResult;
import com.vmware.appliance.infraprofile.pluginHelper.ImportConfigValue;
import com.vmware.appliance.infraprofile.pluginHelper.InvokeDataSourceApi;
import com.vmware.appliance.infraprofile.util.NotificationUtil;
import com.vmware.appliance.vcenter.settings.ApplyImpact;
import com.vmware.appliance.vcenter.settings.ConfigTypes;
import com.vmware.appliance.vcenter.settings.Impact;
import com.vmware.appliance.vcenter.settings.Notification;
import com.vmware.appliance.vcenter.settings.Notifications;
import com.vmware.appliance.vcenter.settings.ServiceType;
import com.vmware.appliance.vcenter.settings.StatusType;
import com.vmware.appliance.vcenter.settings.config.components.applmgmt.ApplianceManagement;
import com.vmware.appliance.vcenter.settings.config.components.applmgmt.BackupSchedule;
import com.vmware.appliance.vcenter.settings.config.components.applmgmt.LocalAccounts;
import com.vmware.appliance.vcenter.settings.config.components.applmgmt.LogForwarding;
import com.vmware.appliance.vcenter.settings.config.components.applmgmt.Smtp;

/**
 * IPM plugin of Appliance Management service. This plugin will export/import the existing vCenter
 * configuration of Appliance management service.
 */
public class ApplianceManagementPlugin {
   public static Log _logger = LogFactory.getLog(ApplianceManagementPlugin.class);

   private ApplianceManagement applianceManagementSpec;
   private InvokeDataSourceApi invokeDataSourceApi;
   private ImportConfigValue importConfigValueFromVmodl;
   private static final String DATA_SOURCE_DETAIL = "DATA_SOURCE_DETAIL";
   private static final String DATA_SOURCE_FILE = "ApplianceManagementDataSource.json";
   private static final String SEND_MAIL = "/etc/mail/sendmail.cf";
   private static final String serverWord = "DS";
   private static final String portWord = "Mrelay";
   public static final String SENDMAIL = "sendmail";
   public static final String RSYSLOG = "rsyslog";

   /**
    * Constructor will instantiate Appliance Management Vmodl spec and InvokeDataSourceApi object.
    */
   public ApplianceManagementPlugin() {

   }

   /**
    * This method will collect and return current configuration of ApplianceManagement.
    * 
    * @return : Current configuration of ApplianceManagement in VC.
    */

   public Object getCurrentDesiredState() {
      createDependentObject();

      _logger.info(" Start export ApplianceManagement");
      JsonObject dataSourceJsonObject =
            invokeDataSourceApi.getDataSourceFile(DATA_SOURCE_FILE);
      if (dataSourceJsonObject == null) {
         _logger.error("DataSource file not found: " + DATA_SOURCE_FILE);
         throw new RuntimeException("DataSource file not found: " + DATA_SOURCE_FILE);
      }
      JsonArray dataSourceDetailsArray =
            dataSourceJsonObject.get(DATA_SOURCE_DETAIL).getAsJsonArray();
      _logger.info(" Got dataSourceJsonObject ApplianceManagement");
      for (int index = 0; index < dataSourceDetailsArray.size(); index++) {
         JsonObject dataSourceDetailObject =
               dataSourceDetailsArray.get(index).getAsJsonObject();
         _logger.info("ApplianceManagement invokeDataSourceApi start");
         invokeDataSourceApi.invokeApi(dataSourceDetailObject);
         _logger.info("ApplianceManagement invokeDataSourceApi complete");
      }
      _logger.info("Final Plugin Spec: " + applianceManagementSpec);
      exportSendmail(applianceManagementSpec);
      return applianceManagementSpec;

   }


   public Object getDesiredState() {
      return getCurrentDesiredState();
   }

   /**
    * This method will write the desire state configuration in vCenter.
    *
    * @param desiredState
    *           : The desired state
    * @return : Apply effect on vCenter.
    */

   public ApplyEffect apply(Object desiredState) {
      createDependentObject();
      ApplyEffect applyEffect = new ApplyEffect();
      _logger.info(" Start import ApplianceManagement");
      if (desiredState == null) {
         _logger
               .error(" Import config spec of Appliance Management " + "plugin is null");
         applyEffect.setStatus(ConfigTypes.ApplyStatus.ERROR);
         return applyEffect;
      }
      if (!(desiredState instanceof ApplianceManagement)) {
         _logger.error(" Import config spec of Appliance Management plugin "
               + "is not instance of ApplianceManagement class.");
         applyEffect.setStatus(ConfigTypes.ApplyStatus.ERROR);
         return applyEffect;
      }
      ApplianceManagement applianceManagementSpec = (ApplianceManagement) desiredState;
      importConfigValueFromVmodl.setConfigSpecObject(applianceManagementSpec);
      JsonObject dataSourceJsonObject =
            invokeDataSourceApi.getDataSourceFile(DATA_SOURCE_FILE);
      JsonArray dataSourceDetailsArray =
            dataSourceJsonObject.get(DATA_SOURCE_DETAIL).getAsJsonArray();
      for (int index = 0; index < dataSourceDetailsArray.size(); index++) {
         JsonObject dataSourceDetailObject =
               dataSourceDetailsArray.get(index).getAsJsonObject();
         importConfigValueFromVmodl.importConfigValueFromVmodl(dataSourceDetailObject);
      }
      _logger.info(" Complete import ApplianceManagement");
      applyEffect.setStatus(ConfigTypes.ApplyStatus.SUCCESS);
      return applyEffect;
   }

   /**
    * This method will validate desire state configuration.
    *
    * @param desiredState
    *           : The desired state
    * @return : ValidationResult.
    */

   public ValidationResult validate(Object desiredState) {
      _logger.info(
            "Started the validation of ApplianceManagement" + desiredState.toString());

      Impact impact = new Impact();
      Map<ServiceType, Set<String>> services = new HashMap<>();
      Set<String> systemdSet = new HashSet<>();
      ValidationResult validationRes = new ValidationResult();
      List<Notification> errorNotifications = new ArrayList<Notification>();
      ConfigValidationLogic configValidationLogic = new ConfigValidationLogic();

      ApplianceManagement applianceManagement = (ApplianceManagement) desiredState;
      List<BackupSchedule> backupSchedules = applianceManagement.getBackupSchedules();

      if (backupSchedules != null) {
         List<Notification> backupScheduleNotification =
               configValidationLogic.validateBackupSchedule(backupSchedules);
         if (CollectionUtils.isNotEmpty(backupScheduleNotification)) {
            errorNotifications.addAll(backupScheduleNotification);
            _logger.info("BackupSchedule Notofications"
                  + backupScheduleNotification.toString());
         }
      }

      LocalAccounts localAccounts = applianceManagement.getRootLocalAccount();
      if (localAccounts != null) {
         List<Notification> localAccountsNotification =
               ConfigValidationLogic.validateLocalAccounts(localAccounts);
         if (CollectionUtils.isNotEmpty(localAccountsNotification)) {
            errorNotifications.addAll(localAccountsNotification);
            _logger.info(
                  "Local Accounts Notofications" + localAccountsNotification.toString());
         } else {
            systemdSet.add(SENDMAIL);
         }
      }
      List<LogForwarding> forwardingList = applianceManagement.getSyslog();
      if (forwardingList != null) {
         List<Notification> forwardingNotification =
               ConfigValidationLogic.validateForwarding(forwardingList);
         if (CollectionUtils.isNotEmpty(forwardingNotification)) {
            errorNotifications.addAll(forwardingNotification);
            _logger.info("Syslog Notofications" + forwardingNotification.toString());

         } else {
            systemdSet.add(RSYSLOG);
         }
      }
      if (!systemdSet.isEmpty()) {
         services.put(ServiceType.SYSTEMD, systemdSet);
         impact.setServices(services);
         impact.setApplyImpact(ApplyImpact.RESTART_SERVICE);
         validationRes.setImpact(impact);
      } else {
         impact.setApplyImpact(ApplyImpact.NO_IMPACT);
         validationRes.setImpact(impact);
      }
      Notifications notifications = new Notifications();
      _logger.info("Error Notofications" + errorNotifications.toString());
      if (!errorNotifications.isEmpty()) {
         notifications.setErrors(errorNotifications);
         validationRes.setStatus(StatusType.INVALID);
         validationRes.setNotifications(notifications);
         _logger.info("Validation Result" + validationRes.toString());
         return validationRes;
      }

      //TODO : warnings

      List<Notification> infoNotifications = new ArrayList<Notification>();
      infoNotifications = NotificationUtil.createSoftwareNotificationList(APPLMGMT_VALID,
            MESSAGE, NO_RESOLUTION);
      validationRes.setStatus(StatusType.VALID);
      notifications.setInfo(infoNotifications);
      validationRes.setNotifications(notifications);
      _logger.info("Validation Result" + validationRes.toString());
      return validationRes;

   }


   public void unregister() {

   }


   private void createDependentObject() {
      if (applianceManagementSpec == null) {
         applianceManagementSpec = new ApplianceManagement();
         invokeDataSourceApi = new InvokeDataSourceApi(applianceManagementSpec);
         importConfigValueFromVmodl = new ImportConfigValue();
      }
   }

   /**
    * This method will export config properties for sendmail (SMTP).
    */
   private void exportSendmail(ApplianceManagement applianceManagementSpec) {


      Map<String, Object> configExportMap = new LinkedHashMap<>();
      String serverLine = null;
      String portrLine = null;
      BufferedReader br = null;
      try {
         br = new BufferedReader(new FileReader(SEND_MAIL));
         String line;
         Smtp smtp = new Smtp();
         while ((line = br.readLine()) != null) {
            if (line.startsWith(serverWord)) {
               serverLine = line;
               String serverVal = null;
               if (serverLine.length() > 2)
                  serverVal =
                        (String) serverLine.subSequence(3, serverLine.length() - 1);
               smtp.setMailServer(serverVal);
            }
            if (line.startsWith(portWord)) {
               br.readLine();
               portrLine = br.readLine();
               String[] portList = portrLine.trim().split(" ");
               String portrVal = null;
               if (portList.length > 2) {
                  portrVal = portList[2];
                  smtp.setRelayPort(portrVal);
               }
            }
         }
         br.close();
         //applianceManagementSpec.setSmtp(smtp);
      } catch (IOException e) {
         _logger.error(String.format("Exception while exporting SMTP Conf %s", e));
      } finally {
         if (br != null) {
            try {
               br.close();
            } catch (IOException e) {
               _logger.error(String.format("Exception while exporting SMTP Conf %s", e));
            }
         }
      }
   }
}
