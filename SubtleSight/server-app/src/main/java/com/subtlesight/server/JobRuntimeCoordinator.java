package com.subtlesight.server;

import com.subtlesight.jobs.DurableJobQueue;
import com.subtlesight.jobs.DurableJobRunner;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class JobRuntimeCoordinator {
    private final DurableJobQueue queue;private final DurableJobRunner runner;
    public JobRuntimeCoordinator(DurableJobQueue queue,DurableJobRunner runner){this.queue=queue;this.runner=runner;}
    @PostConstruct void recover(){queue.recoverExpiredLeases();}
    @Scheduled(fixedDelay=200) public void tick(){for(int i=0;i<4&&runner.tick();i++);}
}
