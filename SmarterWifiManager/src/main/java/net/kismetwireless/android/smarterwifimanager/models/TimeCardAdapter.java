package net.kismetwireless.android.smarterwifimanager.models;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.doomonafireball.betterpickers.timepicker.TimePickerBuilder;
import com.doomonafireball.betterpickers.timepicker.TimePickerDialogFragment;

import net.kismetwireless.android.smarterwifimanager.R;
import net.kismetwireless.android.smarterwifimanager.SmarterApplication;
import net.kismetwireless.android.smarterwifimanager.services.SmarterWifiService;

import java.util.ArrayList;

import javax.inject.Inject;

/**
 * Created by dragorn on 6/19/15.
 */
public class TimeCardAdapter extends RecyclerView.Adapter<TimeCardAdapter.ViewHolder> {
    @Inject
    Context context;

    @Inject
    SmarterWifiService smarterWifiService;

    AppCompatActivity activity;

    ArrayList<SmarterTimeRange> timeRanges;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        // Containers we pass to the hide/show function
        LinearLayout timeStartContainer, timeEndContainer, expandView, collapseView;

        CheckBox wifiCb, bluetoothCb;
        CompoundButton wifiSwitch, bluetoothSwitch, enableSwitch;

        // Clock
        TextView startHours, startMinutes, startAmPm, endHours, endMinutes, endAmPm;

        // Expand/collapse are two layouts for simplicity
        LinearLayout collapsedMain, expandedMain;

        // Main image buttons
        ImageView deleteCollapse, deleteExpand, undoExpand, saveExpand;

        // Blue/bold day picker text
        TextView repMon, repTue, repWed, repThu, repFri, repSat, repSun;

        // Red fail text
        TextView errorText1, errorText2;
        TextView summaryView;

        public ViewHolder(View v) {
            super(v);

            collapsedMain = (LinearLayout) v.findViewById(R.id.collapsedMainLayout);
            expandedMain = (LinearLayout) v.findViewById(R.id.expandedMainLayout);
            expandView = (LinearLayout) v.findViewById(R.id.expandView);
            collapseView = (LinearLayout) v.findViewById(R.id.collapseView);

            deleteCollapse = (ImageView) v.findViewById(R.id.timeRangeDeleteCollapse);
            deleteExpand = (ImageView) v.findViewById(R.id.timeRangeDelete);
            undoExpand = (ImageView) v.findViewById(R.id.timeRangeUndo);
            saveExpand= (ImageView) v.findViewById(R.id.timeRangeSave);

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

            summaryView = (TextView) v.findViewById(R.id.rangeSummaryText);

            errorText1 = (TextView) v.findViewById(R.id.errorView1);
            errorText2 = (TextView) v.findViewById(R.id.errorView2);
        }
    }

    public TimeCardAdapter(Context context, AppCompatActivity activity, ArrayList<SmarterTimeRange> timeRanges) {
        this.timeRanges = timeRanges;

        SmarterApplication.get(context).inject(this);

        this.activity = activity;
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

        int failcode = item.getRangeValid();

        if (failcode < 0) {
            holder.errorText1.setVisibility(View.GONE);
            holder.errorText2.setVisibility(View.GONE);
            holder.summaryView.setVisibility(View.VISIBLE);
        } else {
            holder.errorText1.setText(failcode);
            holder.errorText2.setText(failcode);
            holder.errorText1.setVisibility(View.VISIBLE);
            holder.errorText2.setVisibility(View.VISIBLE);
            holder.summaryView.setVisibility(View.GONE);
        }

        if (item.getDirty()) {
            if (item.getRevertable())
                toggleImageViewEnable(holder.undoExpand, true);
            else
                toggleImageViewEnable(holder.undoExpand, false);

            // toggleImageViewEnable(saveExpand, true);
            // Save turns back into save
            holder.saveExpand.setImageResource(R.drawable.ic_action_save);
        } else {
            toggleImageViewEnable(holder.undoExpand, false);

            //toggleImageViewEnable(saveExpand, false);
            // Turn save into collapse
            holder.saveExpand.setImageResource(R.drawable.navigation_collapse);
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

        collapseView(holder, item.getCollapsed(), item);

        holder.enableSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                item.setEnabled(b);
                smarterWifiService.updateTimeRangeEnabled(item);

                // Disable and open, close
                if (!b) {
                    holder.timeStartContainer.setClickable(false);
                    holder.timeEndContainer.setClickable(false);
                    holder.timeStartContainer.setEnabled(false);
                    holder.timeEndContainer.setEnabled(false);

                    if (!item.getCollapsed()) {
                        item.setCollapsed(true);
                        collapseView(holder, item.getCollapsed(), item);
                    }
                }

                // Enable and closed, open
                if (b) {
                    holder.timeStartContainer.setClickable(true);
                    holder.timeEndContainer.setClickable(true);
                    holder.timeStartContainer.setEnabled(true);
                    holder.timeEndContainer.setEnabled(true);

                    if (item.getCollapsed()) {
                        item.setCollapsed(false);
                        collapseView(holder, item.getCollapsed(), item);
                    }
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

        holder.collapseView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                item.setCollapsed(!item.getCollapsed());

                collapseView(holder, item.getCollapsed(), item);
            }
        });

        holder.deleteCollapse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deleteDialog(item);
            }
        });

        holder.deleteExpand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                deleteDialog(item);
            }
        });

        holder.undoExpand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!item.getRevertable())
                    return;

                item.revertChanges();

                notifyDataSetChanged();
            }
        });

        holder.saveExpand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!item.getDirty()) {
                    // We're a collapse button
                    item.setCollapsed(true);
                    collapseView(holder, item.getCollapsed(), item);
                    notifyDataSetChanged();
                    return;
                }

                // Otherwise save
                smarterWifiService.updateTimeRange(item);
                item.applyChanges();
                notifyDataSetChanged();
            }
        });

    }

    public int getItemCount() {
        return timeRanges.size();
    }

    private void collapseView(final ViewHolder holder, boolean collapse, SmarterTimeRange item) {
        // Extract from the main views
        TextView daysRepeatView = (TextView) holder.collapseView.findViewById(R.id.daysRepeatCollapse);

        TextView summaryView = (TextView) holder.collapsedMain.findViewById(R.id.rangeSummaryText);

        if (!item.getEnabled()) {
            summaryView.setText(R.string.timerange_disabled_text);
        } else {
            if (item.getDays() == 0) {
                summaryView.setText(R.string.timerange_no_days);
            } else if (!item.getBluetoothControlled() && !item.getWifiControlled()) {
                summaryView.setText(context.getString(R.string.timerange_no_effect));
            } else {
                StringBuilder sb = new StringBuilder();

                if (item.getWifiControlled()) {
                    sb.append(context.getString(R.string.timerange_control_wifi));
                    sb.append(" ");
                    if (item.getWifiEnabled())
                        sb.append(context.getString(R.string.timerange_control_on));
                    else
                        sb.append(context.getString(R.string.timerange_control_off));
                }

                if (item.getBluetoothControlled()) {
                    if (sb.length() > 0)
                        sb.append(", ");

                    sb.append(context.getString(R.string.timerange_control_bluetooth));
                    sb.append(" ");
                    if (item.getBluetoothEnabled())
                        sb.append(context.getString(R.string.timerange_control_on));
                    else
                        sb.append(context.getString(R.string.timerange_control_off));
                }

                summaryView.setText(sb.toString());
            }
        }

        if (collapse) {
            holder.collapseView.setVisibility(View.GONE);
            holder.expandView.setVisibility(View.VISIBLE);

            holder.collapsedMain.setVisibility(View.VISIBLE);
            holder.expandedMain.setVisibility(View.GONE);

            daysRepeatView.setText(SmarterTimeRange.getHumanDayText(context, item.getDays()));
        } else {
            holder.collapseView.setVisibility(View.VISIBLE);
            holder.expandView.setVisibility(View.GONE);

            holder.collapsedMain.setVisibility(View.GONE);
            holder.expandedMain.setVisibility(View.VISIBLE);
        }
    }

    private void toggleImageViewEnable(ImageView v, boolean b) {
        // If we dont' leave the background view clickable, we can use this to disable the buttons
        v.setEnabled(b);
        v.setClickable(b);

        if (b) {
            AlphaAnimation alpha = new AlphaAnimation(1.0F, 1.0F);
            alpha.setDuration(0);
            alpha.setFillAfter(true);
            v.startAnimation(alpha);

                /* We don't need this if we dont' use the whole background row as a clickable collapse; leave for reference
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
                    TypedArray a = context.obtainStyledAttributes(new int[] { android.R.attr.selectableItemBackground });
                    int resource = a.getResourceId(0, 0);
                    a.recycle();

                    v.setBackground(context.getResources().getDrawable(resource));
                }
                */
        } else {
            AlphaAnimation alpha = new AlphaAnimation(0.5F, 0.5F);
            alpha.setDuration(0);
            alpha.setFillAfter(true);
            v.startAnimation(alpha);
            // v.setBackground(null);
        }
    }

    // TODO change this to automatic delete with a snackbar to undo
    private void deleteDialog(final SmarterTimeRange item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        builder.setTitle(R.string.timerange_dialog_delete_title);
        builder.setMessage(R.string.timerange_dialog_delete_text);

        builder.setNegativeButton(R.string.timerange_dialog_delete_cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {

            }
        });

        /*
        builder.setPositiveButton(R.string.timerange_dialog_delete_ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                serviceBinder.deleteTimeRange(item);

                lastTimeList.remove(item);
                listAdapter.notifyDataSetChanged();

                if (lastTimeList.size() <= 0) {
                    emptyView.setVisibility(View.VISIBLE);
                    lv.setVisibility(View.GONE);
                } else {
                    emptyView.setVisibility(View.GONE);
                    lv.setVisibility(View.VISIBLE);
                }
            }
        });
        */

        builder.create().show();
    }
}
