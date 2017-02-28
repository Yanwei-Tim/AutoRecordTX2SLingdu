package com.tchip.autorecord.util;

import java.io.File;
import java.io.FileInputStream;
import java.text.DecimalFormat;

import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.view.WindowManager;

import com.tchip.autorecord.Constant;
import com.tchip.autorecord.MyApp;
import com.tchip.autorecord.R;
import com.tchip.autorecord.view.FlashCleanDialog;

public class FileUtil {

	/**
	 * 遍历目录大小
	 * 
	 * @return 剩余空间，单位：字节B
	 */
	public static long getTotalSizeOfFilesInDir(File file) {
		if (file.isFile())
			return file.length();
		final File[] children = file.listFiles();
		long total = 0;
		if (children != null)
			for (final File child : children) {
				total += getTotalSizeOfFilesInDir(child);
			}
		return total;
	}

	/**
	 * 获取不同分辨率下，前后录像所占空间比率
	 * 
	 * @param resolution
	 * @param isFront
	 * @return
	 */
	private static float getVideoRate(int resolution, boolean isFront) {
		switch (resolution) {
		case 720:
			return isFront ? 0.845f : 0.155f; // 137.0f / 162 : 25.0f / 162;

		case 1080:
		default:
			return isFront ? 0.884f : 0.116f; // 194.0f / 219 : 25.0f / 219;
		}
	}

	/**
	 * [双录到同一张SD卡]空间是否不足，需要删除旧视频
	 * 
	 * 前录路径：/storage/sdcard1/DrivingRecord/VideoFront/ *.mp4
	 * 
	 * 后录路径：/storage/sdcard1/tachograph_back/DrivingRecord/unlock/
	 * 
	 */
	public static boolean isFrontStorageLess() {
		// float sdTotal =
		// StorageUtil.getSDTotalSize(Constant.Path.RECORD_SDCARD); // SD卡总空间
		float sdFree = StorageUtil
				.getSDAvailableSize(Constant.Path.RECORD_SDCARD); // SD剩余空间
		float frontUse = (float) FileUtil.getTotalSizeOfFilesInDir(new File(
				Constant.Path.VIDEO_FRONT_TOTAL)); // 前置已用空间
		float backUse = (float) FileUtil.getTotalSizeOfFilesInDir(new File(
				Constant.Path.VIDEO_BACK_TOTAL)); // 后置已用空间

		float recordTotal = sdFree + frontUse + backUse; // 录像可用空间
		float frontTotal = recordTotal
				* getVideoRate(MyApp.resolutionState, true); // 前置归属空间
		float frontFree = frontTotal - frontUse; // 前置剩余空间
		long intFrontFree = (long) frontFree;
		long intSdFree = (long) sdFree;

		boolean isStorageLess = intFrontFree < Constant.Record.FRONT_MIN_FREE_STORAGE
				|| intSdFree < Constant.Record.FRONT_MIN_FREE_STORAGE;
		MyLog.v("FileUtil.isFrontStorageLess:" + isStorageLess);
		return isStorageLess;
	}

	public static boolean isFrontLockLess() {
		float sdTotal = StorageUtil.getSDTotalSize(Constant.Path.RECORD_SDCARD); // SD卡总空间
		float frontLockUse = (float) FileUtil
				.getTotalSizeOfFilesInDir(new File(
						Constant.Path.VIDEO_FRONT_LOCK)); // 前置加锁已用空间
		long intFrontLockUse = (long) frontLockUse;
		long intFrontLockMax = (long) (sdTotal * Constant.Record.FRONT_LOCK_MAX_PERCENT);

		boolean isFrontLockLess = intFrontLockUse > intFrontLockMax;
		MyLog.v("FileUtil.isFrontLockLess:" + isFrontLockLess + ",USE:"
				+ intFrontLockUse + ",MAX:" + intFrontLockMax);
		return isFrontLockLess;
	}

	public static boolean isBackStorageLess() {
		// float sdTotal =
		// StorageUtil.getSDTotalSize(Constant.Path.RECORD_SDCARD); // SD卡总空间
		float sdFree = StorageUtil
				.getSDAvailableSize(Constant.Path.RECORD_SDCARD); // SD剩余空间
		float frontUse = (float) FileUtil.getTotalSizeOfFilesInDir(new File(
				Constant.Path.VIDEO_FRONT_TOTAL)); // 前置已用空间
		float backUse = (float) FileUtil.getTotalSizeOfFilesInDir(new File(
				Constant.Path.VIDEO_BACK_TOTAL)); // 后置已用空间

		float recordTotal = sdFree + frontUse + backUse; // 录像可用空间
		float backTotal = recordTotal
				* getVideoRate(MyApp.resolutionState, false); // 后置归属空间
		float backFree = backTotal - backUse; // 后置剩余空间
		int intBackFree = (int) backFree;
		int intSdFree = (int) sdFree;

		boolean isStorageLess = intBackFree < Constant.Record.BACK_MIN_FREE_STORAGE
				|| intSdFree < Constant.Record.FRONT_MIN_FREE_STORAGE;
		MyLog.v("FileUtil.isBackStorageLess:" + isStorageLess);
		return isStorageLess;
	}

	public static boolean isBackLockLess() {
		float sdTotal = StorageUtil.getSDTotalSize(Constant.Path.RECORD_SDCARD); // SD卡总空间
		float backLockUse = (float) FileUtil.getTotalSizeOfFilesInDir(new File(
				Constant.Path.VIDEO_BACK_LOCK)); // 后置加锁已用空间
		long intBackLockUse = (long) backLockUse;
		long intBackLockMax = (long) (sdTotal * Constant.Record.BACK_LOCK_MAX_PERCENT);

		boolean isBackLockLess = intBackLockUse > intBackLockMax;
		MyLog.v("FileUtil.isBackLockLess:" + isBackLockLess + ",USE:"
				+ intBackLockUse + ",MAX:" + intBackLockMax);
		return isBackLockLess;
	}

}