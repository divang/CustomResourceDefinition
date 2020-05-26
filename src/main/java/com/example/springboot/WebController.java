package com.example.springboot;

import java.io.IOException;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.fabric8.custom.operator.AppCustomServiceOperator;

@RestController
public class WebController {

   ObjectMapper objectMapper = new ObjectMapper();
   AppCustomServiceOperator customServiceOperatorMain;

   @RequestMapping(value = "/config/current", produces = { "application/json" },
         method = RequestMethod.GET)
   public JsonNode index() throws IOException {

      if (customServiceOperatorMain == null) {
         customServiceOperatorMain = new AppCustomServiceOperator();
         customServiceOperatorMain.start();
      }
      JsonNode jsonNode = objectMapper.readTree(objectMapper
            .writeValueAsString(customServiceOperatorMain.getCurrentState()));
      return jsonNode;
   }

}
