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

	}

	private void checkAdasLicensed(Context context) {
		if (licenseInterface.isLicensed(context)) {
			textVerifyState.setText(getResources().getString(
					R.string.adas_verify_ok));
		} else {
			/**
			 * 1） "认证成功"
			 * 
			 * 2） "认证失败， 手机未授权"
			 * 
			 * 3） "认证失败， 授权数已用完"
			 * 
			 * 4） "认证失败， 服务器异常"
			 * 
			 * 5） "已授权， 如果不能正常使用， 请使用授权恢复"
			 * 
			 * 6） "认证失败， 请检查网络"
			 * 
			 * 7） "网络错误"
			 * 
			 * 8） "授权服务端未开启"
			 * 
			 * 9） "未发现授权设备"
			 * 
			 * 10） "获取设备信息出错"
			 */
			int licenseState = licenseInterface.getLicense(context);
			MyLog.i("[ADAS]licenseState:" + licenseState);
			switch (licenseState) {
			case 1:
				textVerifyState.setText(getResources().getString(
						R.string.adas_verify_ok));
				break;

			case 2:
				textVerifyState.setText(getResources().getString(
						R.string.adas_verify_fail_not_auth));
				break;

			case 3:
				textVerifyState.setText(getResources().getString(
						R.string.adas_verify_fail_out_of_number));
				break;

			case 4:
				textVerifyState.setText(getResources().getString(
						R.string.adas_verify_fail_server_error));
				break;

			case 5:
				textVerifyState.setText(getResources().getString(
						R.string.adas_already_auth_restore));
				/**
				 * 1） "认证成功"
				 * 
				 * 2） "认证失败， 手机未授权"
				 * 
				 * 3） "认证失败， 授权数已用完"
				 * 
				 * 4） "认证失败， 服务器异常"
				 * 
				 * 5） "恢复授权失败， 未找到记录"
				 * 
				 * 6） "恢复授权失败， 恢复过频繁"
				 * 
				 * 7） "认证失败， 请检查网络"
				 * 
				 * 8） "网络错误"
				 * 
				 * 9） "授权服务端未开启"
				 * 
				 * 10） "未发现授权设备"
				 * 
				 * 11） "获取设备信息出错"
				 */
				int restoreState = licenseInterface.restoreLicense(context);
				MyLog.i("[ADAS]restoreState:" + restoreState);
				switch (restoreState) {
				case 1:
					textVerifyState.setText(getResources().getString(
							R.string.adas_verify_ok));
					break;

				case 2:
					textVerifyState.setText(getResources().getString(
							R.string.adas_verify_fail_not_auth));
					break;

				case 3:
					textVerifyState.setText(getResources().getString(
							R.string.adas_verify_fail_out_of_number));
					break;

				case 4:
					textVerifyState.setText(getResources().getString(
							R.string.adas_verify_fail_server_error));
					break;

				case 5:
					textVerifyState.setText(getResources().getString(
							R.string.adas_restore_fail_no_item));
					break;

				case 6:
					textVerifyState.setText(getResources().getString(
							R.string.adas_restore_fail_too_frequency));
					break;

				case 7:
				case 8:
					textVerifyState.setText(getResources().getString(
							R.string.adas_verify_fail_check_network));
					break;

				case 9:
					textVerifyState.setText(getResources().getString(
							R.string.adas_verify_fail_server_close));
					break;

				case 10:
					textVerifyState.setText(getResources().getString(
							R.string.adas_verify_no_auth_device));
					break;

				case 11:
					textVerifyState.setText(getResources().getString(
							R.string.adas_get_device_info_error));
					break;

				default:
					break;
				}

			case 6:
			case 7:
				textVerifyState.setText(getResources().getString(
						R.string.adas_verify_fail_check_network));
				break;

			case 8:
				textVerifyState.setText(getResources().getString(
						R.string.adas_verify_fail_server_close));
				break;

			case 9:
				textVerifyState.setText(getResources().getString(
						R.string.adas_verify_no_auth_device));
				break;

			case 10:
				textVerifyState.setText(getResources().getString(
						R.string.adas_get_device_info_error));
				break;

			default:
				break;
			}
		}
	}

}
