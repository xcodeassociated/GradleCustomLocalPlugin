package com.sample.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.sample.project.version.*;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String... args) {
        logger.debug("Hello Main!");
        logger.info("Commit Hash: " + SampleVersion.GIT_SHA);
        logger.info("Commit Tag: " + SampleVersion.GIT_TAG);
        logger.info("Commit Date UTC: " + SampleVersion.GIT_DATE);
        logger.info("Git Revision: " + SampleVersion.GIT_REVISION);
        logger.info("Build Date UTC: " + SampleVersion.BUILD_DATE);
        logger.info("Build Date UNIX: " + SampleVersion.BUILD_UNIX_TIME);
    }
}
