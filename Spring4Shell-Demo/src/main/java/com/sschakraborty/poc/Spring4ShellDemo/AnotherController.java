package com.sschakraborty.poc.Spring4ShellDemo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class AnotherController {
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Logger logger;

    @RequestMapping(value = "/spring4shell_endpoint_2")
    public void index(Spring4ShellRequest request) throws JsonProcessingException {
        logger.info(objectMapper.writeValueAsString(request));
    }
}
