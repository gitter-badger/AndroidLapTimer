package com.pimentoso.android.laptimer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class LapListAdapter extends BaseAdapter {

	private final Context context;
	private final List<Map<String, String>> values;

	public LapListAdapter(Context context, ArrayList<Long> laps) {
		super();
		this.context = context;
		
		this.values = new ArrayList<Map<String,String>>();
		Map<String, String> currentItemMap = null;
		
		long bestTime = Long.MAX_VALUE;
		int bestIndex = 0;
		long worstTime = Long.MIN_VALUE;
		int worstIndex = 0;
		
		if (laps.size() > 0) {
			for (int i = 0; i < laps.size(); i++) {
				currentItemMap = new HashMap<String, String>();
				this.values.add(currentItemMap);
				long lap = laps.get(i);
				
				if (lap > worstTime) {
					worstTime = lap;
					worstIndex = i;
				}
				
				if (lap < bestTime) {
					bestTime = lap;
					bestIndex = i;
				}
	
				currentItemMap.put("num", "Lap " + (i+1));
				currentItemMap.put("time", TimerActivity.convertTime(lap));
			}
			
			values.get(worstIndex).put("notice", "(worst)");
			values.get(bestIndex).put("notice", "(best)");
			
			Collections.reverse(values);
		}
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(R.layout.list_item_lap, parent, false);

		TextView numView = (TextView) rowView.findViewById(R.id.lap_num);
		TextView timeView = (TextView) rowView.findViewById(R.id.lap_time);
		TextView noticeView = (TextView) rowView.findViewById(R.id.lap_notice);

		String num = values.get(position).get("num");
		String time = values.get(position).get("time");
		String notice = values.get(position).get("notice");

		numView.setText(num);
		timeView.setText(time);
		noticeView.setText(notice);

		return rowView;
	}

	@Override
	public int getCount() {
		return values.size();
	}

	@Override
	public Object getItem(int pos) {
		return pos;
	}

	@Override
	public long getItemId(int pos) {
		return pos;
	}
}
