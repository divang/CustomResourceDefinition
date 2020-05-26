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
import org.json.JSONException;
import org.json.JSONObject;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.vmware.appliance.vcenter.settings.config.components.applmgmt.ApplianceNetwork;
import com.vmware.appliance.vcenter.settings.config.components.applmgmt.BackupSchedule;
import com.vmware.appliance.vcenter.settings.config.components.applmgmt.LocalAccounts;
import com.vmware.appliance.vcenter.settings.config.components.applmgmt.LogForwarding;
import com.vmware.appliance.vcenter.settings.config.components.applmgmt.Smtp;

/**
 * VC-Profile plugin of Appliance Management service. This class will provide current configs of
 * appliance. This class will validate and apply the desire state of appliance service.
 */
public class AppliancePlugin {
   public static Log _logger = LogFactory.getLog(AppliancePlugin.class);

   private static final String DATA_SOURCE_DETAIL = "DATA_SOURCE_DETAIL";
   private static final String SEND_MAIL = "/etc/mail/sendmail.cf";
   private static final String serverWord = "DS";
   private static final String portWord = "Mrelay";
   private static final String SENDMAIL = "sendmail";
   private static final String RSYSLOG = "rsyslog";
   private static final String NETWORK_DATA_SOURCE_FILE =
         "ApplianceNetworkDataSource.json";
   private static final String APPLIANCE_DATA_SOURCE_FILE =
         "ApplianceManagementDataSource.json";

   private NetworkOperation networkOperation;
   private ObjectMapper mapper = new ObjectMapper();

   /**
    * Constructor will instantiate Appliance Vmodl spec and InvokeDataSourceApi object.
    */
   public AppliancePlugin() {
      networkOperation = new NetworkOperation();
   }

   public void setNetworkOperation(NetworkOperation networkOperation) {
      this.networkOperation = networkOperation;
   }

   /**
    * This method will collect and return current configuration of Appliance.
    * 
    * @return : Current configuration of Appliance in VC.
    */

   public Object getCurrentDesiredState() {
      _logger.info("Start get current config Appliance");

      ApplianceManagement applianceManagementSpec = new ApplianceManagement();
      InvokeDataSourceApi invokeDataSourceApi =
            new InvokeDataSourceApi(applianceManagementSpec);

      JsonObject dataSourceJsonObject =
            invokeDataSourceApi.getDataSourceFile(APPLIANCE_DATA_SOURCE_FILE);
      if (dataSourceJsonObject == null) {
         String error_message = String.format("DataSource file not found:" + " %s",
               APPLIANCE_DATA_SOURCE_FILE);
         _logger.error(error_message);
         throw new RuntimeException(error_message);
      }
      JsonArray dataSourceDetailsArray =
            dataSourceJsonObject.get(DATA_SOURCE_DETAIL).getAsJsonArray();
      _logger.debug("Got dataSourceJsonObject Appliance");
      for (int index = 0; index < dataSourceDetailsArray.size(); index++) {
         JsonObject dataSourceDetailObject =
               dataSourceDetailsArray.get(index).getAsJsonObject();
         invokeDataSourceApi.invokeApi(dataSourceDetailObject);
      }
      _logger.info("Appliance invokeDataSourceApi complete");
      getConfigSendmail(applianceManagementSpec);
      ApplianceNetwork network = networkOperation.getCurrentDesiredState();
      applianceManagementSpec.setNetwork(network);
      _logger.debug(String.format("Final Plugin Spec: %s", applianceManagementSpec));

      return applianceManagementSpec;
   }


   public Object getDesiredState() {
      return getCurrentDesiredState();
   }

   /**
    * This method will write the desire state configuration in vCenter.
    * 
    * @param desiredState:
    *           The desired state
    * @return : Apply effect on vCenter.
    */
   ObjectMapper objectMapper = new ObjectMapper();

   public ApplyEffect apply(Object desiredState) {
      ImportConfigValue importConfigValueFromVmodl = new ImportConfigValue();
      ApplyEffect applyEffect = new ApplyEffect();
      _logger.info("Start apply ApplianceManagement");
      if (desiredState == null) {
         _logger.error("Apply config spec of Appliance Management " + "plugin is null");
         applyEffect.setStatus(ConfigTypes.ApplyStatus.ERROR);
         return applyEffect;
      }
      /*
      if (!(desiredState instanceof ApplianceManagement)) {
         String error_message = "Apply config spec of Appliance "
               + "plugin is not instance of ApplianceManagement class.";
         _logger.error(error_message);
         throw new RuntimeException("error_message");
      }
       */
      JSONObject jo = mapper.convertValue(desiredState, JSONObject.class);

      try {
         _logger.info("Converting Json Object: " + jo.getString("appliance"));

         ApplianceManagement applianceManagementSpec = objectMapper
               .convertValue(jo.getString("appliance"), ApplianceManagement.class);
         _logger.info("Converted Json Object");
         InvokeDataSourceApi invokeDataSourceApi =
               new InvokeDataSourceApi(applianceManagementSpec);
         importConfigValueFromVmodl.setConfigSpecObject(applianceManagementSpec);
         JsonObject dataSourceJsonObject =
               invokeDataSourceApi.getDataSourceFile(APPLIANCE_DATA_SOURCE_FILE);
         JsonArray dataSourceDetailsArray =
               dataSourceJsonObject.get(DATA_SOURCE_DETAIL).getAsJsonArray();
         for (int index = 0; index < dataSourceDetailsArray.size(); index++) {
            JsonObject dataSourceDetailObject =
                  dataSourceDetailsArray.get(index).getAsJsonObject();
            importConfigValueFromVmodl
                  .importConfigValueFromVmodl(dataSourceDetailObject);
         }
         _logger.info("Complete apply Appliance");
         networkOperation.apply(applianceManagementSpec.getNetwork(), applyEffect);
         applyEffect.setStatus(ConfigTypes.ApplyStatus.SUCCESS);
      } catch (JSONException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
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
      _logger.info(String.format("Started the validation of ApplianceManagement",
            desiredState.toString()));
      ValidationResult validationRes = new ValidationResult();
      if (desiredState == null) {
         _logger.error(
               "Validate config spec of Appliance Management " + "plugin is null");
         validationRes.setStatus(StatusType.INVALID);
         return validationRes;
      }
      if (!(desiredState instanceof ApplianceManagement)) {
         String error_message = "Validate config spec of Appliance Management "
               + "plugin is not instance of ApplianceManagement class.";
         _logger.error(error_message);
         throw new RuntimeException(error_message);
      }

      Impact impact = new Impact();
      Map<ServiceType, Set<String>> services = new HashMap<>();
      Set<String> systemdSet = new HashSet<>();
      List<Notification> errorNotifications = new ArrayList<Notification>();
      ConfigValidationLogic configValidationLogic = new ConfigValidationLogic();

      ApplianceManagement applianceManagement = (ApplianceManagement) desiredState;
      List<BackupSchedule> backupSchedules = applianceManagement.getBackupSchedules();

      if (backupSchedules != null) {
         List<Notification> backupScheduleNotification =
               configValidationLogic.validateBackupSchedule(backupSchedules);
         if (CollectionUtils.isNotEmpty(backupScheduleNotification)) {
            errorNotifications.addAll(backupScheduleNotification);
            _logger.debug(String.format("BackupSchedule Notification %s"
                  + backupScheduleNotification.toString()));
         }
      }

      LocalAccounts localAccounts = applianceManagement.getRootLocalAccount();
      if (localAccounts != null) {
         List<Notification> localAccountsNotification =
               ConfigValidationLogic.validateLocalAccounts(localAccounts);
         if (CollectionUtils.isNotEmpty(localAccountsNotification)) {
            errorNotifications.addAll(localAccountsNotification);
            _logger.debug(String.format("Local Accounts Notofications %s"
                  + localAccountsNotification.toString()));
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
            _logger.debug(String.format("Syslog Notofications %s",
                  forwardingNotification.toString()));
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
      networkOperation.validate(applianceManagement.getNetwork(), errorNotifications);
      Notifications notifications = new Notifications();
      _logger.debug(
            String.format("Error Notification %s", errorNotifications.toString()));
      if (!errorNotifications.isEmpty()) {
         notifications.setErrors(errorNotifications);
         validationRes.setStatus(StatusType.INVALID);
         validationRes.setNotifications(notifications);
         _logger.debug(String.format("Validation Result %s", validationRes.toString()));
         return validationRes;
      }

      //TODO : warnings

      List<Notification> infoNotifications = new ArrayList<Notification>();
      infoNotifications = NotificationUtil.createSoftwareNotificationList(APPLMGMT_VALID,
            MESSAGE, NO_RESOLUTION);
      validationRes.setStatus(StatusType.VALID);
      notifications.setInfo(infoNotifications);
      validationRes.setNotifications(notifications);
      _logger.info(
            String.format("Validation Result appliance %s", validationRes.toString()));
      return validationRes;
   }


   public void unregister() {

   }

   /**
    * This method will get current configs of sendmail (SMTP).
    */
   private void getConfigSendmail(ApplianceManagement applianceManagementSpec) {
      Map<String, Object> configExportMap = new LinkedHashMap<>();
      String serverLine = null;
      String portrLine = null;
      try (BufferedReader br = new BufferedReader(new FileReader(SEND_MAIL))) {
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
         applianceManagementSpec.setSmtp(smtp);
      } catch (IOException e) {
         _logger.error(String.format(
               "Exception while getting current config of " + "SMTP Conf %s", e));
      }
   }
}
