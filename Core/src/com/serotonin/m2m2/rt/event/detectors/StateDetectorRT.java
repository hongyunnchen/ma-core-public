/*
    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
    @author Matthew Lohbihler
 */
package com.serotonin.m2m2.rt.event.detectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.serotonin.m2m2.rt.dataImage.PointValueTime;
import com.serotonin.m2m2.vo.event.detector.TimeoutDetectorVO;

/**
 * @author Matthew Lohbihler
 */
abstract public class StateDetectorRT<T extends TimeoutDetectorVO<T>> extends TimeDelayedEventDetectorRT<T> {

	/**
	 * @param vo
	 */
	public StateDetectorRT(T vo) {
		super(vo);
	}

	private final Log log = LogFactory.getLog(StateDetectorRT.class);

    /**
     * State field. Whether the state has been detected or not. This field is used to prevent multiple events being
     * raised during the duration of a single state detection.
     */
    private boolean stateActive;

    private long stateActiveTime;
    private long stateInactiveTime;

    /**
     * State field. Whether the event is currently active or not. This field is used to prevent multiple events being
     * raised during the duration of a single state detection.
     */
    private boolean eventActive;

    protected boolean isActive() {
        return eventActive;
    }

    @Override
	public boolean isEventActive() {
        return eventActive;
    }

    private void changeStateActive() {
        stateActive = !stateActive;

        if (stateActive)
            // Schedule a job that will call the event active if it runs.
            scheduleJob();
        else
            unscheduleJob(stateInactiveTime);
    }

    abstract protected boolean stateDetected(PointValueTime newValue);

    @Override
    public void pointChanged(PointValueTime oldValue, PointValueTime newValue) {
        if (stateDetected(newValue)) {
            if (!stateActive) {
                stateActiveTime = newValue.getTime();
                changeStateActive();
            }
        }
        else {
            if (stateActive) {
                stateInactiveTime = newValue.getTime();
                changeStateActive();
            }
        }
    }

    @Override
    protected long getConditionActiveTime() {
        return stateActiveTime;
    }

    @Override
    synchronized public void setEventActive(boolean b) {
        eventActive = b;
        if (eventActive) {
            // Just for the fun of it, make sure that the state is active.
            if (stateActive)
                // Ok, things are good. Carry on...
                // Raise the event.
                raiseEvent(stateActiveTime + getDurationMS(), createEventContext());
            else {
                // Perhaps the job wasn't successfully unscheduled. Write a log entry and ignore.
                log.warn("Call to set event active when state is not active. Ignoring.");
                eventActive = false;
            }
        }
        else
            // Deactive the event.
            returnToNormal(stateInactiveTime);
    }
}
