package com.ericsson.oss.adc.models;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;

public class SizedQueue<T> extends LinkedList<T> {

    private static final Logger LOG = LoggerFactory.getLogger(SizedQueue.class);

    private int limit;

    public SizedQueue(final int ropPeriodMinutes, final int retentionPeriodMinutes) {
        if (ropPeriodMinutes != 0 && retentionPeriodMinutes >= ropPeriodMinutes) {
            this.limit = (retentionPeriodMinutes/ropPeriodMinutes);
        } else {
            this.limit = 4; // default limit of 4 sets of ROPs
        }
        LOG.info("SizedQueue of limit {} created (ropPeriodMinutes: {}, retentionPeriodMinutes: {})",
                this.limit, ropPeriodMinutes, retentionPeriodMinutes);
    }

    public T addWithRemove(T object)  {
        boolean added = super.add(object);
        if (added && size() > limit) {
            return super.remove();
        }
        return null;
    }

    public int getLimit() {
        return limit;
    }
}
