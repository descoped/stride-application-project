package io.descoped.stride.application.server;

import org.glassfish.hk2.runlevel.ChangeableRunLevelFuture;
import org.glassfish.hk2.runlevel.ErrorInformation;
import org.glassfish.hk2.runlevel.RunLevelFuture;
import org.glassfish.hk2.runlevel.RunLevelListener;
import org.jvnet.hk2.annotations.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RunLevelLogger implements RunLevelListener {
    private static final Logger log = LoggerFactory.getLogger(RunLevelLogger.class);

    @Override
    public void onProgress(ChangeableRunLevelFuture currentJob, int levelAchieved) {
        log.debug("Reached run level {}", levelAchieved);
    }

    @Override
    public void onCancelled(RunLevelFuture currentJob, int levelAchieved) {
        log.warn("Cancelled run level {}", levelAchieved);

    }

    @Override
    public void onError(RunLevelFuture currentJob, ErrorInformation errorInformation) {
        log.error("Error at run level from {}", errorInformation.getFailedDescriptor().getImplementation(), errorInformation.getError());
    }
}
