/* ************************************************************************
 * Copyright 2020 VMware, Inc.  All rights reserved. -- VMware Confidential
 * ************************************************************************/
package com.vmware.ipm.plugins;

import static com.vmware.appliance.infraprofile.commons.Constants.CHAR_ENCODING;
import static com.vmware.appliance.infraprofile.commons.Constants.REGEX_RETAIN_WHITESPACE;
import static com.vmware.appliance.infraprofile.commons.Constants.REGEX_SPACES;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.vmware.appliance.infraprofile.commons.Constants;
import com.vmware.appliance.infraprofile.util.MiscUtils;

/**
 * This class is responsible to export and import those configuration file of Appliance Management
 * whose format is not generic type. These files required specific logic to parse, read and write.
 */
class ApplianceManagementNonGenericFiles {

   private String _pluginName;
   private static Log _logger;
   private List<StringBuilder> _fileContents;

   private static final String MAXIMUM_DAYS = "maximumDays";
   private static final String WARNING_DAYS = "warningDays";
   private static final String CONFIG_DATA_DELIMITER = ":";
   private static final char COMMENT = '#';
   private static final char OPEN_SQUARE_BRACKET = '[';
   private static final char CLOSE_SQUARE_BRACKET = ']';
   private static final char EQUALS_CHAR = '=';
   private static final String EMPTY_STRING = "";
   private static final String SERVER = "server";
   private static final char SPACE_CHAR = ' ';
   private static final String SSH_SERVICE = "sshd.service";
   private static final String SSH_ENABLED = "enabled";
   private static final String serverWord = "DS";
   private static final String portWord = "Mrelay";
   private static final String MAIL_SERVER = "mail_server";
   private static final String RELAY_PORT = "relay_port";
   private static final String SYSLOG_SERVER = "Server_";
   private static final String SYSLOG_PORT = "Port_";
   private static final String SYSLOG_PROTOCOL = "Protocol_";
   private static final String SYSLOG_TEMP_JSON =
         "/usr/lib/vmware-infraprofile/scripts/syslog_outpot.json";
   private static final String SYSLOG_DATA_SCRIPT =
         "/usr/lib/vmware-infraprofile/scripts/syslog_data.py";
   private static final String RSYSLOG_TEMP_JSON =
         "/usr/lib/vmware-infraprofile/scripts/syslog_outpot.json";
   private static final String PAM_TALLY_SO = "pam_tally2.so";
   private static final String EVEN_DENY_ROOT = "even_deny_root";

   /**
    * @param pluginName
    *           : Name of plugin
    */
   ApplianceManagementNonGenericFiles(String pluginName) {
      this._pluginName = pluginName;
      _logger = LogFactory.getLog(ApplianceManagementNonGenericFiles.class);
      _fileContents = new ArrayList<>();
   }

   /**
    * This ENUM will define all configuration files whose format is not generic type .
    */
   private enum CONFIGURATION_FILES {
      SHADOW("/etc/shadow"), SYSTEM_AUTH("/etc/pam.d/system-auth"), NTP(
            "/etc/ntp.conf"), SSHD("/usr/bin/systemctl/sshd.service"), SMTP_CONF(
                  "/etc/mail/sendmail.cf"), RSYSLOG("/etc/vmware-syslog/syslog.conf");

      private String filePath;

      CONFIGURATION_FILES(String filePath) {
         this.filePath = filePath;
      }

      public String getFilePath() {
         return filePath;
      }

      public static Stream<CONFIGURATION_FILES> stream() {
         return Stream.of(CONFIGURATION_FILES.values());
      }
   }

   /**
    * Export configuration properties from configuration files.
    *
    * @param fileName
    *           : Absolute file name
    * @param configKeys
    *           : Configuration keys to export.
    * @return : Map : contains config data in key,value format.
    */
   Map<String, Object> export(String fileName, List<String> configKeys) {

      CONFIGURATION_FILES enumfileName = null;
      Map<String, Object> exportConfigurations = new LinkedHashMap<>();

      try {
         enumfileName = CONFIGURATION_FILES.stream()
               .filter(cf -> cf.getFilePath().equals(fileName)).findAny().get();

         switch (enumfileName) {
         case SHADOW:
            exportConfigurations = exportShadowFile(fileName, configKeys);
            break;
         case SYSTEM_AUTH:
            exportConfigurations = exportPasswordPolicyFile(fileName, configKeys);
            break;
         case NTP:
            exportConfigurations = exportNtpConf(fileName, configKeys);
            break;
         case SSHD:
            exportConfigurations = exportSshd();
            break;
         case SMTP_CONF:
            exportConfigurations = exportSendmail();
            break;
         case RSYSLOG:
            exportConfigurations = exportRsyslog();
            break;
         }
      } catch (IllegalArgumentException | NoSuchElementException e) {
         _logger.error(
               String.format("Unsupported configuration " + "source file: %s", fileName),
               e);
      }

      _logger.debug(String.format("Export config of file %s: %s ", fileName,
            exportConfigurations.toString()));

      return exportConfigurations;
   }

   /**
    * Import given input configuration properties to current VC.
    *
    * @param fileName
    *           : Absolute configuration file name
    * @param updatedConfigValues
    *           : list of config to update configuration file.
    * @return : Configuration file updated successfully or not.
    */
   boolean importConfig(String fileName, Map<String, Object> updatedConfigValues) {

      CONFIGURATION_FILES enumfileName = null;
      boolean importConfigSuccess = false;

      try {
         enumfileName = CONFIGURATION_FILES.stream()
               .filter(cf -> cf.getFilePath().equals(fileName)).findAny().get();

         switch (enumfileName) {
         case SHADOW:
            importConfigSuccess = importShadowFile(fileName, updatedConfigValues);
            break;
         case SYSTEM_AUTH:
            importConfigSuccess =
                  importPasswordPolicyFile(fileName, updatedConfigValues);
            break;
         case NTP:
            importConfigSuccess = importNtpConf(fileName, updatedConfigValues);
            break;
         case SSHD:
            importConfigSuccess = importSshd(updatedConfigValues);
            break;
         case SMTP_CONF:
            importConfigSuccess = importSendmail(updatedConfigValues);
            break;
         case RSYSLOG:
            importConfigSuccess = importRsyslog(updatedConfigValues);
            break;
         }
      } catch (IllegalArgumentException | NoSuchElementException e) {
         _logger.error(
               String.format("Unsupported configuration " + "source file: %s", fileName),
               e);
      }

      return importConfigSuccess;
   }

   /**
    * Export config values from shadow file.
    *
    * @param fileName
    *           : Absolute file name of shadow file.
    * @return : Shadow file updated successfully or not.
    */
   private Map<String, Object> exportShadowFile(String fileName,
         List<String> configKeys) {

      Map<String, Object> shadowConfigExportMap = new LinkedHashMap<>();

      try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
         stream.forEach(line -> {
            String[] lineToken = line.split(CONFIG_DATA_DELIMITER);
            Map<String, Object> configExportMap = new LinkedHashMap<>();
            /*
             position 1: Username: It is login name.
             Position 2: encrypted password
             Position 3: Last password change:Days since Jan 1, 1970 that
                         password was last changed
             Position 4: Minimum days:The minimum number of days required
                         between password changes i.e. the number of days left
                         before the user is allowed to change his/her password
             Position 5: Maximum days: he maximum number of days the password is
                         valid (after that user is forced to change his/her
                         password)
             Position 6: Warning days:The number of days before password is to
                         expire that user is warned that his/her password must
                         be changed
             Position 7: Inactive:The number of days after password expires that
                         account is disabled
             Position 8: Expire:days since Jan 1, 1970 that account is disabled
                         i.e. an absolute date specifying when the login may no
                         longer be used.
             */
            if (lineToken.length < 6) {
               _logger.error(String.format(
                     "%s line of file: %s does not contains no of" + " warning days ",
                     line, fileName));
               return;
            }
            configExportMap.put(MAXIMUM_DAYS, lineToken[4]);
            configExportMap.put(WARNING_DAYS, lineToken[5]);
            shadowConfigExportMap.put(lineToken[0], configExportMap);
         });

      } catch (IOException e) {
         _logger.error(String.format(
               "Exception while exporting configuration" + " file %s  of plugin %s",
               fileName, _pluginName), e);
         throw new RuntimeException(String.format(
               "Exception while exporting configuration" + " file %s  of plugin %s",
               fileName, _pluginName), e);
      }
      return shadowConfigExportMap;
   }

   /**
    * Import config values in shadow file.
    *
    * @param fileName
    *           : Absolute file name of shadow file.
    * @param updatedConfigValues
    *           : Config values to update.
    * @return
    */
   private boolean importShadowFile(String fileName,
         Map<String, Object> updatedConfigValues) {

      if (MapUtils.isEmpty(updatedConfigValues)) {
         _logger.info(String.format("No configuration to update in file %s ", fileName));
         return false;
      }
      _fileContents.clear();

      try (Stream<String> stream = Files.lines(Paths.get(fileName))) {

         stream.forEach(line -> {
            StringBuilder sb = new StringBuilder(line);

            //TODO : Right now ignoring the line without "warning days".
            //       In future, If such use case arise to import "warning days"
            //       even if it is not present , then will change the logic.
            if (line.split(CONFIG_DATA_DELIMITER).length < 6) {
               _logger.error(String.format("%s line of file: %s does not contains no of"
                     + " warning days , So not overwriting" + "", line, fileName));
               _fileContents.add(sb);
               return;
            }

            String userName = sb.substring(0, sb.indexOf(CONFIG_DATA_DELIMITER));
            String newMaximumDays = "";
            String newWarningDays = "";

            if (updatedConfigValues.containsKey(userName)) {
               Map<String, String> configMap =
                     (Map<String, String>) updatedConfigValues.get(userName);

               if (MapUtils.isNotEmpty(configMap)) {
                  newMaximumDays = configMap.get(MAXIMUM_DAYS);
                  newWarningDays = configMap.get(WARNING_DAYS);
               }
            }
            /*
            position 1: Username: It is login name.
            Position 2: encrypted password
            Position 3: Last password change:Days since Jan 1, 1970 that
                        password was last changed
            Position 4: Minimum days:The minimum number of days required
                        between password changes i.e. the number of days left
                        before the user is allowed to change his/her password
            Position 5: Maximum days: he maximum number of days the password is
                        valid (after that user is forced to change his/her
                        password)
            Position 6: Warning days:The number of days before password is to
                        expire that user is warned that his/her password must
                        be changed
            Position 7: Inactive:The number of days after password expires that
                        account is disabled
            Position 8: Expire:days since Jan 1, 1970 that account is disabled
                        i.e. an absolute date specifying when the login may no
                        longer be used.
            */
            int startIndex = StringUtils.ordinalIndexOf(line, CONFIG_DATA_DELIMITER, 4);
            int lastIndex = StringUtils.ordinalIndexOf(line, CONFIG_DATA_DELIMITER, 5);

            sb = sb.replace(startIndex + 1, lastIndex, newMaximumDays);

            startIndex =
                  StringUtils.ordinalIndexOf(sb.toString(), CONFIG_DATA_DELIMITER, 5);
            lastIndex =
                  StringUtils.ordinalIndexOf(sb.toString(), CONFIG_DATA_DELIMITER, 6);

            sb = sb.replace(startIndex + 1, lastIndex, newWarningDays);
            _fileContents.add(sb);
         });

      } catch (IOException e) {
         _logger.error(String.format(
               "Exception while importing configuration" + " file %s  of plugin %s",
               fileName, _pluginName), e);
         throw new RuntimeException(String.format(
               "Exception while importing configuration" + " file %s  of plugin %s",
               fileName, _pluginName), e);
      }

      writeFile(fileName);
      return true;
   }

   /**
    * This method will write contents of ArrayList _fileContents into given input file.
    *
    * @param fileName
    *           : Absolute file path to overwrite
    * @return : boolean : File got overridden successfully or not.
    */
   private void writeFile(String fileName) {
      Path path = Paths.get(fileName);
      try (BufferedWriter writer =
            Files.newBufferedWriter(path, Charset.forName(CHAR_ENCODING))) {

         _fileContents.stream().forEach(line -> {
            try {
               writer.write(line.toString());
               writer.newLine();
            } catch (IOException e) {
               _logger.error(
                     String.format("Exception while writing in file %s", fileName), e);
               throw new RuntimeException(
                     String.format("Exception while writing in file %s", fileName), e);
            }
         });

      } catch (IOException ex) {
         _logger.error(String.format("Exception while loading the file %s", fileName),
               ex);
         throw new RuntimeException(
               String.format("Exception while loading the file %s", fileName), ex);
      }
   }

   /**
    * This file will export config properties of system-auth file.
    *
    * @param fileName
    *           : system-auth absolute file name.
    * @param configKeys
    *           : List of properties to export.
    * @return
    */
   private Map<String, Object> exportPasswordPolicyFile(String fileName,
         List<String> configKeys) {
      Map<String, Object> configExportMap = new LinkedHashMap<>();

      try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
         stream.forEach(line -> {
            if (StringUtils.isEmpty(line)) {
               return;
            }
            /*
             Ignore the commented lines.
             */
            if (line.charAt(0) == COMMENT) {
               return;
            }
            String[] lineToken = line.split(REGEX_SPACES);
            try {
               Arrays.stream(lineToken).filter(token -> {
                  if (token.contains(String.valueOf(EQUALS_CHAR))) {
                     if (configKeys
                           .contains(token.substring(0, token.indexOf(EQUALS_CHAR)))) {
                        return true;
                     }
                     return false;
                  } else {
                     if (configKeys.contains(token)) {
                        return true;
                     }
                     return false;
                  }
               }).forEach(token -> {
                  if (token.contains(String.valueOf(EQUALS_CHAR))) {
                     configExportMap.put(token.substring(0, token.indexOf(EQUALS_CHAR)),
                           token.substring(token.indexOf(EQUALS_CHAR) + 1));
                  } else {
                     configExportMap.put(token, true);
                  }
               });
            } catch (IndexOutOfBoundsException e) {
               _logger.error(String.format(" %s line is not in correct format", line),
                     e);
            }
         });
      } catch (IOException e) {
         _logger.error(String.format(
               "Exception while exporting configuration" + " file %s  of plugin %s",
               fileName, _pluginName), e);
         throw new RuntimeException(String.format(
               "Exception while exporting configuration" + " file %s  of plugin %s",
               fileName, _pluginName), e);
      }
      return configExportMap;
   }

   /**
    * This method will import configuration in system-auth file
    *
    * @param fileName
    *           : system-auth absolute file path
    * @param updatedConfigProperties
    *           : Updated config properties value to import
    * @return : Import successful or not
    */
   private boolean importPasswordPolicyFile(String fileName,
         Map<String, Object> updatedConfigProperties) {
      if (MapUtils.isEmpty(updatedConfigProperties)) {
         _logger.info(String.format("No configuration to update in file %s ", fileName));
         return false;
      }
      _fileContents.clear();

      try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
         stream.forEach(line -> {
            /*
             Write empty line as it is.
             */
            if (StringUtils.isEmpty(line)) {
               _fileContents.add(new StringBuilder(line));
               return;
            }
            /*
             Write the commented line as it is.
             */
            if (line.charAt(0) == COMMENT) {
               _fileContents.add(new StringBuilder(line));
               return;
            }
            String[] lineTokens = line.split(REGEX_RETAIN_WHITESPACE);
            String updatedLine = Arrays.stream(lineTokens).map(token -> {
               if (StringUtils.isEmpty(token)) {
                  return token;
               }
               if (token.contains(String.valueOf(EQUALS_CHAR))) {
                  String[] keyValue = token.split(String.valueOf(EQUALS_CHAR));
                  if (updatedConfigProperties.containsKey(keyValue[0])) {
                     return keyValue[0] + EQUALS_CHAR
                           + updatedConfigProperties.get(keyValue[0]);
                  }
                  return token;
               } else {
                  if ((updatedConfigProperties.containsKey(token))
                        && (!(boolean) updatedConfigProperties.get(token))) {

                     return EMPTY_STRING;
                  }
                  //TODO : Here we need to add more generic logic to
                  // handle all keys wihtout any value.
                  if (token.equals(PAM_TALLY_SO) && !line.contains(EVEN_DENY_ROOT)
                        && (updatedConfigProperties.containsKey(EVEN_DENY_ROOT))
                        && ((boolean) updatedConfigProperties.get(EVEN_DENY_ROOT))) {
                     return token + " " + EVEN_DENY_ROOT;
                  }
                  return token;
               }
            }).collect(Collectors.joining());
            _fileContents.add(new StringBuilder(updatedLine));
         });
         _logger.debug(String.format("Importing config of file %s is : %s", fileName,
               _fileContents.toString()));
      } catch (IOException e) {
         _logger.error(String.format(
               "Exception while importing configuration" + " file %s  of plugin %s",
               fileName, _pluginName), e);
         throw new RuntimeException(String.format(
               "Exception while importing configuration" + " file %s  of plugin %s",
               fileName, _pluginName), e);
      }
      writeFile(fileName);
      return true;
   }

   /**
    * This method will export config of NTP.conf file
    *
    * @param fileName
    *           : NTP.Conf file absolute file path
    * @param configKeys
    *           : List of configuration properties to export
    * @return : Configuration properties values present in NTP.conf file
    */
   private Map<String, Object> exportNtpConf(String fileName, List<String> configKeys) {

      Map<String, Object> configExportMap = new LinkedHashMap<>();
      ArrayList<String> serverList = new ArrayList<>();

      if (!configKeys.contains(SERVER)) {
         _logger.info(String.format("No server properties key to export in file %s ",
               fileName));
         return configExportMap;
      }

      try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
         stream.forEach(line -> {
            if (StringUtils.isEmpty(line)) {
               return;
            }
            /*
             Ignore the commented lines.
             */
            if (line.charAt(0) == COMMENT) {
               return;
            }
            /*
             Ignore the line without server as key.
             */
            if (!line.startsWith(SERVER)) {
               return;
            }
            String[] lineToken = line.split(REGEX_SPACES);
            /*
            Ignore the line if server has no value.
            */
            if (lineToken.length != 2) {
               return;
            }

            serverList.add(lineToken[1]);
         });
      } catch (IOException e) {
         _logger.error(String.format(
               "Exception while exporting configuration" + " file %s  of plugin %s",
               fileName, _pluginName), e);
         throw new RuntimeException(String.format(
               "Exception while exporting configuration" + " file %s  of plugin %s",
               fileName, _pluginName), e);
      }
      configExportMap.put(SERVER, serverList);
      return configExportMap;
   }

   /**
    * This method will import configuration properties of NTP.conf file.
    *
    * @param fileName
    *           : NTP.Conf file absolute file path.
    * @param updatedConfigProperties
    *           : Updated config properties value to import
    * @return : Import completed successful or not.
    */
   private boolean importNtpConf(String fileName,
         Map<String, Object> updatedConfigProperties) {
      if (MapUtils.isEmpty(updatedConfigProperties)) {
         _logger.info(String.format("No configuration to update in file %s ", fileName));
         return false;
      }
      if (!updatedConfigProperties.containsKey(SERVER)) {
         _logger.info(String
               .format("No server configuration value to update in file %s ", fileName));
         return false;
      }
      _fileContents.clear();

      try (Stream<String> stream = Files.lines(Paths.get(fileName))) {
         AtomicInteger index = new AtomicInteger(0);
         stream.forEach(line -> {
            /*
            Write the empty line as it is.
             */
            if (StringUtils.isEmpty(line)) {
               _fileContents.add(new StringBuilder(line));
               return;
            }
            /*
             Write the comment line as it is.
             */
            if (line.charAt(0) == COMMENT) {
               _fileContents.add(new StringBuilder(line));
               return;
            }
            /*
            Write the line without server key as it is.
             */
            if (!line.startsWith(SERVER)) {
               _fileContents.add(new StringBuilder(line));
               return;
            }
            String[] lineToken = line.split(REGEX_SPACES);

            /*
             Write the line as it is if no value of server key.
             */
            if (lineToken.length != 2) {
               _fileContents.add(new StringBuilder(line));
               return;
            }
            List<String> serverValues =
                  (List<String>) updatedConfigProperties.get(SERVER);
            if (serverValues.size() > index.get()) {
               StringBuilder serverValue = new StringBuilder();
               serverValue.append(lineToken[0]);
               serverValue.append(SPACE_CHAR);
               serverValue.append(serverValues.get(index.getAndIncrement()));
               _fileContents.add(serverValue);
               return;
            }
            _fileContents.add(new StringBuilder(line));
         });
         _logger.debug(String.format("Importing config of file %s is : %s", fileName,
               _fileContents.toString()));

      } catch (IOException e) {
         _logger.error(String.format(
               "Exception while importing configuration" + " file %s  of plugin %s",
               fileName, _pluginName), e);
         throw new RuntimeException(String.format(
               "Exception while importing configuration" + " file %s  of plugin %s",
               fileName, _pluginName), e);
      }
      writeFile(fileName);
      return true;
   }

   /**
    * This method will export config properties for sshd.
    *
    * @return
    */
   private Map<String, Object> exportSshd() {
      Map<String, Object> configExportMap = new LinkedHashMap<>();
      boolean sshIsEnabled = false;

      _logger.error("Exporting sshd.service");
      try {
         String[] cmdEnable = { "systemctl", "is-enabled", "sshd" };
         String enable = MiscUtils.executeCmd(cmdEnable);
         _logger.error("sshd is" + enable);
         String[] cmdActive = { "systemctl", "is-active", "sshd" };
         String active = MiscUtils.executeCmd(cmdActive);
         _logger.error("sshd is " + active);

         if (enable.trim().compareToIgnoreCase("enabled") == 0
               && active.trim().compareToIgnoreCase("active") == 0) {
            sshIsEnabled = true;
            _logger.error("sshd service is enabled");
         }
      } catch (Exception ex) {
         _logger.error("Failed to get SSHD.service", ex);
      }
      configExportMap.put(SSH_ENABLED, String.valueOf(sshIsEnabled));

      return configExportMap;
   }

   /**
    * This method will import config properties for sshd.
    *
    * @return
    */
   private boolean importSshd(Map<String, Object> updatedConfigProperties) {
      boolean importedSuccess = false;

      if (updatedConfigProperties.get(SSH_ENABLED).equals("true")) {
         // Unmask the service unit file.
         MiscUtils.RunSystemService(Constants.SERVICE_OP.UNMASK,
               Arrays.<String> asList(SSH_SERVICE));
         // Enable the service.
         MiscUtils.RunSystemService(Constants.SERVICE_OP.ENABLE,
               Arrays.<String> asList(SSH_SERVICE));
         // Start the service.
         MiscUtils.RunSystemService(Constants.SERVICE_OP.START,
               Arrays.<String> asList(SSH_SERVICE));
         importedSuccess = true;
      } else if (updatedConfigProperties.get(SSH_ENABLED).equals("false")) {
         // Mask the service unit file.
         MiscUtils.RunSystemService(Constants.SERVICE_OP.MASK,
               Arrays.<String> asList(SSH_SERVICE));
         // Disable the service.
         MiscUtils.RunSystemService(Constants.SERVICE_OP.DISABLE,
               Arrays.<String> asList(SSH_SERVICE));
         // Stop the service.
         MiscUtils.RunSystemService(Constants.SERVICE_OP.STOP,
               Arrays.<String> asList(SSH_SERVICE));
         importedSuccess = true;
      }
      return importedSuccess;
   }

   /**
    * This method will export config properties for sendmail (SMTP).
    *
    * @return
    */
   private Map<String, Object> exportSendmail() {
      Map<String, Object> configExportMap = new LinkedHashMap<>();
      String serverLine = null;
      String portrLine = null;
      BufferedReader br;
      try {
         br = new BufferedReader(
               new FileReader(CONFIGURATION_FILES.SMTP_CONF.getFilePath()));
         String line;
         while ((line = br.readLine()) != null) {
            if (line.startsWith(serverWord)) {
               serverLine = line;
               String serverVal = null;
               if (serverLine.length() > 2)
                  serverVal =
                        (String) serverLine.subSequence(3, serverLine.length() - 1);
               configExportMap.put(MAIL_SERVER, serverVal);
            }
            if (line.startsWith(portWord)) {
               br.readLine();
               portrLine = br.readLine();
               String[] portList = portrLine.trim().split(" ");
               String portrVal = null;
               if (portList.length > 2) {
                  portrVal = portList[2];
                  configExportMap.put(RELAY_PORT, portrVal);
               }
            }
         }
         br.close();
      } catch (IOException e) {
         _logger.error(String.format("Exception while exporting SMTP Conf %s", e));
      }

      return configExportMap;
   }

   /**
    * This method will import config properties for sendmail (SMTP).
    *
    * @return
    */
   private boolean importSendmail(Map<String, Object> updatedConfigProperties) {
      boolean importedSuccess = true;
      String smtpServer = (String) updatedConfigProperties.get(MAIL_SERVER);
      String smtpPort = (String) updatedConfigProperties.get(RELAY_PORT);

      if (smtpServer == null && smtpPort == null)
         return importedSuccess;

      _fileContents.clear();
      BufferedReader br;
      try {
         br = new BufferedReader(
               new FileReader(CONFIGURATION_FILES.SMTP_CONF.getFilePath()));
         String line;
         while ((line = br.readLine()) != null) {
            if (line.startsWith(serverWord) && smtpServer != null) {
               line = "DS[" + smtpServer + "]";
            } else if (line.startsWith(portWord) && smtpPort != null) {
               _fileContents.add(new StringBuilder(line));
               _fileContents.add(new StringBuilder(br.readLine()));
               line = br.readLine();
               String[] portList = line.trim().split(" ");
               line = "\t\t" + portList[0] + " " + portList[1] + " " + smtpPort;
            }
            _fileContents.add(new StringBuilder(line));
         }
         br.close();
         writeFile(CONFIGURATION_FILES.SMTP_CONF.getFilePath());
      } catch (IOException e) {
         _logger.error(String.format("Exception while importing SMTP Conf %s", e));
         importedSuccess = false;
      }

      return importedSuccess;
   }

   /**
    * This method will export config properties for rsyslog.
    *
    * @return
    */
   private Map<String, Object> exportRsyslog() {
      Map<String, Object> configExportMap = new LinkedHashMap<>();
      String[] rsyslogExport =
            { "python", SYSLOG_DATA_SCRIPT, "export", RSYSLOG_TEMP_JSON };
      MiscUtils.executeCmd(rsyslogExport);

      try {
         Gson gson = new Gson();
         int index = 0;
         BufferedReader br = new BufferedReader(new FileReader(SYSLOG_TEMP_JSON));
         if (br == null) {
            _logger.error("Buffer Reader is Null, " + "returning empty configuration");
            return configExportMap;
         }
         RsyslogData[] rsyslogData = gson.fromJson(br, RsyslogData[].class);
         for (RsyslogData eachConf : rsyslogData) {
            configExportMap.put(SYSLOG_SERVER + Integer.toString(index),
                  eachConf.getHostname());
            configExportMap.put(SYSLOG_PORT + Integer.toString(index),
                  eachConf.getPort());
            configExportMap.put(SYSLOG_PROTOCOL + Integer.toString(index),
                  eachConf.getProtocol());
            index++;
         }
      } catch (Exception e) {
         _logger.error(String.format("Exception while exporting Rsyslog Conf", e));
      }
      return configExportMap;
   }

   /**
    * This method will import config properties for rsyslog.
    *
    * @return
    */
   private boolean importRsyslog(Map<String, Object> updatedConfigProperties) {
      boolean importedSuccess = true;

      try {
         ArrayList<RsyslogData> rsyslogData = new ArrayList<RsyslogData>();
         if (updatedConfigProperties.size() % 3 != 0) {
            _logger.error(String.format("Updated config data is malformd."));
            return false;
         }

         for (int index = 0; index < 3; index++) {
            RsyslogData eachData = new RsyslogData();
            if (updatedConfigProperties
                  .get(SYSLOG_SERVER + Integer.toString(index)) == null)
               continue;
            eachData.setHostname((String) updatedConfigProperties
                  .get(SYSLOG_SERVER + Integer.toString(index)));

            if (updatedConfigProperties
                  .get(SYSLOG_PORT + Integer.toString(index)) == null)
               continue;
            eachData.setPort((String) updatedConfigProperties
                  .get(SYSLOG_PORT + Integer.toString(index)));

            if (updatedConfigProperties
                  .get(SYSLOG_PROTOCOL + Integer.toString(index)) == null)
               continue;
            eachData.setProtocol((String) updatedConfigProperties
                  .get(SYSLOG_PROTOCOL + Integer.toString(index)));

            rsyslogData.add(eachData);
         }

         RsyslogData[] rsyslogDataarr =
               rsyslogData.toArray(new RsyslogData[rsyslogData.size()]);
         Gson gson = new GsonBuilder().setPrettyPrinting().create();
         String jsonString = gson.toJson(rsyslogDataarr);
         FileWriter fileWriter;

         fileWriter = new FileWriter(SYSLOG_TEMP_JSON);
         fileWriter.write(jsonString);
         fileWriter.close();
      } catch (Exception e) {
         _logger.error(String.format("Exception while importing Rsyslog Conf", e));
         importedSuccess = false;
      }

      String[] rsyslogImport =
            { "python", SYSLOG_DATA_SCRIPT, "import", RSYSLOG_TEMP_JSON };
      MiscUtils.executeCmd(rsyslogImport);

      return importedSuccess;
   }

   /**
    * This class is contains Configurations for Rsyslog.
    */
   class RsyslogData {
      private String hostname;
      private String port;
      private String protocol;

      public RsyslogData() {
         super();
      }

      public RsyslogData(String hostname, String port, String protocol) {
         super();
         hostname = this.hostname;
         port = this.port;
         protocol = this.protocol;
      }

      public String getHostname() {
         return hostname;
      }

      public void setHostname(String hostname) {
         this.hostname = hostname;
      }

      public String getPort() {
         return port;
      }

      public void setPort(String port) {
         this.port = port;
      }

      public String getProtocol() {
         return protocol;
      }

      public void setProtocol(String protocol) {
         this.protocol = protocol;
      }
   }
}
