package com.tchip.autorecord.util;

import com.tchip.autorecord.Constant;

import android.util.Log;

public class MyLog {

	public static final String TAG_DEFAULT = "AZ";

	private static String MODULE = "[AutoRecord]";

	public static void e(String log) {
		if (Constant.isDebug)
			Log.e(TAG_DEFAULT, DateUtil.getTimeStr("HH:mm:ss") + MODULE + log);
	}

	public static void e(String tag, String log) {
		if (Constant.isDebug)
			Log.e(tag, DateUtil.getTimeStr("HH:mm:ss") + MODULE + log);
	}

	public static void v(String log) {
		if (Constant.isDebug)
			Log.v(TAG_DEFAULT, DateUtil.getTimeStr("HH:mm:ss") + MODULE + log);
	}

	public static void v(String tag, String log) {
		if (Constant.isDebug)
			Log.v(tag, DateUtil.getTimeStr("HH:mm:ss") + MODULE + log);
	}

	public static void d(String log) {
		if (Constant.isDebug)
			Log.d(TAG_DEFAULT, DateUtil.getTimeStr("HH:mm:ss") + MODULE + log);
	}

	public static void d(String tag, String log) {
		if (Constant.isDebug)
			Log.d(tag, DateUtil.getTimeStr("HH:mm:ss") + MODULE + log);
	}

	public static void i(String log) {
		if (Constant.isDebug)
			Log.i(TAG_DEFAULT, DateUtil.getTimeStr("HH:mm:ss") + MODULE + log);
	}

	public static void i(String tag, String log) {
		if (Constant.isDebug)
			Log.i(tag, DateUtil.getTimeStr("HH:mm:ss") + MODULE + log);
	}

	public static void w(String log) {
		if (Constant.isDebug)
			Log.w(TAG_DEFAULT, DateUtil.getTimeStr("HH:mm:ss") + MODULE + log);
	}

	public static void w(String tag, String log) {
		if (Constant.isDebug)
			Log.w(tag, DateUtil.getTimeStr("HH:mm:ss") + MODULE + log);
	}

}
