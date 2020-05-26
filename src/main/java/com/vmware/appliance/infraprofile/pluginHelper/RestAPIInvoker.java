/* **********************************************************
 * Copyright 2020 VMware, Inc.  All rights reserved. -- VMware Confidential
 * **********************************************************/
package com.vmware.appliance.infraprofile.pluginHelper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.jayway.jsonpath.DocumentContext;
import com.vmware.appliance.infraprofile.vapi.authz.ServiceUtil;
import com.vmware.vim.binding.lookup.ServiceRegistration;
import com.vmware.vim.sso.client.SamlToken;
import com.vmware.vim.sso.client.SecurityTokenService;
import com.vmware.vim.sso.client.SecurityTokenServiceConfig;
import com.vmware.vim.sso.client.TokenSpec;

import sun.net.www.protocol.https.HttpsURLConnectionImpl;

/**
 * This is a Rest client class ,It provide's functionality to invoke any REST api.
 */
public class RestAPIInvoker {


   private static Log _logger = LogFactory.getLog(RestAPIInvoker.class);
   private static String[] stores = { "machine", "vpxd", "vsphere-webclient", "hvc" };
   private static final String BASE_API_URL = "https://10.161.75.87/rest";
   private String sessionId;
   private CisSessionManager cisSessionManager;
   private DocumentContext jsonContext;

   private static final String CONTENT_TYPE = "Content-Type";
   private static final String CONTENT_TYPE_VALUE = "application/json";
   private static final String ACCEPT = "Accept";
   private static final String API_SESSION_ID = "vmware-api-session-id";

   static {
      disableSslVerification();
   }

   /**
    * Get Cis session id.
    * 
    * @return
    */
   public CisSessionManager getCisSessionManager() {
      return cisSessionManager;
   }

   public void setCisSessionManager(CisSessionManager cisSessionManager) {
      this.cisSessionManager = cisSessionManager;
   }

   public RestAPIInvoker() {
      disableSslVerification();
      cisSessionManager = new CisSessionManager();
   }

   static void disableSslVerification() {
      try {
         // Create a trust manager that does not validate certificate chains
         TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
               return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
         } };

         // Install the all-trusting trust manager
         SSLContext sc = SSLContext.getInstance("SSL");
         sc.init(null, trustAllCerts, new java.security.SecureRandom());
         HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

         // Create all-trusting host name verifier
         HostnameVerifier allHostsValid = (hostname, session) -> true;
         // Install the all-trusting host verifier
         HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
      } catch (NoSuchAlgorithmException | KeyManagementException e) {
         throw new RuntimeException(e);
      }
   }

   /**
    * This method will provide the Cis session.
    * 
    * @return Cis Session Id.
    */
   /* public String getSessionId() {
      SamlToken samlToken;
      PrivateKey privateKey;
      try {
         samlToken = getSamlToken(stores[0]);
         privateKey = getPrivateKey(stores[0]);
      } catch (Exception e) {
         String errorMessage = "Exception while fetching samlToken or "
               + "privateKey";
         _logger.error(errorMessage, e);
         throw new RuntimeException(errorMessage , e);
      }
   
      String cisSessionId = cisSessionManager.getCisSession(samlToken,
            privateKey);
   
      _logger.info("CIS Session Id : " + cisSessionId);
   
      return cisSessionId;
   }*/

   /**
    * This method will provide functionality to invoke Rest api with GET action.
    * 
    * @param dataSourcePath
    *           : Rest API URI path.
    * @return : Response of API in string format.
    */
   private String callGetApi(String dataSourcePath) {
      _logger.debug("calling NewApiCall");
      String output = null;
      sessionId = getSessionId();

      try {
         _logger.info("sessionId: " + sessionId);
         _logger.info("dataSourcePath: " + dataSourcePath);
         HttpsURLConnectionImpl conn = getHttpUrlConnection("GET", dataSourcePath);

         if (conn.getResponseCode() != 200) {
            _logger.error("Failed : HTTP error code : " + conn.getResponseCode());
            throw new RuntimeException(
                  "Failed : HTTP error code : " + conn.getResponseCode());
         }

         BufferedReader br =
               new BufferedReader(new InputStreamReader((conn.getInputStream())));
         while ((output = br.readLine()) != null) {
            _logger.debug(
                  String.format("GET API %s response %s", dataSourcePath, output));
            return output;
         }
      } catch (Exception e) {
         _logger.error("Exception while calling GET API " + dataSourcePath, e);
         throw new RuntimeException("Exception while calling GET API " + dataSourcePath,
               e);
      }
      return output;
   }

   /**
    *
    * @param dataSourcePath
    *           : Rest API URI path.
    * @param jsonContext
    *           : Request body in jsonContext
    * @param action
    *           : PUT ,POST or PATCH.
    */
   private void callPutApi(String dataSourcePath, String jsonContext, String action) {
      String output = null;
      sessionId = getSessionId();

      try {
         HttpsURLConnectionImpl conn = getHttpUrlConnection(action, dataSourcePath);
         conn.setDoInput(true);
         conn.setDoOutput(true);
         OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
         wr.write(jsonContext);
         wr.flush();

         if (conn.getResponseCode() != 200) {
            _logger.error("Failed : HTTP error code : " + conn.getResponseCode());
            throw new RuntimeException(
                  "Failed : HTTP error code : " + conn.getResponseCode());
         }
         _logger
               .info(String.format("PUT API %s completed successfully", dataSourcePath));
      } catch (Exception e) {
         _logger.error("Exception while calling PUT API " + dataSourcePath, e);
         throw new RuntimeException("Exception while calling PUT API " + dataSourcePath,
               e);
      }
   }

   /**
    * This method provide functionality to invoke any Rest API.
    * 
    * @param apiPath
    *           : API URI path
    * @param action
    *           : API action.
    * @param pJsonContext
    *           : Api request body.
    * @return : Api response in string.
    */
   public String invokeRestAPI(String apiPath, String action, String pJsonContext) {
      if (action.equals("GET")) {
         _logger.info("Start calling GET API " + apiPath);
         String output = callGetApi(apiPath);
         _logger.info("Complete calling GET API " + apiPath);
         return output;
      } else {
         _logger.debug("jsonContext: " + apiPath + ": " + jsonContext);
         _logger.info("Start calling PUT API " + apiPath);
         callPutApi(apiPath, pJsonContext, action);
         _logger.info("Complete calling PUT API " + apiPath);
         return "200";
      }
   }

   /**
    * This method will collect SamlToken and return .
    * 
    * @param store
    *           : store name
    * @return : SamlToken object.
    * @throws Exception
    */
   private SamlToken getSamlToken(String store) throws Exception {
      ServiceRegistration.Endpoint[] eps = ServiceUtil.getSsoAndStsEndPoint();
      X509Certificate[] certs = ServiceUtil.getCertsFromStore(store);
      SecurityTokenServiceConfig.HolderOfKeyConfig hokConfig =
            new SecurityTokenServiceConfig.HolderOfKeyConfig(getPrivateKey(store),
                  certs[0], null);
      SecurityTokenService tokenService =
            ServiceUtil.getSecurityTokenService(eps[0], eps[1], hokConfig);
      TokenSpec.Builder builder = new TokenSpec.Builder(60 * 5);
      builder.delegationSpec(new TokenSpec.DelegationSpec(true, null));
      TokenSpec tokenSpec = builder.createTokenSpec();
      SamlToken token = tokenService.acquireTokenByCertificate(tokenSpec);
      return token;
   }

   /**
    *
    * @param storeName
    *           : Store name
    * @return : Private key
    * @throws Exception
    */
   private PrivateKey getPrivateKey(String storeName) throws Exception {
      KeyStore store = ServiceUtil.loadKeyStore(storeName);
      KeyStore.ProtectionParameter pass =
            new KeyStore.PasswordProtection("".toCharArray());
      KeyStore.PrivateKeyEntry pk =
            (KeyStore.PrivateKeyEntry) store.getEntry(storeName, pass);
      return pk.getPrivateKey();
   }

   /**
    * This method will create a HTTP connection and return the connection.
    * 
    * @param action
    * @param dataSourcePath
    * @return : HTTP URL Connection object.
    */
   private HttpsURLConnectionImpl getHttpUrlConnection(String action,
         String dataSourcePath) {
      HttpsURLConnectionImpl conn = null;
      try {
         URL url = new URL(BASE_API_URL + dataSourcePath);
         _logger.info("URL:" + url.getPath());
         conn = (HttpsURLConnectionImpl) url.openConnection();

         conn.setRequestProperty(CONTENT_TYPE, CONTENT_TYPE_VALUE);
         conn.setRequestProperty(ACCEPT, CONTENT_TYPE_VALUE);
         conn.addRequestProperty(API_SESSION_ID, sessionId);
         conn.setRequestMethod(action);
      } catch (IOException e) {
         String errorMessage = "Exception while creating HTTP URL connection";
         _logger.error(errorMessage, e);
         throw new RuntimeException(errorMessage, e);
      }
      return conn;
   }

   //10.161.75.87

   public String getSessionId() {
      try {
         URL url = new URL("https://10.161.75.87/rest/com/vmware/cis/session");
         HttpsURLConnectionImpl conn = (HttpsURLConnectionImpl) url.openConnection();
         conn.setRequestMethod("POST");
         conn.setRequestProperty("Accept", "application/json");
         String encoded = java.util.Base64.getEncoder()
               .encodeToString(("Administrator@vsphere.local" + ":" + "Admin!23")
                     .getBytes(StandardCharsets.UTF_8));
         //         String encoded = java.util.Base64.getEncoder()
         //               .encodeToString(("vsphere-client" + ":" +
         //                     "vmware").getBytes(StandardCharsets.UTF_8));
         conn.setRequestProperty("Authorization", "Basic " + encoded);
         if (conn.getResponseCode() != 200) {
            throw new RuntimeException(
                  "Failed : HTTP error code : " + conn.getResponseCode());
         }
         BufferedReader br =
               new BufferedReader(new InputStreamReader((conn.getInputStream())));
         String sessionId = null;
         String output;
         while ((output = br.readLine()) != null) {
            sessionId = output.substring(output.indexOf(':') + 2, output.length() - 2);
         }
         _logger.info("session Id " + sessionId);
         return sessionId;
      } catch (IOException e) {
         _logger.info("Exception while getting session id", e);
         throw new RuntimeException("Exception while getting session id", e);
      }
   }
}
