package com.wehavephones.mobile;

import android.util.Log;

import com.wehavephones.core.FrameTimeLogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Puts WoW's two busiest threads (in practice the main and render threads) on
 * the prime cores and keeps WoW's other threads off them, so the threads that
 * decide the frame rate never wait behind a worker thread. Everything else is
 * left to Android's scheduler.
 *
 * The prime cores are found at runtime: the cores with the highest
 * cpuinfo_max_freq (cpu6-7 on the S26 Ultra's SM8850). Thread CPU time is
 * sampled from /proc every few seconds. A new thread only takes a prime slot
 * when it is clearly busier than the one it replaces, so placement doesn't
 * flap. Affinity is set with toybox taskset, which works on threads of our own
 * uid without extra permissions.
 */
public final class CpuPlacement {
    private static final String TAG = "CpuPlacement";
    private static final long SAMPLE_MS = 3000;
    private static final int HOT_THREADS = 2;
    /** A challenger needs this much more CPU time than the hot thread it replaces. */
    private static final double SWITCH_MARGIN = 1.25;
    /** Below this share of one core a thread is never considered hot. */
    private static final double MIN_HOT_SHARE = 0.25;
    private static final String TASKSET = "/system/bin/taskset";

    private static CpuPlacement active;

    private final String primeMask;
    private final String otherMask;
    private final Thread thread;
    private volatile boolean running = true;

    private int wowPid = -1;
    private final Map<Integer, Long> lastTicks = new HashMap<>();
    private final Set<Integer> hot = new HashSet<>();
    private final Set<Integer> placedOther = new HashSet<>();

    private CpuPlacement(String primeMask, String otherMask) {
        this.primeMask = primeMask;
        this.otherMask = otherMask;
        thread = new Thread(this::loop, "CpuPlacement");
        thread.setDaemon(true);
    }

    /** Starts placement for this session when enabled and the phone has distinct prime cores. */
    public static synchronized void start(boolean enabled) {
        stop();
        if (!enabled) {
            FrameTimeLogger.setTag("cpu=auto");
            return;
        }
        String[] masks = coreMasks();
        if (masks == null || !new File(TASKSET).exists()) {
            FrameTimeLogger.setTag("cpu=unavailable");
            return;
        }
        FrameTimeLogger.setTag("cpu=prime[waiting-for-wow]");
        active = new CpuPlacement(masks[0], masks[1]);
        active.thread.start();
    }

    public static synchronized void stop() {
        if (active != null) {
            active.running = false;
            active.thread.interrupt();
            active = null;
        }
    }

    /** {prime mask, other mask} in hex, or null when all cores have the same top speed. */
    static String[] coreMasks() {
        List<Long> maxFreqs = new ArrayList<>();
        for (int cpu = 0; cpu < 64; cpu++) {
            File file = new File("/sys/devices/system/cpu/cpu" + cpu + "/cpufreq/cpuinfo_max_freq");
            if (!file.exists()) break;
            maxFreqs.add(readLong(file.getPath()));
        }
        if (maxFreqs.size() < 2) return null;
        long top = 0;
        for (long freq : maxFreqs) top = Math.max(top, freq);
        long prime = 0, other = 0;
        for (int cpu = 0; cpu < maxFreqs.size(); cpu++) {
            if (maxFreqs.get(cpu) == top) prime |= 1L << cpu;
            else if (maxFreqs.get(cpu) > 0) other |= 1L << cpu;
        }
        if (prime == 0 || other == 0) return null;
        return new String[]{Long.toHexString(prime), Long.toHexString(other)};
    }

    private void loop() {
        while (running) {
            try {
                Thread.sleep(SAMPLE_MS);
                step();
            }
            catch (InterruptedException e) {
                return;
            }
            catch (RuntimeException e) {
                Log.w(TAG, "Placement step failed", e);
            }
        }
    }

    private void step() {
        if (wowPid <= 0 || !new File("/proc/" + wowPid).exists()) {
            ProcessScanner.Entry wow = ProcessScanner.findWowProcess();
            int pid = wow != null ? wow.pid : -1;
            if (pid != wowPid) {
                wowPid = pid;
                lastTicks.clear();
                hot.clear();
                placedOther.clear();
            }
            if (wowPid <= 0) return;
        }

        File[] tasks = new File("/proc/" + wowPid + "/task").listFiles();
        if (tasks == null) return;
        Map<Integer, Long> delta = new HashMap<>();
        Map<Integer, Long> now = new HashMap<>();
        for (File task : tasks) {
            int tid;
            try {
                tid = Integer.parseInt(task.getName());
            }
            catch (NumberFormatException e) {
                continue;
            }
            long ticks = threadTicks(wowPid, tid);
            if (ticks < 0) continue;
            now.put(tid, ticks);
            Long before = lastTicks.get(tid);
            if (before != null) delta.put(tid, ticks - before);
        }
        lastTicks.clear();
        lastTicks.putAll(now);
        hot.retainAll(now.keySet());
        placedOther.retainAll(now.keySet());
        if (delta.isEmpty()) return;

        // Clock ticks are 1/100 s on Android.
        long minHot = (long)(SAMPLE_MS / 10 * MIN_HOT_SHARE);
        List<Integer> ranked = new ArrayList<>(delta.keySet());
        ranked.sort((a, b) -> Long.compare(delta.get(b), delta.get(a)));

        Set<Integer> wanted = new HashSet<>(hot);
        wanted.removeIf(tid -> delta.getOrDefault(tid, 0L) < minHot);
        for (int tid : ranked) {
            if (wanted.contains(tid)) continue;
            if (delta.get(tid) < minHot) break;
            if (wanted.size() < HOT_THREADS) {
                wanted.add(tid);
                continue;
            }
            if (!beatsWeakest(tid, wanted, delta)) break;
            wanted.remove(weakest(wanted, delta));
            wanted.add(tid);
        }

        StringBuilder commands = new StringBuilder();
        for (int tid : wanted) {
            if (!hot.contains(tid)) append(commands, primeMask, tid);
        }
        for (int tid : now.keySet()) {
            if (wanted.contains(tid)) continue;
            if (hot.contains(tid) || !placedOther.contains(tid)) {
                append(commands, otherMask, tid);
                placedOther.add(tid);
            }
        }
        placedOther.removeAll(wanted);
        hot.clear();
        hot.addAll(wanted);
        if (commands.length() > 0) {
            run(commands.toString());
            verify();
        }
    }

    /** Tags the frame log with the cores the hot threads are really allowed on, so a failed taskset shows. */
    private void verify() {
        StringBuilder allowed = new StringBuilder();
        for (int tid : hot) {
            String list = cpusAllowed(wowPid, tid);
            if (allowed.length() > 0) allowed.append(',');
            allowed.append(list);
        }
        FrameTimeLogger.setTag("cpu=prime[" + (allowed.length() > 0 ? allowed : "none") + "]");
    }

    private static String cpusAllowed(int pid, int tid) {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/" + pid + "/task/" + tid + "/status"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("Cpus_allowed_list:")) return line.substring(18).trim();
            }
        }
        catch (IOException ignored) {}
        return "?";
    }

    private static boolean beatsWeakest(int tid, Set<Integer> wanted, Map<Integer, Long> delta) {
        int weakest = weakest(wanted, delta);
        return delta.get(tid) > delta.getOrDefault(weakest, 0L) * SWITCH_MARGIN;
    }

    private static int weakest(Set<Integer> set, Map<Integer, Long> delta) {
        int weakest = -1;
        long least = Long.MAX_VALUE;
        for (int tid : set) {
            long value = delta.getOrDefault(tid, 0L);
            if (value < least) {
                least = value;
                weakest = tid;
            }
        }
        return weakest;
    }

    private static void append(StringBuilder commands, String mask, int tid) {
        commands.append(TASKSET).append(" -p ").append(mask).append(' ').append(tid).append(" >/dev/null 2>&1;");
    }

    private static void run(String script) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"/system/bin/sh", "-c", script});
            process.waitFor();
        }
        catch (IOException e) {
            Log.w(TAG, "taskset failed", e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** utime + stime of a thread in clock ticks, or -1. */
    private static long threadTicks(int pid, int tid) {
        String stat;
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/" + pid + "/task/" + tid + "/stat"))) {
            stat = reader.readLine();
        }
        catch (IOException e) {
            return -1;
        }
        if (stat == null) return -1;
        // Fields after the ")" that closes comm: state is field 3, utime 14, stime 15.
        int close = stat.lastIndexOf(')');
        if (close < 0) return -1;
        String[] fields = stat.substring(close + 2).split(" ");
        if (fields.length < 13) return -1;
        try {
            return Long.parseLong(fields[11]) + Long.parseLong(fields[12]);
        }
        catch (NumberFormatException e) {
            return -1;
        }
    }

    private static long readLong(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line = reader.readLine();
            return line != null ? Long.parseLong(line.trim()) : 0;
        }
        catch (IOException | NumberFormatException e) {
            return 0;
        }
    }
}
