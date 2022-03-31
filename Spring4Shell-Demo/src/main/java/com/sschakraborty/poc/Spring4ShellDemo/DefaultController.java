package com.sschakraborty.poc.Spring4ShellDemo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DefaultController {
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Logger logger;

    @RequestMapping(value = "/spring4shell")
    public void index(Spring4ShellRequest request) throws JsonProcessingException {
        logger.info(objectMapper.writeValueAsString(request));
    }

    /**
     * The following is the remediation
     * You only need to add this method in one of your controllers in order to prevent exploitation.
     */
    @InitBinder
    public void initBinder(WebDataBinder binder) {
        String[] blackList = {"class.*","Class.*","*.class.*",".*Class.*"};
        binder.setDisallowedFields(blackList);
    }
}
