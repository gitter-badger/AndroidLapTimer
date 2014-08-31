package com.pimentoso.android.laptimer;

import android.util.Log;

public class FPSCounter {

    private int frames = 0;
    private int fps = 0;
    private long startTime = System.nanoTime();
	private StringBuilder sb = new StringBuilder();
    
    public void update() {
        frames++;
        if(System.nanoTime() - startTime >= 1000000000) {
        	fps = frames;
            frames = 0;
            startTime = System.nanoTime();
        	Log.d("Mini4WD Lap Timer FPS", printFrames());
        }
    }

	public String printFrames() {
		sb.setLength(0);
		sb.append("fps: ").append(fps);
		return sb.toString();
	}
}
