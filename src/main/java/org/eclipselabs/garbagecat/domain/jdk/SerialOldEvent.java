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
 * SERIAL_OLD
 * </p>
 * 
 * <p>
 * Enabled with the <code>-XX:+UseSerialGC</code> JVM option. Uses a mark-sweep-compact algorithm.
 * </p>
 * 
 * <h3>Example Logging</h3>
 * 
 * <p>
 * 1) Standard format:
 * </p>
 * 
 * <pre>
 * 187.159: [Full GC 187.160: [Tenured: 97171K-&gt;102832K(815616K), 0.6977443 secs] 152213K-&gt;102832K(907328K), [Perm : 49152K-&gt;49154K(49158K)], 0.6929258 secs]
 * </pre>
 * 
 * <p>
 * 2) JDK 1.6 with trigger:
 * </p>
 * 
 * <pre>
 * 2.457: [Full GC (System) 2.457: [Tenured: 1092K-&gt;2866K(116544K), 0.0489980 secs] 11012K-&gt;2866K(129664K), [Perm : 8602K-&gt;8602K(131072K)], 0.0490880 secs]
 * </pre>
 * 
 * <p>
 * 3) Combined {@link SerialNewEvent} and
 * {@link SerialOldEvent} with permanent generation data.
 * 
 * <p>
 * It looks like this is a result of the young generation guarantee. The young generation fills up to where it exceeds
 * the old generation free space, so a full collection is triggered to free up old space.
 * </p>
 * 
 * <h3>Example Logging</h3>
 * 
 * <pre>
 * 3727.365: [GC 3727.365: [DefNew: 400314K-&gt;400314K(400384K), 0.0000550 secs]3727.365: [Tenured: 837793K-&gt;597490K(889536K), 44.7498530 secs] 1238107K-&gt;597490K(1289920K), [Perm : 54745K-&gt;54745K(54784K)], 44.7501880 secs] [Times: user=5.32 sys=0.33, real=44.75 secs]
 * </pre>
 * 
 * <p>
 * With Metaspace and Datestamps.
 * </p>
 * 
 * <pre>
 * 2.447: [Full GC (Metadata GC Threshold) 2.447: [Tenured: 0K-&gt;12062K(524288K), 0.1248607 secs] 62508K-&gt;12062K(760256K), [Metaspace: 20526K-&gt;20526K(1069056K)], 0.1249442 secs] [Times: user=0.18 sys=0.08, real=0.13 secs]
 * </pre>
 * 
 * @author <a href="mailto:mmillson@redhat.com">Mike Millson</a>
 * @author jborelo
 * 
 */
public class SerialOldEvent extends SerialCollector implements BlockingEvent, YoungCollection, OldCollection,
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
    private static final String TRIGGER = "(" + JdkRegEx.TRIGGER_SYSTEM_GC + "|"
            + JdkRegEx.TRIGGER_METADATA_GC_THRESHOLD + "|" + JdkRegEx.TRIGGER_ALLOCATION_FAILURE + ")";

    /**
     * Regular expression for SERIAL_NEW block in some events.
     */
    public static final String SERIAL_NEW_BLOCK = "(" + JdkRegEx.DATESTAMP + ": )?" + JdkRegEx.TIMESTAMP
            + ": \\[DefNew( \\((" + JdkRegEx.TRIGGER_PROMOTION_FAILED + ")\\) )?: " + JdkRegEx.SIZE_K + "->"
            + JdkRegEx.SIZE_K + "\\(" + JdkRegEx.SIZE_K + "\\), " + JdkRegEx.DURATION + "\\]";

    /**
     * Regular expressions defining the logging.
     */
    private static final String REGEX = "^(" + JdkRegEx.DATESTAMP + ": )?" + JdkRegEx.TIMESTAMP + ": \\[(Full )?GC( \\("
            + TRIGGER + "\\))?([ ]{0,1}" + SERIAL_NEW_BLOCK + ")?( )?(" + JdkRegEx.DATESTAMP + ": )?"
            + JdkRegEx.TIMESTAMP + ": \\[Tenured: " + JdkRegEx.SIZE_K + "->" + JdkRegEx.SIZE_K + "\\(" + JdkRegEx.SIZE_K
            + "\\), " + JdkRegEx.DURATION + "\\] " + JdkRegEx.SIZE_K + "->" + JdkRegEx.SIZE_K + "\\(" + JdkRegEx.SIZE_K
            + "\\), \\[(Perm |Metaspace): " + JdkRegEx.SIZE_K + "->" + JdkRegEx.SIZE_K + "\\(" + JdkRegEx.SIZE_K
            + "\\)\\], " + JdkRegEx.DURATION + "\\]" + TimesData.REGEX + "?[ ]*$";

    private static Pattern pattern = Pattern.compile(SerialOldEvent.REGEX);

    /**
     * Default constructor
     */
    public SerialOldEvent() {
    }

    /**
     * 
     * @param logEntry
     *            The log entry for the event.
     */
    public SerialOldEvent(String logEntry) {
        this.logEntry = logEntry;
        Matcher matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            timestamp = JdkMath.convertSecsToMillis(matcher.group(12)).longValue();
            // Use last trigger
            if (matcher.group(31) != null) {
                trigger = matcher.group(31);
            } else if (matcher.group(15) != null) {
                trigger = matcher.group(15);
            }
            old = Integer.parseInt(matcher.group(51));
            oldEnd = Integer.parseInt(matcher.group(52));
            oldAllocation = Integer.parseInt(matcher.group(53));
            int totalBegin = Integer.parseInt(matcher.group(57));
            young = totalBegin - getOldOccupancyInit();
            int totalEnd = Integer.parseInt(matcher.group(58));
            youngEnd = totalEnd - getOldOccupancyEnd();
            int totalAllocation = Integer.parseInt(matcher.group(59));
            youngAvailable = totalAllocation - getOldSpace();
            // Do not need total begin/end/allocation, as these can be calculated.
            permGen = Integer.parseInt(matcher.group(61));
            permGenEnd = Integer.parseInt(matcher.group(62));
            permGenAllocation = Integer.parseInt(matcher.group(63));
            duration = JdkMath.convertSecsToMicros(matcher.group(64)).intValue();
        }
    }

    /**
     * Alternate constructor. Create serial old detail logging event from values.
     * 
     * @param logEntry
     *            The log entry for the event.
     * @param timestamp
     *            The time when the GC event started in milliseconds after JVM startup.
     * @param duration
     *            The elapsed clock time for the GC event in microseconds.
     */
    public SerialOldEvent(String logEntry, long timestamp, int duration) {
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
        return JdkUtil.LogEventType.SERIAL_OLD.toString();
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
    public static boolean match(String logLine) {
        return pattern.matcher(logLine).matches();
    }
}
