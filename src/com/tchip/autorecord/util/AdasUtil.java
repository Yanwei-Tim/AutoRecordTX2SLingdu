package com.tchip.autorecord.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.sinosmart.adas.ADASInterface;
import com.tchip.autorecord.Constant;
import com.tchip.autorecord.util.ProviderUtil.Name;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.location.Criteria;

public class AdasUtil {

	/**
	 * 根据output数据发送广播通知AuoUI
	 * 
	 * output 存储格式说明： A.前十位：
	 * 
	 * @param [0] 车道1起点X坐标
	 * @param [1] 车道1起点Y坐标
	 * @param [2] 车道1终点X坐标
	 * @param [3] 车道1终点Y坐标
	 * @param [4] 车道1偏离标识（1为偏离）
	 * 
	 * @param [5] 车道2起点X坐标
	 * @param [6] 车道2起点Y坐标
	 * @param [7] 车道2终点X坐标
	 * @param [8] 车道2终点Y坐标
	 * @param [9] 车道2偏离标识（1为偏离）
	 * 
	 *        B.数组索引号 9 以后存储的为车尾数据， 每 5 位存储一个车尾相关数据， 存储顺序为：
	 * 
	 * @param 车尾区域左上角x坐标
	 * @param 车尾区域左上角y坐标
	 * @param 车尾区域宽度
	 * @param 车尾距离
	 * @param 碰撞预警标识
	 *            (1代表需要预警)
	 */
	public static void sendBroadcastByOutput(Context context,
			double[] adasOutput) {
		if (adasOutput.length >= 4 && adasOutput[4] == 1) {
			MyLog.i("ADAS", "[4] == 1");
			context.sendBroadcast(new Intent(Constant.Broadcast.ADAS_MSG)
					.putExtra("type", "right"));
		}

		if (adasOutput.length >= 9 && adasOutput[9] == 1) {
			MyLog.i("ADAS", "[9] == 1");
			context.sendBroadcast(new Intent(Constant.Broadcast.ADAS_MSG)
					.putExtra("type", "left"));
		}

		if (adasOutput.length >= 14 && adasOutput[14] == 1) {
			MyLog.i("ADAS", "[14] == 1");
			context.sendBroadcast(new Intent(Constant.Broadcast.ADAS_MSG)
					.putExtra("type", "front"));
		}
	}

	/**
	 * 设置ADAS参数
	 */
	public static void setAdasConfig(Context context,
			ADASInterface adasInterface) {
		if (adasInterface != null) {
			adasInterface.setDebug(1); // 绘制校准箭头
			if ("1".equals(ProviderUtil.getValue(context, Name.ADAS_SOUND, "1"))) {
				adasInterface.enableSound(ADASInterface.SET_ON); // 开启声音提示
			} else {
				adasInterface.enableSound(ADASInterface.SET_OFF);
			}
			if ("1".equals(ProviderUtil.getValue(context, Name.ADAS_LINE, "1"))) {
				adasInterface.setLane(ADASInterface.SET_ON); // 车道偏离预警
			} else {
				adasInterface.setLane(ADASInterface.SET_OFF);
			}
			if ("1".equals(ProviderUtil.getValue(context, Name.ADAS_VEHICLE,
					"1"))) {
				adasInterface.setVehicle(ADASInterface.SET_ON); // 前车碰撞预警
			} else {
				adasInterface.setVehicle(ADASInterface.SET_OFF);
			}
			if ("1".equals(ProviderUtil.getValue(context,
					Name.ADAS_ANGLE_ADJUST, "0"))) {
				adasInterface.CalibInfoSwitch(true); // 是否显示“调整摄像头角度,车身请勿超过红线”
			} else {
				adasInterface.CalibInfoSwitch(false);
			}

			String adasSensity = ProviderUtil.getValue(context,
					Name.ADAS_SENSITY, "1"); // 设置预警灵敏度
			if ("0".equals(adasSensity)) { // 低
				adasInterface.setWarningSensitivity(0);
			} else if ("2".equals(adasSensity)) { // 高
				adasInterface.setWarningSensitivity(2);
			} else { // 中
				adasInterface.setWarningSensitivity(1);
			}

			String adasThreshold = ProviderUtil.getValue(context,
					Name.ADAS_THRESHOLD, "20"); // 设置最低预警车速，低于该速度不预警
			if ("0".equals(adasThreshold)) {
				adasInterface.setSpeedThreshold(0);
			} else if ("50".equals(adasThreshold)) {
				adasInterface.setSpeedThreshold(50);
			} else if ("80".equals(adasThreshold)) {
				adasInterface.setSpeedThreshold(80);
			} else {
				adasInterface.setSpeedThreshold(20);
			}

			// adasInterface.SetForwardDistBias(bias); // 为车距添加修正值:-10~+10m
			// adasInterface.setPerdestrain(ADASInterface.SET_OFF); // 行人识别？
			// adasInterface.reCalibration(); // 重新对该摄像头校准
		}
	}

	/**
	 * 返回Location查询条件
	 * 
	 * @return
	 */
	public static Criteria getLocationCriteria() {
		Criteria criteria = new Criteria();
		// 设置定位精确度 Criteria.ACCURACY_COARSE比较粗略，Criteria.ACCURACY_FINE则比较精细
		criteria.setAccuracy(Criteria.ACCURACY_FINE);
		criteria.setSpeedRequired(true); // 设置是否要求速度
		criteria.setCostAllowed(false); // 设置是否允许运营商收费
		criteria.setBearingRequired(false); // 设置是否需要方位信息
		criteria.setAltitudeRequired(false); // 设置是否需要海拔信息
		criteria.setPowerRequirement(Criteria.POWER_LOW); // 设置对电源的需求
		return criteria;
	}

	/** 保存一张bitmap */
	public static void saveOneBitmap(Bitmap bitmap) {
		File file = new File(
				"/storage/sdcard0/ADAS_"
						+ new SimpleDateFormat("yyyyMMdd-HH-mm-ss-SSS",
								Locale.CHINESE).format(new Date()));
		if (file.exists()) {
			file.delete();
		}
		try {
			FileOutputStream out = new FileOutputStream(file);
			bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
			out.flush();
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/** 保存YUV数据 */
	public static void saveOneYUVImage(byte[] data) {
		File fileTmp = new File(
				"/storage/sdcard1/DrivingRecord/Image/"
						+ new SimpleDateFormat("yyyy-MM-dd_HHmmss",
								Locale.CHINESE).format(new Date()) + ".yuv");
		try {
			FileOutputStream fos = new FileOutputStream(fileTmp);
			fos.write(data);
			fos.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
