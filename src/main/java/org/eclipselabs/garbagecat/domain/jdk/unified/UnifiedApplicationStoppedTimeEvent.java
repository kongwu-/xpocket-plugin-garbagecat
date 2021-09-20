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
package org.eclipselabs.garbagecat.domain.jdk.unified;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipselabs.garbagecat.domain.jdk.ApplicationStoppedTimeEvent;
import org.eclipselabs.garbagecat.util.jdk.JdkMath;
import org.eclipselabs.garbagecat.util.jdk.JdkUtil;
import org.eclipselabs.garbagecat.util.jdk.unified.UnifiedRegEx;
import org.eclipselabs.garbagecat.util.jdk.unified.UnifiedUtil;

/**
 * <p>
 * UNIFIED_APPLICATION_STOPPED_TIME
 * </p>
 * 
 * <p>
 * {@link ApplicationStoppedTimeEvent} with unified logging (JDK9+).
 * </p>
 * 
 * <p>
 * Logging enabled with the <code>safepoint=info</code> unified logging option. It shows the time spent in a safepoint,
 * when all threads are stopped and reachable by the JVM. Many JVM operations require that all threads be in a safepoint
 * to execute. The most common is a "stop the world" garbage collection.
 * </p>
 * 
 * <p>
 * A required logging option to determine overall throughput and identify throughput and pause issues not related to
 * garbage collection.
 * </p>
 * 
 * <p>
 * Other JVM operations besides gc that require a safepoint:
 * </p>
 * <ul>
 * <li>ThreadDump</li>
 * <li>HeapDumper</li>
 * <li>GetAllStackTrace</li>
 * <li>PrintThreads</li>
 * <li>PrintJNI</li>
 * <li>RevokeBias</li>
 * <li>Deoptimization</li>
 * <li>FindDeadlock</li>
 * <li>EnableBiasLocking</li>
 * </ul>
 * 
 * <h3>Example Logging</h3>
 * 
 * <pre>
 * [0.031s][info][safepoint    ] Total time for which application threads were stopped: 0.0000643 seconds, Stopping threads took: 0.0000148 seconds
 * </pre>
 * 
 * @author <a href="mailto:mmillson@redhat.com">Mike Millson</a>
 * 
 */
public class UnifiedApplicationStoppedTimeEvent extends ApplicationStoppedTimeEvent implements UnifiedLogging {

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
     * Regular expressions defining the logging.
     */
    private static final String REGEX = "^" + UnifiedRegEx.DECORATOR
            + " Total time for which application threads were stopped: (\\d{1,4}[\\.\\,]\\d{7}) seconds, "
            + "Stopping threads took: (\\d{1,4}[\\.\\,]\\d{7}) seconds[ ]*$";
    /**
     * RegEx pattern.
     */
    private static Pattern pattern = Pattern.compile(REGEX);

    /**
     * Create event from log entry.
     * 
     * @param logEntry
     *            The log entry for the event.
     */
    public UnifiedApplicationStoppedTimeEvent(String logEntry) {
        super(logEntry);
        this.logEntry = logEntry;
        Matcher matcher = pattern.matcher(logEntry);
        if (matcher.find()) {
            if (matcher.group(1).matches(UnifiedRegEx.UPTIMEMILLIS)) {
                timestamp = Long.parseLong(matcher.group(13));
            } else if (matcher.group(1).matches(UnifiedRegEx.UPTIME)) {
                timestamp = JdkMath.convertSecsToMillis(matcher.group(12)).longValue();
            } else {
                if (matcher.group(15) != null) {
                    if (matcher.group(15).matches(UnifiedRegEx.UPTIMEMILLIS)) {
                        timestamp = Long.parseLong(matcher.group(17));
                    } else {
                        timestamp = JdkMath.convertSecsToMillis(matcher.group(16)).longValue();
                    }
                } else {
                    // Datestamp only.
                    timestamp = UnifiedUtil.convertDatestampToMillis(matcher.group(1));
                }
            }
            duration = JdkMath.convertSecsToMicros(matcher.group(25)).intValue();
        }
    }

    /**
     * Alternate constructor. Create application stopped time event from values.
     * 
     * @param logEntry
     *            The log entry for the event.
     * @param timestamp
     *            The time when the GC event started in milliseconds after JVM startup.
     * @param duration
     *            The elapsed clock time for the GC event in microseconds (rounded).
     */
    public UnifiedApplicationStoppedTimeEvent(String logEntry, long timestamp, int duration) {
        super(logEntry, timestamp, duration);
        this.logEntry = logEntry;
        this.timestamp = timestamp;
        this.duration = duration;
    }

    public String getLogEntry() {
        return logEntry;
    }

    public int getDuration() {
        return duration;
    }

    public String getName() {
        return JdkUtil.LogEventType.UNIFIED_APPLICATION_STOPPED_TIME.toString();
    }

    public long getTimestamp() {
        return timestamp;
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
