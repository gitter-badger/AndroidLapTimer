package com.pimentoso.android.laptimer;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

public class SensitivityDialogActivity extends Activity implements OnClickListener, OnSeekBarChangeListener
{
	public static final int DEFAULT_SENSITIVITY = 15;
	
	private SeekBar bar;
	private TextView barValue;
	private int currentValue;
	
	@Override
	public void onCreate(Bundle savedInstanceState)
	{	
		super.onCreate(savedInstanceState);
		setContentView(R.layout.sensitivity);
		findViewById(R.id.button_sensitivity_default).setOnClickListener(this);
		findViewById(R.id.button_sensitivity_close).setOnClickListener(this);
		
		LayoutParams params = getWindow().getAttributes();
		params.width = LayoutParams.MATCH_PARENT;
		getWindow().setAttributes((android.view.WindowManager.LayoutParams) params);
		
		currentValue = DefaultPreferences.get(this, "sensitivity", DEFAULT_SENSITIVITY);
		
		bar = (SeekBar) findViewById(R.id.seekbar_sensitivity);
		bar.setOnSeekBarChangeListener(this);
		
		barValue = (TextView) findViewById(R.id.seekbar_sensitivity_value);
		barValue.setText(Integer.toString(currentValue));
		
		bar.setProgress(currentValue);
		
		// set sensitivity value to 25-barValue
		// bar 20 = sensitivity 5
		// bar 15 = sensitivity 10
		// bar 10 = sensitivity 15
		// bar 5 = sensitivity 20
		// bar 0 = sensitivity 25
	}

	@Override
	public void onClick(View v) {
		if (v.getId() == R.id.button_sensitivity_close) {
			int finalValue = bar.getProgress();
			DefaultPreferences.put(this, "sensitivity", finalValue);
			TimerActivity.calibrateThreshold = 25 - finalValue;
			this.finish();
		}
		else if (v.getId() == R.id.button_sensitivity_default) {
			bar.setProgress(DEFAULT_SENSITIVITY);
			barValue.setText(Integer.toString(DEFAULT_SENSITIVITY));
		}
	}

	@Override
	public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {
		String t = String.valueOf(arg1);
		barValue.setText(t);
	}

	@Override
	public void onStartTrackingTouch(SeekBar arg0) {
		
	}

	@Override
	public void onStopTrackingTouch(SeekBar arg0) {
		
	}
}