package org.aisen.weibo.sina.receiver;

import android.content.Intent;

import org.aisen.android.common.utils.Logger;

public class TimingIntent extends Intent {

	private long timing;
	
	public TimingIntent() {
		
	}
	
	public TimingIntent(long timing) {
		this.timing = timing;
		putExtra("timing", timing);
		addFlags(FLAG_INCLUDE_STOPPED_PACKAGES);
		setAction(TimingBroadcastReceiver.ACTION_TIMING_PUBLISH);
	}
	
	@Override
	public boolean filterEquals(Intent other) {
		if (other instanceof TimingIntent) {
			Logger.d(TimingBroadcastReceiver.TAG, "两个Intent匹配");
			
			return ((TimingIntent) other).timing == timing;
		}
		
		Logger.d(TimingBroadcastReceiver.TAG, "两个Intent不匹配");
		return false;
	}
	
}
