/**********************************************************************************************************************
 * garbagecat                                                                                                         *
 *                                                                                                                    *
 * Copyright (c) 2008-2020 Mike Millson                                                                               *
 *                                                                                                                    * 
 * All rights reserved. This program and the accompanying materials are made available under the terms of the Eclipse *
 * Public License v1.0 which accompanies this distribution, and is available at                                       *
 * http://www.eclipse.org/legal/epl-v10.html.                                                                         *
 *                                                                                                                    *
 * Contributors:                                                                                                      *
 *    Mike Millson - initial API and implementation                                                                   *
 *********************************************************************************************************************/
package org.eclipselabs.garbagecat.domain.jdk;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipselabs.garbagecat.domain.BlockingEvent;
import org.eclipselabs.garbagecat.domain.OldCollection;
import org.eclipselabs.garbagecat.domain.OldData;
import org.eclipselabs.garbagecat.domain.PermMetaspaceCollection;
import org.eclipselabs.garbagecat.domain.PermMetaspaceData;
import org.eclipselabs.garbagecat.domain.SerialCollection;
import org.eclipselabs.garbagecat.domain.TimesData;
import org.eclipselabs.garbagecat.domain.TriggerData;
import org.eclipselabs.garbagecat.domain.YoungCollection;
import org.eclipselabs.garbagecat.domain.YoungData;
import org.eclipselabs.garbagecat.util.jdk.JdkMath;
import org.eclipselabs.garbagecat.util.jdk.JdkRegEx;
import org.eclipselabs.garbagecat.util.jdk.JdkUtil;

/**
 * <p>
 * PARALLEL_SERIAL_OLD
 * </p>
 * 
 * <p>
 * Enabled with <code>-XX:+UseParallelGC</code> JVM option. This is really a
 * {@link SerialOldEvent}; however, the logging is different. Treat as a separate
 * event for now.
 * </p>
 * 
 * <p>
 * Uses "PSOldGen" vs. {@link ParallelCompactingOldEvent} "ParOldGen".
 * 
 * <h3>Example Logging</h3>
 * 
 * <p>
 * 1) Standard format:
 * </p>
 * 
 * <pre>
 * 3.600: [Full GC [PSYoungGen: 5424K-&gt;0K(38208K)] [PSOldGen: 488K-&gt;5786K(87424K)] 5912K-&gt;5786K(125632K) [PSPermGen: 13092K-&gt;13094K(131072K)], 0.0699360 secs]
 * </pre>
 * 
 * <p>
 * 2) With trigger (Note "Full GC" vs. "Full GC (System)"):
 * </p>
 * 
 * <pre>
 * 4.165: [Full GC (System) [PSYoungGen: 1784K-&gt;0K(12736K)] [PSOldGen: 1081K-&gt;2855K(116544K)] 2865K-&gt;2855K(129280K) [PSPermGen: 8600K-&gt;8600K(131072K)], 0.0427680 secs]
 * </pre>
 * 
 * <p>
 * 3) JDK8 with comman before Metaspace block:
 * </p>
 * 
 * <pre>
 * 2018-12-06T19:04:46.807-0500: 0.122: [Full GC (Ergonomics) [PSYoungGen: 508K-&gt;385K(1536K)] [PSOldGen: 408K-&gt;501K(2048K)] 916K-&gt;887K(3584K), [Metaspace: 3680K-&gt;3680K(1056768K)], 0.0030057 secs] [Times: user=0.01 sys=0.00, real=0.00 secs]
 * </pre>
 * 
 * TODO: Expand or extend {@link SerialOldEvent}.
 * 
 * @author <a href="mailto:mmillson@redhat.com">Mike Millson</a>
 * @author jborelo
 * 
 */
public class ParallelSerialOldEvent extends ParallelCollector implements BlockingEvent, YoungCollection, OldCollection,
        PermMetaspaceCollection, YoungData, OldData, PermMetaspaceData, TriggerData, SerialCollection {

    /**
     * The log entry for the event. Can be used for debugging purposes.
     */
    private String logEntry;

    /**
     * The elapsed clock time for the GC event in microseconds (rounded).
     */
    private int duration;

    /**
     * The time when the GC event started in milliseconds after JVM startup.
     */
    private long timestamp;

    /**
     * Young generation size (kilobytes) at beginning of GC event.
     */
    private int young;

    /**
     * Young generation size (kilobytes) at end of GC event.
     */
    private int youngEnd;

    /**
     * Available space in young generation (kilobytes). Equals young generation allocation minus one survivor space.
     */
    private int youngAvailable;

    /**
     * Old generation size (kilobytes) at beginning of GC event.
     */
    private int old;

    /**
     * Old generation size (kilobytes) at end of GC event.
     */
    private int oldEnd;

    /**
     * Space allocated to old generation (kilobytes).
     */
    private int oldAllocation;

    /**
     * Permanent generation size (kilobytes) at beginning of GC event.
     */
    private int permGen;

    /**
     * Permanent generation size (kilobytes) at end of GC event.
     */
    private int permGenEnd;

    /**
     * Space allocated to permanent generation (kilobytes).
     */
    private int permGenAllocation;

    /**
     * The trigger for the GC event.
     */
    private String trigger;

    /**
     * Trigger(s) regular expression(s).
     */
    private static final String TRIGGER = "(" + JdkRegEx.TRIGGER_SYSTEM_GC + "|" + JdkRegEx.TRIGGER_ERGONOMICS + ")";

    /**
     * Regular expressions defining the logging.
     */
    private static final String REGEX = "^(" + JdkRegEx.DATESTAMP + ": )?" + JdkRegEx.TIMESTAMP + ": \\[Full GC (\\("
            + TRIGGER + "\\) )?\\[PSYoungGen: " + JdkRegEx.SIZE_K + "->" + JdkRegEx.SIZE_K + "\\(" + JdkRegEx.SIZE_K
            + "\\)\\] \\[PSOldGen: " + JdkRegEx.SIZE_K + "->" + JdkRegEx.SIZE_K + "\\(" + JdkRegEx.SIZE_K + "\\)\\] "
            + JdkRegEx.SIZE_K + "->" + JdkRegEx.SIZE_K + "\\(" + JdkRegEx.SIZE_K
            + "\\)[,]{0,1} \\[(PSPermGen|Metaspace): " + JdkRegEx.SIZE_K + "->" + JdkRegEx.SIZE_K + "\\("
            + JdkRegEx.SIZE_K + "\\)\\], " + JdkRegEx.DURATION + "\\]" + TimesData.REGEX + "?[ ]*$";

    private static Pattern pattern = Pattern.compile(ParallelSerialOldEvent.REGEX);

    /**
     * Create event from log entry.
     * 
     * @param logEntry
     *            The log entry for the event.
     */
    public ParallelSerialOldEvent(String logEntry) {
        this.logEntry = logEntry;
        Matcher matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            this.timestamp = JdkMath.convertSecsToMillis(matcher.group(12)).longValue();

            if (matcher.group(14) != null) {
                this.trigger = matcher.group(14);
            }
            this.young = Integer.parseInt(matcher.group(16));
            this.youngEnd = Integer.parseInt(matcher.group(17));
            this.youngAvailable = Integer.parseInt(matcher.group(18));

            this.old = Integer.parseInt(matcher.group(19));
            this.oldEnd = Integer.parseInt(matcher.group(20));
            this.oldAllocation = Integer.parseInt(matcher.group(21));

            this.permGen = Integer.parseInt(matcher.group(26));
            this.permGenEnd = Integer.parseInt(matcher.group(27));
            this.permGenAllocation = Integer.parseInt(matcher.group(28));

            this.duration = JdkMath.convertSecsToMicros(matcher.group(29)).intValue();
        }
    }

    /**
     * Alternate constructor. Create parallel old detail logging event from values.
     * 
     * @param logEntry
     *            The log entry for the event.
     * @param timestamp
     *            The time when the GC event started in milliseconds after JVM startup.
     * @param duration
     *            The elapsed clock time for the GC event in microseconds.
     */
    public ParallelSerialOldEvent(String logEntry, long timestamp, int duration) {
        this.logEntry = logEntry;
        this.timestamp = timestamp;
        this.duration = duration;
    }

    public String getLogEntry() {
        return logEntry;
    }

    protected void setLogEntry(String logEntry) {
        this.logEntry = logEntry;
    }

    public int getDuration() {
        return duration;
    }

    protected void setDuration(int duration) {
        this.duration = duration;
    }

    public long getTimestamp() {
        return timestamp;
    }

    protected void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getYoungOccupancyInit() {
        return young;
    }

    protected void setYoungOccupancyInit(int young) {
        this.young = young;
    }

    public int getYoungOccupancyEnd() {
        return youngEnd;
    }

    protected void setYoungOccupancyEnd(int youngEnd) {
        this.youngEnd = youngEnd;
    }

    public int getYoungSpace() {
        return youngAvailable;
    }

    protected void setYoungSpace(int youngAvailable) {
        this.youngAvailable = youngAvailable;
    }

    public int getOldOccupancyInit() {
        return old;
    }

    protected void setOldOccupancyInit(int old) {
        this.old = old;
    }

    public int getOldOccupancyEnd() {
        return oldEnd;
    }

    protected void setOldOccupancyEnd(int oldEnd) {
        this.oldEnd = oldEnd;
    }

    public int getOldSpace() {
        return oldAllocation;
    }

    protected void setOldSpace(int oldAllocation) {
        this.oldAllocation = oldAllocation;
    }

    public int getPermOccupancyInit() {
        return permGen;
    }

    protected void setPermOccupancyInit(int permGen) {
        this.permGen = permGen;
    }

    public int getPermOccupancyEnd() {
        return permGenEnd;
    }

    protected void setPermOccupancyEnd(int permGenEnd) {
        this.permGenEnd = permGenEnd;
    }

    public int getPermSpace() {
        return permGenAllocation;
    }

    protected void setPermSpace(int permGenAllocation) {
        this.permGenAllocation = permGenAllocation;
    }

    public String getName() {
        return JdkUtil.LogEventType.PARALLEL_SERIAL_OLD.toString();
    }

    public String getTrigger() {
        return trigger;
    }

    protected void setTrigger(String trigger) {
        this.trigger = trigger;
    }

    /**
     * Determine if the logLine matches the logging pattern(s) for this event.
     * 
     * @param logLine
     *            The log line to test.
     * @return true if the log line matches the event pattern, false otherwise.
     */
    public static final boolean match(String logLine) {
        return pattern.matcher(logLine).matches();
    }
}
