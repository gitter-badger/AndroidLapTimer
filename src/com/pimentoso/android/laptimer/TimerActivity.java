package com.pimentoso.android.laptimer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Main activity for Mini4WD Lap Timer.
 * 
 * @author Pimentoso
 */
public class TimerActivity extends Activity implements SurfaceHolder.Callback, Camera.PreviewCallback, OnClickListener {
	
	private static final long DEFAULT_CATCH_DELAY = 500; // ignore laps shorter than this
	private static final long CALIBRATION_ERROR_FRAMES = 50; // how many consecutive frames caught before calibration warning (about 2 sec) 
	
	// layout elements
	private SurfaceView mSurfaceView;
	private SurfaceHolder mSurfaceHolder;
	private Camera mCamera;

	private View cameraBar;
	private TextView timerLabel;
	private TextView statusLabel;
	private TextView fpsLabel;
	private ListView lapList;
	private LapListAdapter lapListAdapter;
	private Button startButton;
	private Button calibrateButton;
	private SoundPool soundPool;
	private int lapSound;

	// camera preview dimensions
	private int cameraWidth;
	private int cameraHeight;
	
	// flags
	private boolean isCalibrating = false;
	private boolean isCalibrated = false;
	private boolean isStarted = false;
	private boolean isTimerRunning = false;

	// frame counter during calibration
	private int frame = 0;

	// offsets of the 3 relevant pixels
	private int[] pixelOffset = new int[3];

	// color values of the 3 relevant pixels during calibration (20 frames are calculated)
	private int[][] calibrateRange = new int[3][20];

	// final color values of the 3 relevant pixels after calibration
	private int[] calibrateValue = new int[3];

	// lightness difference threshold, over which the frame is caught (= a new lap is started)
	public static int calibrateThreshold = 10;

	// last frame catch milliseconds
	private long mLastCatchTime = 0;
	
	// number of frames caught subsequentially (for calibration warning purposes)
	private int subsequentFramesCaught = 0;

	// lap times
	private ArrayList<Long> laps = new ArrayList<Long>();

	private Handler mHandler = new Handler();
	private FPSCounter fps;
	private long mStartTime = 0L;
	
	private static byte[] frameBuffer1;
	private static byte[] frameBuffer2;
	private static byte[] frameBuffer3;

	private Runnable mUpdateTimeTask = new Runnable() {

		public void run() {
			long millis = SystemClock.uptimeMillis() - mStartTime;
			timerLabel.setText(Utils.convertTime(millis));
			mHandler.postAtTime(this, SystemClock.uptimeMillis() + 40);
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		
		// force localization, for debugging purposes
		/*
		String languageToLoad  = "ja";
	    Locale locale = new Locale(languageToLoad); 
	    Locale.setDefault(locale);
	    Configuration config = new Configuration();
	    config.locale = locale;
	    getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());
	    */
		
		setContentView(R.layout.main);

		mSurfaceView = (SurfaceView) findViewById(R.id.surface_camera);
		mSurfaceHolder = mSurfaceView.getHolder();
		mSurfaceHolder.addCallback(this);

		calibrateThreshold = DefaultPreferences.get(this, "sensitivity", SensitivityDialogActivity.DEFAULT_SENSITIVITY);

		cameraBar = findViewById(R.id.camera_bar);
		timerLabel = (TextView) findViewById(R.id.text_timer);
		statusLabel = (TextView) findViewById(R.id.text_status);
		fpsLabel = (TextView) findViewById(R.id.text_fps);
		startButton = (Button) findViewById(R.id.button_start);
		calibrateButton = (Button) findViewById(R.id.button_calibrate);
		lapList = (ListView) findViewById(R.id.list_laps);

		startButton.setOnClickListener(this);
		calibrateButton.setOnClickListener(this);

		statusLabel.setText(getString(R.string.label_status_init));
		startButton.setEnabled(false);

		soundPool = new SoundPool(10, AudioManager.STREAM_NOTIFICATION, 0);
		lapSound = soundPool.load(this, R.raw.lap, 1);
		
		fps = new FPSCounter();
	}

	@Override
	public void onStart() {

		super.onStart();

		// show help
		if (DefaultPreferences.get(this, "first_time", "1").equals("1")) {
			showAlertBox();
			DefaultPreferences.put(this, "first_time", "0");
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {

	}

	@Override
	public void surfaceCreated(SurfaceHolder surface) {

		synchronized (this) {
			
			try {
				mCamera = Camera.open();
			}
			catch (RuntimeException e) {
				// camera service already in use: die
				new AlertDialog.Builder(this).setMessage(getString(R.string.error_camera_locked_text)).setTitle("Error").setCancelable(true).setIcon(android.R.drawable.ic_dialog_info).setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						TimerActivity.this.finish();
					}
				}).show();
				return;
			}

			if (mCamera == null) {
				// camera not found: die
				new AlertDialog.Builder(this).setMessage(getString(R.string.error_camera_null_text)).setTitle("Error").setCancelable(true).setIcon(android.R.drawable.ic_dialog_info).setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						TimerActivity.this.finish();
					}
				}).show();
				return;
			}

			Camera.Parameters parameters = mCamera.getParameters();

			// find smallest camera preview size (it's good for fps)
			Camera.Size smallestPreviewSize = getSmallestPreviewSize(parameters);
			
			cameraWidth = smallestPreviewSize.width;
			cameraHeight = smallestPreviewSize.height;
			parameters.setPreviewSize(cameraWidth, cameraHeight);
			Log.d("Mini4WD Lap Timer", "Camera preview size: " + cameraWidth + "x" + cameraHeight);

			// create 3 framebuffers
			int bytesPerPixel = ImageFormat.getBitsPerPixel(parameters.getPreviewFormat());
			int bufferSize = (cameraWidth * cameraHeight * bytesPerPixel) >> 3;
			
			frameBuffer1 = new byte[bufferSize];
			frameBuffer2 = new byte[bufferSize];
			frameBuffer3 = new byte[bufferSize];

			mCamera.addCallbackBuffer(frameBuffer1);
			mCamera.addCallbackBuffer(frameBuffer2);
			mCamera.addCallbackBuffer(frameBuffer3);

			/* calculate pixel offsets from the top left corner.
			 * the app takes 3 relevant pixels in the center of the camera, and checks for variation in only those 3 pixels.
			 * the pixels are positioned in the middle line of the camera surface, at 10%, 50% and 90% of width.
			 * (the camera is then rotated to portrait)
			 * 
			 *         CAMERA
			 * +--------------------+
			 * |         0          |
			 * |                    |
			 * |         1          |
			 * |                    |
			 * |         2          |
			 * +--------------------+
			 */
			pixelOffset[0] = (int) (cameraWidth / 2) + (cameraWidth * (int) (cameraHeight * 0.1));
			pixelOffset[1] = (int) (cameraWidth / 2) + (cameraWidth * (int) (cameraHeight * 0.5));
			pixelOffset[2] = (int) (cameraWidth / 2) + (cameraWidth * (int) (cameraHeight * 0.9));

			// init camera preview
			mCamera.setParameters(parameters);
			mCamera.setDisplayOrientation(90);

			try {
				mCamera.setPreviewDisplay(surface);
				mCamera.setPreviewCallbackWithBuffer(this);
				mCamera.startPreview();
			}
			catch (IOException e) {
				// startPreview failed: die
				new AlertDialog.Builder(this).setMessage(getString(R.string.error_camera_null_text)).setTitle("Error").setCancelable(true).setIcon(android.R.drawable.ic_dialog_info).setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						TimerActivity.this.finish();
					}
				}).show();
				return;
			}
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder arg0) {

		synchronized (this) {
			try {
				if (mCamera != null) {
					mCamera.setPreviewCallback(null);
					mCamera.stopPreview();
				}
			}
			catch (Exception e) {
				Log.e("Camera", e.getMessage());
			}
			finally {
				if (mCamera != null) {
					mCamera.release();
				}
			}
		}
	}

	@Override
	public void onPreviewFrame(byte[] yuv, Camera arg1) {

		// a frame has arrived from the camera: get the lightness value of the 3 relevant pixels
		int value0 = (int) yuv[pixelOffset[0]] & 0xFF;
		int value1 = (int) yuv[pixelOffset[1]] & 0xFF;
		int value2 = (int) yuv[pixelOffset[2]] & 0xFF;
		
		cameraBar.setBackgroundColor(getResources().getColor(R.color.bar_red));

		// calibrating...
		if (isCalibrating) {
			
			frame++;

			calibrateRange[0][frame - 1] = value0;
			calibrateRange[1][frame - 1] = value1;
			calibrateRange[2][frame - 1] = value2;

			if (frame >= 20) {
				// got values from 20 frames, finish calibration
				isCalibrating = false;
				isCalibrated = true;
				startButton.setEnabled(true);
				calibrateButton.setEnabled(true);
				statusLabel.setText(getString(R.string.label_status_calibrated));

				// calculate average from the 20 values
				int tot0 = 0, tot1 = 0, tot2 = 0;
				for (int i = 0; i < 20; i++) {
					tot0 += calibrateRange[0][i];
					tot1 += calibrateRange[1][i];
					tot2 += calibrateRange[2][i];
				}

				calibrateValue[0] = tot0 / 20;
				calibrateValue[1] = tot1 / 20;
				calibrateValue[2] = tot2 / 20;
			}
		}

		// calibrated, listening for lightness variations
		else if (isCalibrated) {
			
			boolean frameCaught = (value0 < calibrateValue[0] - calibrateThreshold
					|| value0 > calibrateValue[0] + calibrateThreshold
					|| value1 < calibrateValue[1] - calibrateThreshold
					|| value1 > calibrateValue[1] + calibrateThreshold
					|| value2 < calibrateValue[2] - calibrateThreshold
					|| value2 > calibrateValue[2] + calibrateThreshold);
			
			if (frameCaught) {
				subsequentFramesCaught++;
				cameraBar.setBackgroundColor(getResources().getColor(R.color.bar_green));
			}
			else {
				subsequentFramesCaught = 0;
			}

			// check if car has passed
			if (isStarted && frameCaught) {

				// caught lap: calculate time
				long catchTime = SystemClock.uptimeMillis();
				long lapTime = catchTime - mLastCatchTime;
				
				// ignore lap if previous frame was caught, and time is below threshold
				if (subsequentFramesCaught == 1 && lapTime >= DEFAULT_CATCH_DELAY) {
					if (isTimerRunning) {
						// car has passed: start a new lap
						laps.add(lapTime);
						refreshLaps();
						mLastCatchTime = catchTime;
					}
					else {
						// car has passed for the first time: start timer
						isTimerRunning = true;
						mStartTime = SystemClock.uptimeMillis();
						mLastCatchTime = mStartTime;
						mHandler.removeCallbacks(mUpdateTimeTask);
						mHandler.postDelayed(mUpdateTimeTask, 50);
						statusLabel.setText(getString(R.string.label_status_started));
					}
					
					// play sound
					soundPool.play(lapSound, 1f, 1f, 1, 0, 1f);
				}
			}
			
			if (subsequentFramesCaught > CALIBRATION_ERROR_FRAMES) {
				statusLabel.setText(getString(R.string.label_status_calibration_error));
			}
		}

		mCamera.addCallbackBuffer(yuv);
		
		// show FPS
		fps.update();
		fpsLabel.setText(fps.printFrames());
	}

	@Override
	public void onClick(View v) {

		switch (v.getId()) {
			
			case R.id.button_start: {
				
				if (!isCalibrated || isCalibrating) {
					// not calibrated
					break;
				}

				if (isStarted) {
					// clicked on start while timer was running: stop everything
					startButton.setText(getString(R.string.label_start));
					statusLabel.setText(getString(R.string.label_status_stopped));
					calibrateButton.setEnabled(true);
					isStarted = false;
					isTimerRunning = false;
					mHandler.removeCallbacks(mUpdateTimeTask);
				}
				else {
					// clicked on start while timer was stopped: start everything
					startButton.setText(getString(R.string.label_stop));
					statusLabel.setText(getString(R.string.label_status_ready));
					calibrateButton.setEnabled(false);
					timerLabel.setText("0:00:0");
					isStarted = true;
					mStartTime = 0L;

					// reset laps list
					laps = new ArrayList<Long>();

					refreshLaps();
				}

				break;
			}
			case R.id.button_calibrate: {
				
				if (isTimerRunning) {
					// cannot calibrate while timer is running
					break;
				}
				
				startButton.setEnabled(false);
				calibrateButton.setEnabled(false);

				// started calibration: need to reset some stuff
				statusLabel.setText(getString(R.string.label_status_calibrating));
				timerLabel.setText("0:00:0");

				frame = 0;
				mStartTime = 0L;
				isStarted = false;
				isTimerRunning = false;
				isCalibrating = true;
				isCalibrated = false;

				// reset lap list
				laps = new ArrayList<Long>();

				refreshLaps();

				break;
			}
		}
	}
	
	private Camera.Size getSmallestPreviewSize(Camera.Parameters parameters) {
		List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
		if (supportedPreviewSizes.size() == 1) {
			return supportedPreviewSizes.get(0);
		}
		
		int index = 0;
		int width = 0;
		Camera.Size size = null;
		for (int i = supportedPreviewSizes.size(); --i >= 0;) {
			size = supportedPreviewSizes.get(i);
			if (width == 0 || size.width < width) {
				width = size.width;
				index = i;
			}
		}
		
		return supportedPreviewSizes.get(index);
	}

	private void refreshLaps() {
		lapListAdapter = new LapListAdapter(this, laps);
		lapList.setAdapter(lapListAdapter);
		lapListAdapter.notifyDataSetChanged();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
			case R.id.menu_tutorial: {
				showAlertBox();
				return true;
			}
			case R.id.menu_sensitivity: {
				startButton.setText(getString(R.string.label_start));
				statusLabel.setText(getString(R.string.label_status_stopped));
				isStarted = false;
				isTimerRunning = false;
				mHandler.removeCallbacks(mUpdateTimeTask);

				Intent i = new Intent(this, SensitivityDialogActivity.class);
				startActivity(i);
				return true;
			}
			case R.id.menu_email: {
				if (isStarted || isTimerRunning) {
					Toast.makeText(this, getString(R.string.error_timer_started), Toast.LENGTH_SHORT).show();
					return true;
				}
				if (laps == null || laps.size() == 0) {
					Toast.makeText(this, getString(R.string.error_laps_empty), Toast.LENGTH_SHORT).show();
					return true;
				}

				String emailBody = lapsToString();

				final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
				emailIntent.setType("plain/text");
				emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, getString(R.string.app_name));
				emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, emailBody);
				startActivity(Intent.createChooser(emailIntent, getString(R.string.menu_share_label)));

				return true;
			}
			case R.id.menu_csv: {
				if (isStarted || isTimerRunning) {
					Toast.makeText(this, getString(R.string.error_timer_started), Toast.LENGTH_SHORT).show();
					return true;
				}
				if (laps == null || laps.size() == 0) {
					Toast.makeText(this, getString(R.string.error_laps_empty), Toast.LENGTH_SHORT).show();
					return true;
				}

				String csv = lapsToCSV();
				
				// save csv file in SD card
				try {
					File directory = new File(Environment.getExternalStorageDirectory(), "/Mini4WD/");
					directory.mkdirs();
					
					SimpleDateFormat format = new SimpleDateFormat("yyyy-mm-dd-hh-mm-ss");
					String filename = "/Mini4WD/times-" + format.format(new Date()) + ".csv";
					File file = new File(Environment.getExternalStorageDirectory(), filename);
					file.createNewFile();
					
					FileOutputStream fos = new FileOutputStream(file);
					OutputStreamWriter writer = new OutputStreamWriter(fos);
					writer.append(csv);
					writer.close();
					fos.close();
					
					Toast.makeText(getBaseContext(), getString(R.string.menu_csv_ok) + filename, Toast.LENGTH_LONG).show();
				} 
				catch (Exception e) {
					Toast.makeText(this, getString(R.string.menu_csv_ko) + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
				}
				
				return true;
			}
		}
		return false;
	}

	public void showAlertBox() {
		new AlertDialog.Builder(this)
		.setMessage(getString(R.string.dialog_tutorial_text))
		.setTitle(getString(R.string.dialog_tutorial_title))
		.setCancelable(true)
		.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int whichButton) {	
				
			}
		}).show();
	}

	private String lapsToString() {

		StringBuilder s = new StringBuilder();

		s.append("Mini 4WD Android Lap Timer data");
		s.append("\n\n");

		long bestTime = Long.MAX_VALUE;
		int bestIndex = 0;
		
		for (int i = 0; i < laps.size(); i++) {
			long lap = laps.get(i);
			
			if (lap < bestTime) {
				bestTime = lap;
				bestIndex = i;
			}
		}
		
		for (int i = 0; i < laps.size(); i++) {
			long lap = laps.get(i);
			s.append("Lap ").append(i + 1).append(": ").append(Utils.convertTime(lap));
			
			if (i == bestIndex)
				s.append(" (best)");
			
			s.append("\n");
		}

		return s.toString();
	}
	
	private String lapsToCSV() {

		StringBuilder s = new StringBuilder();
		s.append("lap,time,milliseconds").append("\r\n");

		for (int i = 0; i < laps.size(); i++) {
			long lap = laps.get(i);
			s.append(i + 1).append(",").append(Utils.convertTime(lap)).append(",").append(lap).append("\r\n");
		}

		return s.toString();
	}
}
