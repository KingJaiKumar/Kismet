package net.kismetwireless.android.smarterwifimanager.models;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;

import net.kismetwireless.android.smarterwifimanager.LogAlias;
import net.kismetwireless.android.smarterwifimanager.R;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * Created by dragorn on 10/1/13.
 *
 * This has to be parcelable because we can keep un-saved changes in fragments
 */
public class SmarterTimeRange implements Parcelable {
    public static int REPEAT_SUN = (1 << Calendar.SUNDAY);
    public static int REPEAT_MON = (1 << Calendar.MONDAY);
    public static int REPEAT_TUE = (1 << Calendar.TUESDAY);
    public static int REPEAT_WED = (1 << Calendar.WEDNESDAY);
    public static int REPEAT_THU = (1 << Calendar.THURSDAY);
    public static int REPEAT_FRI = (1 << Calendar.FRIDAY);
    public static int REPEAT_SAT = (1 << Calendar.SATURDAY);

    private int startHour, startMinute;
    private int endHour, endMinute;
    private int days;

    private int oldStartHour, oldStartMinute, oldEndHour, oldEndMinute, oldDays;

    private boolean controlWifi, controlBluetooth;
    private boolean wifiOn, bluetoothOn;

    private boolean oldControlWifi, oldControlBluetooth, oldWifiOn, oldBluetoothOn;

    private boolean aggressive = false, oldAggressive;

    private boolean dirty = false;

    // Hide the UI collapse state in here
    private boolean collapsed = true;
    // Is this alarm enabled
    private boolean enabled = true;

    private long dbid = -1;

    // Expanded duration for simple math
    private class DurationSlice {
        long adjustedMinuteOfWeek;
        long durationMinutes;
    }

    private ArrayList<DurationSlice> expandedDurations = new ArrayList<DurationSlice>();

    public SmarterTimeRange() {
        dbid = -1;
    }

    public SmarterTimeRange(int starthour, int startminute, int endhour, int endminute, int days,
                            boolean controlwifi, boolean controlbt, boolean wifion, boolean bton,
                            boolean enabled, boolean aggressive, long id) {
        oldStartHour = startHour = starthour;
        oldStartMinute = startMinute = startminute;
        oldEndHour = endHour = endhour;
        oldEndMinute = endMinute = endminute;
        this.oldDays = this.days = days;
        oldControlWifi = controlWifi = controlwifi;
        oldControlBluetooth = controlBluetooth = controlbt;
        oldWifiOn = wifiOn = wifion;
        oldBluetoothOn = bluetoothOn = bton;

        this.enabled = enabled;
        dirty = false;

        dbid = id;
    }

    public SmarterTimeRange(SmarterTimeRange r) {
        this.startHour = r.getStartHour();
        this.startMinute = r.getStartMinute();
        this.endHour = r.getEndHour();
        this.endMinute = r.getEndMinute();
        this.days = r.getDays();
        this.controlWifi = r.getWifiControlled();
        this.wifiOn = r.getWifiEnabled();
        this.controlBluetooth = r.getBluetoothControlled();
        this.bluetoothOn = r.getBluetoothEnabled();
        this.enabled = r.getEnabled();

        this.dbid = r.getDbId();

        dirty = false;
    }

    public static long getNowInMinutes() {
        /*
        Calendar c = GregorianCalendar.getInstance();

        return (c.get(Calendar.DAY_OF_WEEK) * 1440) + (c.get(Calendar.HOUR_OF_DAY) * 60) + c.get(Calendar.MINUTE);
        */

        return System.currentTimeMillis() / 60000;
    }

    public long getDurationMinutes() {
        // Arbitrary day
        int d = 1;

        long weekstart = (1440 * (d - 1)) + (60 * startHour) + startMinute;

        long duration;
        int endday = d;
        if (getCrossesDays()) {
            endday += 1;
        }

        long endtime = (1440 * (endday - 1)) + (60 * endHour) + endMinute;

        duration = endtime - weekstart;

        return duration;
    }

    public boolean getCrossesDays() {
        if (endHour < startHour || (endHour == startHour && endMinute < startMinute)) {
            return true;
        }

        return false;
    }

    // Returns '0' or duration of current element
    public boolean isInRangeNow() {
        // Blow up our time ranges
        expandTimeDurations();

        // Now since epoch converted to minute precision
        long now = System.currentTimeMillis() / 60000;

        for (DurationSlice d : expandedDurations) {
            if (now < d.adjustedMinuteOfWeek)
                continue;

            if (now < d.adjustedMinuteOfWeek + d.durationMinutes)
                return true;
        }

        return false;
    }

    public long getNextStartMillis() {
        expandTimeDurations();

        long now = System.currentTimeMillis() / 60000;

        long closestmin = 0;

        for (DurationSlice d : expandedDurations) {
            if (now > d.adjustedMinuteOfWeek)
                continue;

            if (closestmin == 0 || d.adjustedMinuteOfWeek < closestmin)
                closestmin = d.adjustedMinuteOfWeek;
        }

        return closestmin * 60000;
    }

    public long getNextEndMillis() {
        expandTimeDurations();

        long now = System.currentTimeMillis() / 60000;

        long closestmin = 0;

        for (DurationSlice d : expandedDurations) {
            long endminute = d.adjustedMinuteOfWeek + d.durationMinutes;

            if (now > endminute)
                continue;

            if (closestmin == 0 || endminute < closestmin)
                closestmin = endminute;
        }

        return closestmin * 60000;
    }

    public void expandTimeDurations() {
        if (expandedDurations.size() != 0 && !dirty)
            return;

        Calendar c = GregorianCalendar.getInstance();

        // Midnight
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.clear(Calendar.MINUTE);
        c.clear(Calendar.SECOND);
        c.clear(Calendar.MILLISECOND);

        /*
        // Decrement via localization
        while (c.get(Calendar.DAY_OF_WEEK) != c.getFirstDayOfWeek()) {
            c.add(Calendar.DATE, -1);
        }
        */

        // Slide back to the beginning of the week, sunday
        c.add(Calendar.DATE, Calendar.SUNDAY - c.get(Calendar.DAY_OF_WEEK));

        LogAlias.d("smarter", "Basing calendar calculation on " + new Date(c.getTimeInMillis()));

        // Get the start of the week, in seconds, since the epoch
        long adjustment = c.getTimeInMillis() / 60000;

        long duration = getDurationMinutes();

        expandedDurations.clear();

        // Go through all the days in the week, sunday to saturday
        for (int d = Calendar.SUNDAY; d <= Calendar.SATURDAY; d++) {
            // For days we're active in
            if ((days & (1 << d)) == 0)
                continue;

            // Start time in minutes
            long weekstart = (1440 * (d - 1)) + (60 * startHour) + startMinute;

            // If we repeat on saturday and end on sunday, we need to make a record a day earlier
            // that carries into sunday; which is a week earlier from our current calculation point,
            // which is the end of the week
            if (d == Calendar.SATURDAY && getCrossesDays()) {
                DurationSlice ds = new DurationSlice();
                ds.adjustedMinuteOfWeek = weekstart + adjustment - (7 * 1440);
                ds.durationMinutes = duration;

                LogAlias.d("smarter", "Occurrence at " + (new Date(ds.adjustedMinuteOfWeek * 60000)).toString() + " until " + (new Date((ds.adjustedMinuteOfWeek + ds.durationMinutes) * 60000)).toString());

                expandedDurations.add(ds);
            }

            DurationSlice ds = new DurationSlice();

            // ds.startMinuteOfWeek = weekstart;
            ds.adjustedMinuteOfWeek = weekstart + adjustment;
            ds.durationMinutes = (int) duration;

            expandedDurations.add(ds);

            LogAlias.d("smarter", "Occurrence at " + (new Date(ds.adjustedMinuteOfWeek * 60000)).toString() + " until " + (new Date((ds.adjustedMinuteOfWeek + ds.durationMinutes) * 60000)).toString());
        }

    }

    public int getStartHour() {
        return startHour;
    }

    public int getEndHour() {
        return endHour;
    }

    public int getStartMinute() {
        return startMinute;
    }

    public int getEndMinute() {
        return endMinute;
    }

    public int getDays() {
        return days;
    }

    public void setDays(int repeats) {
        if (days != repeats) {
            dirty = true;
            expandedDurations.clear();
        }

        oldDays = days;

        days = repeats;
    }

    public void setStartTime(int starthour, int startminute) {
        if (startHour != starthour || startMinute != startminute) {
            dirty = true;
            expandedDurations.clear();
        }

        oldStartHour = startHour;
        oldStartMinute = startMinute;

        startHour = starthour;
        startMinute = startminute;
    }

    public void setEndTime(int endhour, int endminute) {
        if (endHour != endhour || endMinute != endminute) {
            dirty = true;
            expandedDurations.clear();
        }
        //Log.d("smarter", "setendtime " + dirty);

        oldEndHour = endHour;
        oldEndMinute = endMinute;

        endHour = endhour;
        endMinute = endminute;
    }

    public long getDbId() {
        return dbid;
    }

    public void setDbId(long id) {
        dbid = id;
    }

    public boolean getAggressiveManagement() {
        return aggressive;
    }

    public void setAggressiveManagement(boolean agg) {
        if (aggressive != agg) {
            dirty = true;
        }

        oldAggressive = aggressive;
        aggressive = agg;
    }

    public boolean getWifiControlled() {
        return controlWifi;
    }

    public void setWifiControlled(boolean control) {
        if (controlWifi != control) {
            dirty = true;
            expandedDurations.clear();
        }

        oldControlWifi = controlWifi;

        controlWifi = control;
    }

    public boolean getBluetoothControlled() {
        return controlBluetooth;
    }

    public void setBluetoothControlled(boolean control) {
        if (controlBluetooth != control) {
            dirty = true;
            expandedDurations.clear();
        }

        oldControlBluetooth = controlBluetooth;

        controlBluetooth = control;
    }

    public boolean getWifiEnabled() {
        return wifiOn;
    }

    public void setWifiEnabled(boolean en) {
        if (wifiOn != en) {
            dirty = true;
            expandedDurations.clear();
        }

        oldWifiOn = wifiOn;

        wifiOn = en;
    }

    public boolean getBluetoothEnabled() {
        return bluetoothOn;
    }

    public boolean getCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapse) {
        collapsed = collapse;
    }

    public void setBluetoothEnabled(boolean en) {
        if (bluetoothOn != en) {
            dirty = true;
            expandedDurations.clear();
        }

        oldBluetoothOn = bluetoothOn;

        bluetoothOn = en;
    }

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean e) {
        enabled = e;
    }

    public boolean getDirty() {
        return dirty;
    }

    public boolean getRevertable() {
        if (dirty && dbid >= 0)
            return true;

        return false;
    }

    public void revertChanges() {
        startHour = oldStartHour;
        startMinute = oldStartMinute;
        endHour = oldEndHour;
        endMinute = oldEndMinute;
        days = oldDays;
        controlWifi = oldControlWifi;
        wifiOn = oldWifiOn;
        controlBluetooth = oldControlBluetooth;
        bluetoothOn = oldBluetoothOn;
        aggressive = oldAggressive;

        dirty = false;

        expandedDurations.clear();
    }

    public void applyChanges() {
        oldStartHour = startHour;
        oldStartMinute = startMinute;
        oldEndHour = endHour;
        oldEndMinute = endMinute;
        oldDays = days;
        oldControlWifi = controlWifi;
        oldWifiOn = wifiOn;
        oldControlBluetooth = controlBluetooth;
        oldBluetoothOn = bluetoothOn;

        dirty = false;

        expandedDurations.clear();
    }

    static public int getHuman12Hour(int hour) {
        // 12am
        if (hour == 0)
            return 12;

        // 1am - 12pm
        if (hour <= 12)
            return hour;

        // 1pm to 12am
        return getHuman12Hour(hour - 12);
    }

    // am = true
    // pm = false
    static public boolean getHumanAmPm(int hour) {
        // 12am - 11am
        if (hour < 12)
            return true;

        return false;
    }

    static public String getHumanDayText(Context c, int repeat) {
        StringBuilder sb = new StringBuilder();

        if ((repeat & REPEAT_MON) != 0) {
            sb.append(c.getString(R.string.range_mon));
        }
        if ((repeat & REPEAT_TUE) != 0) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(c.getString(R.string.range_tue));
        }
        if ((repeat & REPEAT_WED) != 0) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(c.getString(R.string.range_wed));
        }
        if ((repeat & REPEAT_THU) != 0) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(c.getString(R.string.range_thu));
        }
        if ((repeat & REPEAT_FRI) != 0) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(c.getString(R.string.range_fri));
        }
        if ((repeat & REPEAT_SAT) != 0) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(c.getString(R.string.range_sat));
        }
        if ((repeat & REPEAT_SUN) != 0) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(c.getString(R.string.range_sun));
        }

        return sb.toString();
    }

    public SmarterTimeRange(Parcel source) {
        enabled = (source.readInt() == 1);
        collapsed = (source.readInt() == 1);

        startHour = source.readInt();
        startMinute = source.readInt();
        endHour = source.readInt();
        endMinute = source.readInt();
        days = source.readInt();
        controlWifi = (source.readInt() == 1);
        wifiOn = (source.readInt() == 1);
        controlBluetooth = (source.readInt() == 1);
        bluetoothOn = (source.readInt() == 1);
        dbid = source.readLong();
        aggressive = (source.readInt() == 1);

        oldStartHour = source.readInt();
        oldStartMinute = source.readInt();
        oldEndHour = source.readInt();
        oldEndMinute = source.readInt();
        oldDays = source.readInt();
        oldControlWifi = (source.readInt() == 1);
        oldWifiOn = (source.readInt() == 1);
        oldControlBluetooth = (source.readInt() == 1);
        oldBluetoothOn = (source.readInt() == 1);
        oldAggressive = (source.readInt() == 1);

    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(enabled ? 1 : 0);
        dest.writeInt(collapsed ? 1 : 0);

        dest.writeInt(startHour);
        dest.writeInt(startMinute);
        dest.writeInt(endHour);
        dest.writeInt(endMinute);
        dest.writeInt(days);
        dest.writeInt(controlWifi ? 1 : 0);
        dest.writeInt(wifiOn ? 1 : 0);
        dest.writeInt(controlBluetooth ? 1 : 0);
        dest.writeInt(bluetoothOn ? 1 : 0);
        dest.writeLong(dbid);
        dest.writeInt(aggressive ? 1 : 0);

        dest.writeInt(oldStartHour);
        dest.writeInt(oldStartMinute);
        dest.writeInt(oldEndHour);
        dest.writeInt(oldEndMinute);
        dest.writeInt(oldDays);
        dest.writeInt(oldControlWifi ? 1 : 0);
        dest.writeInt(oldWifiOn ? 1 : 0);
        dest.writeInt(oldControlBluetooth ? 1 : 0);
        dest.writeInt(oldBluetoothOn ? 1 : 0);
        dest.writeInt(oldAggressive ? 1 : 0);
    }

    public int describeContents() {
        return hashCode();
    }

    public static final Parcelable.Creator<SmarterTimeRange> CREATOR = new Creator<SmarterTimeRange>() {
        public SmarterTimeRange createFromParcel(Parcel source) {
            return new SmarterTimeRange(source);
        }
        public SmarterTimeRange[] newArray(int size) {
            return new SmarterTimeRange[size];
        }
    };

    // returns if time range is valid, and the resource id of why not or -1
    public int getRangeValid() {
        if (!controlWifi && !controlBluetooth)
            return R.string.range_fail_nocontrol;

        if (days == 0)
            return R.string.range_fail_nodays;

        if (startHour == endHour && startMinute == endMinute)
            return R.string.range_fail_notime;

        return -1;
    }


}
