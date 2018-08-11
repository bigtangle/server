package net.bigtangle.subtangle.service.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.bigtangle.server.config.SubtangleConfiguration;
import net.bigtangle.subtangle.service.SubtangleService;

@Component
@EnableAsync
public class ScheduleSubtangleService {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleSubtangleService.class);

    @Autowired
    private SubtangleConfiguration subtangleConfiguration;

    @Scheduled(fixedRateString = "${subtangle.rate:10000}")
    public void updateSubtangleService() {
        if (subtangleConfiguration.isActive()) {
            try {
                logger.debug(" Start ScheduleSubtangleService: ");
                subtangleService.giveMoneyToTargetAccount();
            } catch (Exception e) {
                logger.warn("ScheduleSubtangleService ", e);
            }
        }
    }

    @Autowired
    private SubtangleService subtangleService;
}
