package com.pimentoso.android.laptimer;

public class Utils {
	
	private static StringBuilder sb = new StringBuilder();
	
	public static String convertTime(long millis) {

		if (millis == 0) {
			return "0:00:0";
		}

		sb.setLength(0);
		int split = ((int) (millis / 100)) % 10;
		int seconds = (int) (millis / 1000);
		int minutes = seconds / 60;
		seconds = seconds % 60;

		if (seconds < 10) {
			sb.append(minutes).append(":0").append(seconds).append(":").append(split);
		}
		else {
			sb.append(minutes).append(":").append(seconds).append(":").append(split);
		}
		
		return sb.toString();
	}
}
