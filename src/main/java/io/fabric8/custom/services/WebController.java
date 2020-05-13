package io.fabric8.custom.services;

import java.io.IOException;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.fabric8.custom.operator.CustomServiceOperator;

@RestController
public class WebController {

   ObjectMapper objectMapper = new ObjectMapper();
   CustomServiceOperator customServiceOperatorMain;

   @RequestMapping(value = "/config/current", produces = { "application/json" },
         method = RequestMethod.GET)
   public JsonNode index() throws IOException {

      if (customServiceOperatorMain == null) {
         customServiceOperatorMain = new CustomServiceOperator();
         customServiceOperatorMain.start();
      }
      JsonNode jsonNode = objectMapper.readTree(objectMapper
            .writeValueAsString(customServiceOperatorMain.getCurrentState()));
      return jsonNode.findValue("spec");
   }

}
