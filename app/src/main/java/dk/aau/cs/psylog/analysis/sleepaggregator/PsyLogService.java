package dk.aau.cs.psylog.analysis.sleepaggregator;

import android.content.Intent;

import dk.aau.cs.psylog.module_lib.IScheduledTask;
import dk.aau.cs.psylog.module_lib.ScheduledService;

/**
 * Created by Praetorian on 23-04-2015.
 */
public class PsyLogService extends ScheduledService {

    public PsyLogService() {
        super("debug name - sleep aggregator");
    }

    @Override
    public void setScheduledTask() {
        this.scheduledTask = new Aggregator(this);
    }
}
