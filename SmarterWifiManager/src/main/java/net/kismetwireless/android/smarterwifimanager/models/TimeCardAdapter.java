package net.kismetwireless.android.smarterwifimanager.models;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.doomonafireball.betterpickers.timepicker.TimePickerBuilder;
import com.doomonafireball.betterpickers.timepicker.TimePickerDialogFragment;

import net.kismetwireless.android.smarterwifimanager.R;
import net.kismetwireless.android.smarterwifimanager.SmarterApplication;
import net.kismetwireless.android.smarterwifimanager.services.SmarterWifiServiceBinder;

import java.util.ArrayList;

import javax.inject.Inject;

/**
 * Created by dragorn on 6/19/15.
 */
public class TimeCardAdapter extends RecyclerView.Adapter<TimeCardAdapter.ViewHolder> {
    @Inject
    Context context;

    @Inject
    SmarterWifiServiceBinder smarterWifiServiceBinder;

    AppCompatActivity activity;

    ArrayList<SmarterTimeRange> timeRanges;

    RecyclerView recyclerView;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        SmarterTimeRange timeRange;

        // Containers we pass to the hide/show function
        LinearLayout timeStartContainer, timeEndContainer;

        CheckBox wifiCb, bluetoothCb;
        CompoundButton wifiSwitch, bluetoothSwitch, enableSwitch;

        // Clock
        TextView startHours, startMinutes, startAmPm, endHours, endMinutes, endAmPm;

        // Blue/bold day picker text
        TextView repMon, repTue, repWed, repThu, repFri, repSat, repSun;

        // Red fail text
        TextView errorText2;

        public ViewHolder(View v) {
            super(v);

            timeStartContainer = (LinearLayout) v.findViewById(R.id.timeLayoutStart);
            timeEndContainer = (LinearLayout) v.findViewById(R.id.timeLayoutEnd);

            startHours = (TextView) v.findViewById(R.id.timeStartHours);
            startMinutes = (TextView) v.findViewById(R.id.timeStartMinutes);
            endHours = (TextView) v.findViewById(R.id.timeEndHours);
            endMinutes = (TextView) v.findViewById(R.id.timeEndMinutes);
            startAmPm = (TextView) v.findViewById(R.id.timeStart12hr);
            endAmPm = (TextView) v.findViewById(R.id.timeEnd12hr);

            wifiCb = (CheckBox) v.findViewById(R.id.wifiCheckbox);
            wifiSwitch = (CompoundButton) v.findViewById(R.id.wifiSwitch);

            bluetoothCb = (CheckBox) v.findViewById(R.id.bluetoothCheckbox);
            bluetoothSwitch = (CompoundButton) v.findViewById(R.id.bluetoothSwitch);

            enableSwitch = (CompoundButton) v.findViewById(R.id.timeRangeToggle);

            repMon = (TextView) v.findViewById(R.id.dayMon);
            repTue = (TextView) v.findViewById(R.id.dayTue);
            repWed = (TextView) v.findViewById(R.id.dayWed);
            repThu = (TextView) v.findViewById(R.id.dayThu);
            repFri = (TextView) v.findViewById(R.id.dayFri);
            repSat = (TextView) v.findViewById(R.id.daySat);
            repSun = (TextView) v.findViewById(R.id.daySun);

            errorText2 = (TextView) v.findViewById(R.id.errorView2);
        }
    }

    ItemTouchHelper.SimpleCallback swipeItemCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            return false;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            TimeCardAdapter.ViewHolder tcHolder = (TimeCardAdapter.ViewHolder) viewHolder;

            deleteTimeRange(tcHolder.timeRange);
        }
    };

    public TimeCardAdapter(Context context, AppCompatActivity activity, ArrayList<SmarterTimeRange> timeRanges) {
        this.timeRanges = timeRanges;

        SmarterApplication.get(context).inject(this);

        this.activity = activity;
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView v) {
        super.onAttachedToRecyclerView(v);

        recyclerView = v;

        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeItemCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

    }

    @Override
    public TimeCardAdapter.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                   int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                               .inflate(R.layout.time_entry, parent, false);

        ViewHolder vh = new ViewHolder(v);
        return vh;
    }

     // Replace the contents of a view (invoked by the layout manager)
    @Override
    public void onBindViewHolder(ViewHolder h, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        final SmarterTimeRange item = timeRanges.get(position);

        // There should be something smarter to do here
        final ViewHolder holder = h;

        h.timeRange = item;

        int failcode = item.getRangeValid();

        if (failcode < 0) {
            holder.errorText2.setVisibility(View.GONE);
        } else {
            holder.errorText2.setText(failcode);
            holder.errorText2.setVisibility(View.VISIBLE);
        }

        // There are more efficient ways of doing this but it only happens in this one
        // view so...  who cares.
        int dayRep = item.getDays();

        if ((dayRep & SmarterTimeRange.REPEAT_MON) != 0) {
            holder.repMon.setTextColor(context.getResources().getColor(R.color.blue));
            holder.repMon.setTypeface(null, Typeface.BOLD);
        } else {
            holder.repMon.setTextColor(context.getResources().getColor(R.color.white));
            holder.repMon.setTypeface(null, Typeface.NORMAL);
        }
        if ((dayRep & SmarterTimeRange.REPEAT_TUE) != 0) {
            holder.repTue.setTextColor(context.getResources().getColor(R.color.blue));
            holder.repTue.setTypeface(null, Typeface.BOLD);
        } else {
            holder.repTue.setTextColor(context.getResources().getColor(R.color.white));
            holder.repTue.setTypeface(null, Typeface.NORMAL);
        }
        if ((dayRep & SmarterTimeRange.REPEAT_WED) != 0) {
            holder.repWed.setTextColor(context.getResources().getColor(R.color.blue));
            holder.repWed.setTypeface(null, Typeface.BOLD);
        } else {
            holder.repWed.setTextColor(context.getResources().getColor(R.color.white));
            holder.repWed.setTypeface(null, Typeface.NORMAL);
        }
        if ((dayRep & SmarterTimeRange.REPEAT_THU) != 0) {
            holder.repThu.setTextColor(context.getResources().getColor(R.color.blue));
            holder.repThu.setTypeface(null, Typeface.BOLD);
        } else {
            holder.repThu.setTextColor(context.getResources().getColor(R.color.white));
            holder.repThu.setTypeface(null, Typeface.NORMAL);
        }
        if ((dayRep & SmarterTimeRange.REPEAT_FRI) != 0) {
            holder.repFri.setTextColor(context.getResources().getColor(R.color.blue));
            holder.repFri.setTypeface(null, Typeface.BOLD);
        } else {
            holder.repFri.setTextColor(context.getResources().getColor(R.color.white));
            holder.repFri.setTypeface(null, Typeface.NORMAL);
        }
        if ((dayRep & SmarterTimeRange.REPEAT_SAT) != 0) {
            holder.repSat.setTextColor(context.getResources().getColor(R.color.blue));
            holder.repSat.setTypeface(null, Typeface.BOLD);
        } else {
            holder.repSat.setTextColor(context.getResources().getColor(R.color.white));
            holder.repSat.setTypeface(null, Typeface.NORMAL);
        }
        if ((dayRep & SmarterTimeRange.REPEAT_SUN) != 0) {
            holder.repSun.setTextColor(context.getResources().getColor(R.color.blue));
            holder.repSun.setTypeface(null, Typeface.BOLD);
        } else {
            holder.repSun.setTextColor(context.getResources().getColor(R.color.white));
            holder.repSun.setTypeface(null, Typeface.NORMAL);
        }

        holder.repMon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int d = item.getDays();

                if ((d & SmarterTimeRange.REPEAT_MON) != 0)
                    d &= ~SmarterTimeRange.REPEAT_MON;
                else
                    d |= SmarterTimeRange.REPEAT_MON;

                item.setDays(d);

                notifyDataSetChanged();
            }
        });
        holder.repTue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int d = item.getDays();

                if ((d & SmarterTimeRange.REPEAT_TUE) != 0)
                    d &= ~SmarterTimeRange.REPEAT_TUE;
                else
                    d |= SmarterTimeRange.REPEAT_TUE;

                item.setDays(d);

                notifyDataSetChanged();
            }
        });
        holder.repWed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int d = item.getDays();

                if ((d & SmarterTimeRange.REPEAT_WED) != 0)
                    d &= ~SmarterTimeRange.REPEAT_WED;
                else
                    d |= SmarterTimeRange.REPEAT_WED;

                item.setDays(d);

                notifyDataSetChanged();
            }
        });
        holder.repThu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int d = item.getDays();

                if ((d & SmarterTimeRange.REPEAT_THU) != 0)
                    d &= ~SmarterTimeRange.REPEAT_THU;
                else
                    d |= SmarterTimeRange.REPEAT_THU;

                item.setDays(d);

                notifyDataSetChanged();
            }
        });
        holder.repFri.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int d = item.getDays();

                if ((d & SmarterTimeRange.REPEAT_FRI) != 0)
                    d &= ~SmarterTimeRange.REPEAT_FRI;
                else
                    d |= SmarterTimeRange.REPEAT_FRI;

                item.setDays(d);

                notifyDataSetChanged();
            }
        });
        holder.repSat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int d = item.getDays();

                if ((d & SmarterTimeRange.REPEAT_SAT) != 0)
                    d &= ~SmarterTimeRange.REPEAT_SAT;
                else
                    d |= SmarterTimeRange.REPEAT_SAT;

                item.setDays(d);

                notifyDataSetChanged();
            }
        });
        holder.repSun.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int d = item.getDays();

                if ((d & SmarterTimeRange.REPEAT_SUN) != 0)
                    d &= ~SmarterTimeRange.REPEAT_SUN;
                else
                    d |= SmarterTimeRange.REPEAT_SUN;

                item.setDays(d);

                notifyDataSetChanged();
            }
        });

        holder.wifiCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                item.setWifiControlled(b);
                if (b) {
                    holder.wifiSwitch.setVisibility(View.VISIBLE);
                } else {
                    holder.wifiSwitch.setVisibility(View.GONE);
                }

                notifyDataSetChanged();
            }
        });

        holder.bluetoothCb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                item.setBluetoothControlled(b);
                if (b) {
                    holder.bluetoothSwitch.setVisibility(View.VISIBLE);
                } else {
                    holder.bluetoothSwitch.setVisibility(View.GONE);
                }

                notifyDataSetChanged();
            }
        });

        holder.wifiSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                item.setWifiEnabled(b);
                notifyDataSetChanged();
            }
        });

        holder.bluetoothSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                item.setBluetoothEnabled(b);
                notifyDataSetChanged();
            }
        });


        holder.startHours.setText(String.format("%02d", SmarterTimeRange.getHuman12Hour(item.getStartHour())));
        holder.startMinutes.setText(String.format("%02d", item.getStartMinute()));
        holder.startAmPm.setText(SmarterTimeRange.getHumanAmPm(item.getStartHour()) ? "AM" : "PM");

        holder.endHours.setText(String.format("%02d", SmarterTimeRange.getHuman12Hour(item.getEndHour())));
        holder.endMinutes.setText(String.format("%02d", item.getEndMinute()));
        holder.endAmPm.setText(SmarterTimeRange.getHumanAmPm(item.getEndHour()) ? "AM" : "PM");

        holder.wifiCb.setChecked(item.getWifiControlled());
        holder.wifiSwitch.setChecked(item.getWifiEnabled());
        holder.wifiSwitch.setVisibility(item.getWifiControlled() ? View.VISIBLE : View.GONE);

        holder.bluetoothCb.setChecked(item.getBluetoothControlled());
        holder.bluetoothSwitch.setChecked(item.getBluetoothEnabled());
        holder.bluetoothSwitch.setVisibility(item.getBluetoothControlled() ? View.VISIBLE : View.GONE);

        holder.enableSwitch.setChecked(item.getEnabled());

        holder.enableSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                item.setEnabled(b);
                // Set enabled/disabled at the end
                // smarterWifiServiceBinder.updateTimeRangeEnabled(item);

                // Disable, don't allow clicking things
                if (!b) {
                    holder.timeStartContainer.setClickable(false);
                    holder.timeEndContainer.setClickable(false);
                    holder.timeStartContainer.setEnabled(false);
                    holder.timeEndContainer.setEnabled(false);
                }

                // Enabled, turn on features again
                if (b) {
                    holder.timeStartContainer.setClickable(true);
                    holder.timeEndContainer.setClickable(true);
                    holder.timeStartContainer.setEnabled(true);
                    holder.timeEndContainer.setEnabled(true);
                }

                notifyDataSetChanged();
            }
        });

        // Start and end time launch time pickers
        holder.timeStartContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                item.setCollapsed(false);
                TimePickerBuilder tpb = new TimePickerBuilder();
                tpb.setFragmentManager(activity.getSupportFragmentManager());
                tpb.setStyleResId(R.style.BetterPickersDialogFragment);
                tpb.addTimePickerDialogHandler(new TimePickerDialogFragment.TimePickerDialogHandler() {
                    @Override
                    public void onDialogTimeSet(int reference, int hourOfDay, int minute) {
                        item.setStartTime(hourOfDay, minute);
                        notifyDataSetChanged();
                    }
                });
                tpb.show();
                notifyDataSetChanged();
            }
        });

        holder.timeEndContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                item.setCollapsed(false);
                TimePickerBuilder tpb = new TimePickerBuilder();
                tpb.setFragmentManager(activity.getSupportFragmentManager());
                tpb.setStyleResId(R.style.BetterPickersDialogFragment);
                tpb.addTimePickerDialogHandler(new TimePickerDialogFragment.TimePickerDialogHandler() {
                    @Override
                    public void onDialogTimeSet(int reference, int hourOfDay, int minute) {
                        item.setEndTime(hourOfDay, minute);
                        notifyDataSetChanged();
                    }
                });
                tpb.show();
                notifyDataSetChanged();
            }
        });
    }

    public int getItemCount() {
        return timeRanges.size();
    }

    private void deleteTimeRange(final SmarterTimeRange item) {
        if (item == null) {
            return;
        }

        timeRanges.remove(item);
        smarterWifiServiceBinder.deleteTimeRange(item);

        // Avoid hitting it during a recalculation
        Handler h = new Handler();
        h.post(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
            }
        });

        Snackbar.make(recyclerView, R.string.snackbar_delete_timerange, Snackbar.LENGTH_LONG)
                .setAction(R.string.snackbar_delete_undo, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        timeRanges.add(item);
                        notifyDataSetChanged();
                    }
                })
                .show();
    }
}
