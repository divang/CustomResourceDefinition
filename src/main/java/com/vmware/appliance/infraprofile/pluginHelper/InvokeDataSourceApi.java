/* ************************************************************************
 * Copyright 2020 VMware, Inc.  All rights reserved. -- VMware Confidential
 * ************************************************************************/
package com.vmware.appliance.infraprofile.pluginHelper;

import static com.vmware.appliance.infraprofile.commons.Constants.API;
import static com.vmware.appliance.infraprofile.commons.Constants.ARRAY_ZERO_INDEX;
import static com.vmware.appliance.infraprofile.commons.Constants.DATA_SOURCE;
import static com.vmware.appliance.infraprofile.commons.Constants.EXPORT_PATH;
import static com.vmware.appliance.infraprofile.commons.Constants.ExportAPI;
import static com.vmware.appliance.infraprofile.commons.Constants.PLUGIN_DATA_SOURCE_FILE_FOLDER;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.InvalidPathException;
import com.jayway.jsonpath.JsonPath;

/**
 * This class provide functionality to invoke any API present in Data source meta file of components
 * and will return API response in Map format.
 */
public class InvokeDataSourceApi {

   private DocumentContext jsonContext;
   private Object componentSpec;
   private static Log _logger = LogFactory.getLog(InvokeDataSourceApi.class);
   private static JsonParser jsonParser;
   private StoreConfigValuesInVmodl storeConfigValuesInVmodl;
   private RestAPIInvoker restAPIInvoker;

   public InvokeDataSourceApi(Object componentSpec) {
      this.componentSpec = componentSpec;
      this.storeConfigValuesInVmodl = new StoreConfigValuesInVmodl();
      jsonParser = new JsonParser();
      restAPIInvoker = new RestAPIInvoker();
   }

   public RestAPIInvoker getRestAPIInvoker() {

      return restAPIInvoker;
   }

   public void setStoreConfigValuesInVmodl(
         StoreConfigValuesInVmodl storeConfigValuesInVmodl) {
      this.storeConfigValuesInVmodl = storeConfigValuesInVmodl;
   }

   public void setRestAPIInvoker(RestAPIInvoker restAPIInvoker) {
      this.restAPIInvoker = restAPIInvoker;
   }

   /**
    * This method will invoke API and fetch their response.
    *
    * @param dataSourceDetailObject
    *           : Data Source object which contains API path as well as list of configuration that
    *           API response will provide.
    */
   public void invokeApi(JsonObject dataSourceDetailObject) {
      JsonObject apiDetailsObject = null;
      String exportApi = null;

      if (dataSourceDetailObject == null) {
         _logger.error("Data Source detail is empty");
         return;
      }

      try {
         apiDetailsObject = dataSourceDetailObject.get(DATA_SOURCE).getAsJsonObject()
               .get(ExportAPI).getAsJsonObject();
         exportApi = apiDetailsObject.get(API).getAsString();
      } catch (NullPointerException e) {
         String errorMessage =
               "Data source Json does not contains Export API " + dataSourceDetailObject;
         _logger.error(errorMessage, e);
         throw new RuntimeException(errorMessage, e);
      }

      _logger.info(" Start executing API :" + exportApi);
      String exportAPIOutput = null;

      try {
         exportAPIOutput = restAPIInvoker.invokeRestAPI(exportApi, "GET", null);
      } catch (Exception e) {
         _logger.error(String.format("Export API %s Execution fail", exportApi), e);
         throw new RuntimeException(
               String.format("Export API %s " + "Execution fail", exportApi), e);
      }

      jsonContext = JsonPath.parse(exportAPIOutput);

      _logger.info("API Response :" + exportAPIOutput);

      Map<String, List<Map<String, Object>>> groupConfigKeyValueMap =
            collectConfigValuesFromAPIResponse(dataSourceDetailObject);
      _logger.debug("API response collected in Map " + groupConfigKeyValueMap);

      storeConfigValuesInVmodl.storeInVmodlSpec(componentSpec, groupConfigKeyValueMap);
   }

   /**
    * This method will fetch all config value provide by API response.
    *
    * @param dataSourceDetailObject:
    *           Data Source object which contains API list of configuration value API response will
    *           provide.
    * @return
    */
   private Map<String, List<Map<String, Object>>> collectConfigValuesFromAPIResponse(
         JsonObject dataSourceDetailObject) {
      Map<String, List<Map<String, Object>>> groupConfigMap = new HashMap<>();
      dataSourceDetailObject.keySet().forEach((groupName) -> {
         if (groupName.equals(DATA_SOURCE)) {
            return;
         }

         _logger.info(String.format(
               "Start retrieving values of group %s " + "from API response", groupName));

         JsonObject configDetailsObject =
               dataSourceDetailObject.get(groupName).getAsJsonObject();
         List<Map<String, Object>> listConfigDetails = new ArrayList();
         AtomicInteger arrayIndex = new AtomicInteger();
         AtomicBoolean moreArrayIndex = new AtomicBoolean();
         do {
            Map<String, Object> configKeyValueMap = new HashMap<>();
            moreArrayIndex.set(false);
            configDetailsObject.keySet().forEach((configKeyName) -> {
               String exportPath = configDetailsObject.get(configKeyName)
                     .getAsJsonObject().get(EXPORT_PATH).getAsString();

               _logger.info("exportPath :" + exportPath);

               String nextExportPath = exportPath;

               if (exportPath.contains(ARRAY_ZERO_INDEX)) {
                  StringBuilder nextIndex = new StringBuilder();
                  nextIndex.append("[");
                  nextIndex.append(arrayIndex.get());
                  nextIndex.append("]");
                  nextExportPath =
                        nextExportPath.replace(ARRAY_ZERO_INDEX, nextIndex.toString());
                  _logger.info("Array exportPath :" + nextExportPath);
               }

               Object configValue = getConfigValue(nextExportPath);
               if (configValue == null) {
                  _logger.info(String.format(
                        "No config value found for  " + "configKeyName %s)",
                        configKeyName));
                  return;
               }
               configKeyValueMap.put(configKeyName, configValue);
               if (exportPath.contains(ARRAY_ZERO_INDEX)) {
                  moreArrayIndex.set(true);
               }
            });

            if (configKeyValueMap.size() > 0) {
               listConfigDetails.add(configKeyValueMap);
            }
            arrayIndex.getAndIncrement();
         } while (moreArrayIndex.get());

         groupConfigMap.put(groupName, listConfigDetails);
         _logger.info(String.format("Config values  %s of group %s ", listConfigDetails,
               groupName));
      });
      return groupConfigMap;
   }

   /**
    * This Method will return config value from API response.
    *
    * @param configPath
    *           : JSON path in API response
    * @return
    */
   private Object getConfigValue(String configPath) {
      try {
         return jsonContext.read(configPath).toString();
      } catch (InvalidPathException | NullPointerException e) {
         _logger.error(String.format("Invalid JSON path %s", configPath), e);
         return null;
      }
   }

   /**
    * This method will return plugin data source file contains in Json Object.
    *
    * @param fileName
    *           : Plugin data source file name.
    * @return
    */
   public JsonObject getDataSourceFile(String fileName) {
      final JsonObject[] dataSourceJsonObject = new JsonObject[1];
      try (Stream<Path> paths = Files.walk(Paths.get(PLUGIN_DATA_SOURCE_FILE_FOLDER))) {
         paths.filter(Files::isRegularFile).filter(Files -> {
            return Files.getFileName().toString().equals(fileName);
         }).forEach(dataSourceFile -> {
            _logger.info("Start reading file: " + dataSourceFile.toString());

            try {
               try (JsonReader reader =
                     new JsonReader(new FileReader(dataSourceFile.toString()))) {
                  dataSourceJsonObject[0] = jsonParser.parse(reader).getAsJsonObject();
               }
            } catch (JsonParseException | IllegalStateException | NullPointerException
                  | NoSuchElementException e) {
               String errorMessage =
                     String.format("Exception while parsing %s", dataSourceFile);
               _logger.error(errorMessage, e);
               throw new RuntimeException(errorMessage, e);
            } catch (IOException e) {
               String errorMessage = String
                     .format("Exception while loading " + "file %s", dataSourceFile);
               _logger.error(errorMessage, e);
               throw new RuntimeException(errorMessage, e);
            }
            _logger.info(String.format("Completed reading file %s: ",
                  dataSourceFile.toString()));
         });

      } catch (IOException e) {
         String errorMessage = String.format(
               "Exception while collecting files present in directory" + " %s",
               PLUGIN_DATA_SOURCE_FILE_FOLDER);
         _logger.error(errorMessage, e);
         throw new RuntimeException(errorMessage, e);
      }
      return dataSourceJsonObject[0];
   }
}
