package org.nzbhydra.web;

import org.nzbhydra.update.UpdateManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Control {

    @Autowired
    private UpdateManager updateManager;

    private static final Logger logger = LoggerFactory.getLogger(Control.class);

    @Secured({"ROLE_ADMIN"})
    @RequestMapping(value = "/internalapi/control/shutdown", method = RequestMethod.GET)
    public String shutdown() throws Exception {
        logger.info("Shutting down due to external request");
        updateManager.exitWithReturnCode(0);
        return "OK";
    }


    @Secured({"ROLE_ADMIN"})
    @RequestMapping(value = "/internalapi/control/restart", method = RequestMethod.GET)
    public String restart() throws Exception {
        logger.info("Shutting down due to external request. Restart will be handled by wrapper");
        updateManager.exitWithReturnCode(2);
        return "OK";
    }


}