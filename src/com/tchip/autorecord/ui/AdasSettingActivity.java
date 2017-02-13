package com.tchip.autorecord.ui;

import com.sinosmart.adas.LicenseInterface;
import com.tchip.autorecord.R;
import com.tchip.autorecord.util.MyLog;
import com.tchip.autorecord.util.ProviderUtil;
import com.tchip.autorecord.util.ProviderUtil.Name;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Switch;
import android.widget.TextView;

public class AdasSettingActivity extends Activity {
	private Context context;
	private TextView textVerifyState;

	private LicenseInterface licenseInterface;

	/**
	 * 车道偏离预警
	 */
	private Switch switchAdasLane;

	/**
	 * 前车碰撞预警
	 */
	private Switch switchAdasVehicle;

	/**
	 * 声音开关
	 */
	private Switch switchAdasSound;

	/**
	 * 室内调试
	 */
	private Switch switchAdasIndoor;
	
	/**
	 * 摄像头角度调整辅助线
	 */
	private Switch switchAngleAdjust;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_adas_setting);
		setTitle(getResources().getString(R.string.adas_setting_title));
		context = getApplicationContext();

		licenseInterface = new LicenseInterface();

		initialLayout();
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	private void initialLayout() {
		textVerifyState = (TextView) findViewById(R.id.textVerifyState);
		checkAdasLicensed(context);

		switchAdasLane = (Switch) findViewById(R.id.switchAdasLane);
		switchAdasLane.setChecked("1".equals(ProviderUtil.getValue(context,
				Name.ADAS_LINE, "1")));
		switchAdasLane
				.setOnCheckedChangeListener(new OnCheckedChangeListener() {

					@Override
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {
						ProviderUtil.setValue(context, Name.ADAS_LINE,
								isChecked ? "1" : "0");
					}
				});

		switchAdasVehicle = (Switch) findViewById(R.id.switchAdasVehicle);
		switchAdasVehicle.setChecked("1".equals(ProviderUtil.getValue(context,
				Name.ADAS_VEHICLE, "1")));
		switchAdasVehicle
				.setOnCheckedChangeListener(new OnCheckedChangeListener() {

					@Override
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {
						ProviderUtil.setValue(context, Name.ADAS_VEHICLE,
								isChecked ? "1" : "0");
					}
				});

		switchAdasSound = (Switch) findViewById(R.id.switchAdasSound);
		switchAdasSound.setChecked("1".equals(ProviderUtil.getValue(context,
				Name.ADAS_SOUND, "1")));
		switchAdasSound
				.setOnCheckedChangeListener(new OnCheckedChangeListener() {

					@Override
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {
						ProviderUtil.setValue(context, Name.ADAS_SOUND,
								isChecked ? "1" : "0");
					}
				});

		switchAdasIndoor = (Switch) findViewById(R.id.switchAdasIndoor);
		switchAdasIndoor.setChecked("1".equals(ProviderUtil.getValue(context,
				Name.ADAS_INDOOR_DEBUG, "0")));
		switchAdasIndoor
				.setOnCheckedChangeListener(new OnCheckedChangeListener() {

					@Override
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {
						ProviderUtil.setValue(context, Name.ADAS_INDOOR_DEBUG,
								isChecked ? "1" : "0");
					}
				});

		
		switchAngleAdjust = (Switch) findViewById(R.id.switchAngleAdjust);
		switchAngleAdjust.setChecked("1".equals(ProviderUtil.getValue(context,
				Name.ADAS_ANGLE_ADJUST, "0")));
		switchAngleAdjust.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				ProviderUtil.setValue(context, Name.ADAS_ANGLE_ADJUST,
						isChecked ? "1" : "0");
			}
		});
		
	}

	private void checkAdasLicensed(Context context) {
		if (licenseInterface.isLicensed(context)) {
			textVerifyState.setText(getResources().getString(
					R.string.adas_verify_ok));
		} else {
			int licenseState = licenseInterface.getLicense(context);
			MyLog.i("[ADAS]licenseState:" + licenseState);

			textVerifyState.setText(getDescriptionByCode(licenseState));
			if (4 == licenseState) {
				int restoreState = licenseInterface.restoreLicense(context);
				MyLog.i("[ADAS]restoreState:" + restoreState);
				textVerifyState.setText(getDescriptionByCode(restoreState));
			}
		}
	}

	/**
	 * @param 0 ：授权成功
	 * @param 1 ：授权失败，设备未授权
	 * @param 2 ：授权失败，授权数已用完
	 * @param 3 ：授权失败，服务器异常
	 * @param 4 ：设备已授权
	 * @param 5 ：授权恢复失败，未找到授权记录
	 * @param 6 ：授权恢复失败，恢复过频繁
	 * @param 201 ：网络错误
	 * 
	 * @return
	 */
	private String getDescriptionByCode(int code) {
		switch (code) {
		case 0:
			return getResources().getString(R.string.adas_verify_ok);

		case 1:
			return getResources().getString(R.string.adas_verify_fail_not_auth);

		case 2:
			return getResources().getString(
					R.string.adas_verify_fail_out_of_number);

		case 3:
			return getResources().getString(
					R.string.adas_verify_fail_server_error);

		case 4:
			return getResources().getString(R.string.adas_already_auth_restore);

		case 5:
			return getResources().getString(R.string.adas_restore_fail_no_item);

		case 6:
			return getResources().getString(
					R.string.adas_restore_fail_too_frequency);

		case 201:
			return getResources().getString(
					R.string.adas_verify_fail_check_network);

		default:
			return "";
		}
	}
}
