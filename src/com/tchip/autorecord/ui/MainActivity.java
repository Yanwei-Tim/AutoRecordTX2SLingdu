package com.tchip.autorecord.ui;

import java.io.File;
import java.text.DecimalFormat;
import java.util.Iterator;

import com.sinosmart.adas.ADASInterface;
import com.sinosmart.adas.LicenseInterface;
import com.tchip.autorecord.Constant;
import com.tchip.autorecord.MyApp;
import com.tchip.autorecord.R;
import com.tchip.autorecord.Typefaces;
import com.tchip.autorecord.service.SensorWatchService;
import com.tchip.autorecord.thread.WriteImageExifThread;
import com.tchip.autorecord.util.AdasUtil;
import com.tchip.autorecord.util.ClickUtil;
import com.tchip.autorecord.util.DateUtil;
import com.tchip.autorecord.util.HintUtil;
import com.tchip.autorecord.util.MyLog;
import com.tchip.autorecord.util.ProviderUtil;
import com.tchip.autorecord.util.ProviderUtil.Name;
import com.tchip.autorecord.util.SettingUtil;
import com.tchip.autorecord.util.StorageUtil;
import com.tchip.autorecord.util.TelephonyUtil;
import com.tchip.autorecord.view.BackLineView;
import com.tchip.tachograph.ScaledStreamCallback;
import com.tchip.tachograph.TachographCallback;
import com.tchip.tachograph.TachographRecorder;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.ContentObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.PorterDuff.Mode;
import android.hardware.Camera;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.SurfaceHolder.Callback;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

public class MainActivity extends Activity {

	private Context context;
	private SharedPreferences sharedPreferences;
	private Editor editor;
	private PowerManager powerManager;
	private WakeLock partialWakeLock;
	private WakeLock fullWakeLock;

	private TextView textLatLng; // 经纬度
	private TextView textPositionCenter, textPositionRight;
	// 前置
	private RelativeLayout layoutFront;
	private TextView textTimeCenter, textTimeRight; // 时间跑秒
	private TextClock textSystemTime;
	private ImageButton imageRecordState; // 录像按钮
	private ImageButton imageVideoLock; // 加锁按钮
	private TextView textVideoLock;
	private ImageButton imageCameraSwitch; // 前后切换
	private TextView textCameraSwitch;
	private ImageButton imageExit; // 退出
	private TextView textExit;
	private ImageButton imageVideoSize; // 视频尺寸
	private TextView textVideoSize;
	private ImageButton imageVideoLength; // 视频分段
	private TextView textVideoLength;
	private ImageButton imageVideoMute; // 静音按钮
	private TextView textVideoMute;
	private ImageButton imagePhotoTake; // 拍照按钮

	private Camera cameraFront;
	private SurfaceView surfaceViewFront;
	private SurfaceHolder surfaceHolderFront;
	private TachographRecorder recorderFront;
	private int intervalState = 3, muteState;

	// 后置
	private RelativeLayout layoutBack;
	private Camera cameraBack;
	private SurfaceView surfaceViewBack;
	private SurfaceHolder surfaceHolderBack;
	private TachographRecorder recorderBack;
	// 倒车线控制
	private LinearLayout layoutBackLine;
	private BackLineView backLineView;
	private RelativeLayout layoutBackLineControl;
	private ImageButton imageBackLineShow;
	private ImageButton imageBackLineEdit;
	private ImageButton imageBackLineReset;

	/** Intent是否是新的 */
	private boolean isIntentInTime = false;

	private Handler mMainHandler; // 主线程Handler

	/** UI配置 */
	private int CAMERA_WIDTH = 1920;
	private int CAMERA_HEIGHT_FRONT = 480;
	private int CAMERA_HEIGHT_BACK = 720;
	private ScrollView scrollPreview;

	// ADAS
	private ADASInterface adasInterface;
	private boolean isAdasInitial = false;
	private LicenseInterface licenseInterface;
	private Bitmap adasBitmap;

	private double[] adasOutput = new double[256];
	private Paint paint;
	private ImageView imageAdas;

	// 当前速度
	private int adasSpeed = 0;
	private int recordSpeed = 0;
	private double nowLatitude = 0.0;
	private double nowLongitude = 0.0;

	private LocationManager locationManager;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mMainHandler = new Handler(this.getMainLooper());
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setStatusBarVisible(true);
		setContentView(R.layout.activity_main_tx2s);

		context = getApplicationContext();
		powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE); // 获取屏幕状态
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE); // 位置
		if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			// TODO: Open GPS
		} else {
			// 获取位置信息
			// 如果不设置查询要求，getLastKnownLocation方法传人的参数为LocationManager.GPS_PROVIDER
			Location location = locationManager
					.getLastKnownLocation(locationManager.getBestProvider(
							AdasUtil.getLocationCriteria(), true));
			locationManager.addGpsStatusListener(gpsStatusListener); // 监听状态
			// 绑定监听，有4个参数
			// 参数1，设备：有GPS_PROVIDER和NETWORK_PROVIDER两种
			// 参数2，位置信息更新周期，单位毫秒
			// 参数3，位置变化最小距离：当位置距离变化超过此值时，将更新位置信息
			// 参数4，监听
			// 备注：参数2和3，如果参数3不为0，则以参数3为准；参数3为0，则通过时间来定时更新；两者为0，则随时刷新
			locationManager.requestLocationUpdates(
					LocationManager.GPS_PROVIDER, 1000, 0, locationListener);
		}

		fullWakeLock = powerManager.newWakeLock(
				PowerManager.ACQUIRE_CAUSES_WAKEUP
						| PowerManager.FULL_WAKE_LOCK, this.getClass()
						.getCanonicalName() + "full");

		sharedPreferences = getSharedPreferences(Constant.MySP.NAME,
				Context.MODE_PRIVATE);
		editor = sharedPreferences.edit();

		initialLayout();
		imageAdas = (ImageView) findViewById(R.id.imageAdas);
		adasBitmap = Bitmap.createBitmap(853, 480, Bitmap.Config.ARGB_8888); // 640*480

		paint = new Paint();
		paint.setColor(Color.BLUE);
		paint.setStrokeWidth(5);
		paint.setXfermode(new PorterDuffXfermode(Mode.CLEAR));

		getContentResolver()
				.registerContentObserver(
						Uri.parse("content://com.tchip.provider.AutoProvider/state/name/"),
						true, new AutoContentObserver(new Handler()));

		StorageUtil.createRecordDirectory();
		setupFrontDefaults();
		setupBackDefaults();

		setupRecordViews();

		// 首次启动是否需要自动录像
		if (1 == SettingUtil.getAccStatus()) {
			MyApp.isAccOn = true; // 同步ACC状态
			new Thread(new AutoThread()).start(); // 序列任务线程
		} else {
			MyApp.isAccOn = false; // 同步ACC状态
			MyLog.v("[Main]ACC Check:OFF");
			String strParkRecord = ProviderUtil.getValue(context,
					Name.PARK_REC_STATE, "0");
			if ("1".equals(strParkRecord)) {
				MyApp.isParkRecording = true;
				acquirePartialWakeLock(10 * 1000);
				new Thread(new AutoThread()).start(); // 序列任务线程
			} else {
				MyApp.isParkRecording = false;
			}
		}
		// 碰撞侦测服务
		Intent intentSensor = new Intent(this, SensorWatchService.class);
		startService(intentSensor);

		new Thread(new BackgroundThread()).start(); // 后台线程

		mainReceiver = new MainReceiver();
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(Constant.Broadcast.ACC_ON);
		intentFilter.addAction(Constant.Broadcast.ACC_OFF);
		intentFilter.addAction(Constant.Broadcast.BACK_CAR_ON);
		intentFilter.addAction(Constant.Broadcast.BACK_CAR_OFF);
		intentFilter.addAction(Constant.Broadcast.SPEECH_COMMAND);
		intentFilter.addAction(Constant.Broadcast.MEDIA_FORMAT);
		intentFilter.addAction(Constant.Broadcast.GOING_SHUTDOWN);
		intentFilter.addAction(Constant.Broadcast.RELEASE_RECORD);
		intentFilter.addAction(Constant.Broadcast.RELEASE_RECORD_TEST);
		intentFilter.addAction("tchip.intent.action.MOVE_RECORD_BACK");
		intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
		intentFilter.addAction(Constant.Broadcast.GPS_STATUS);
		registerReceiver(mainReceiver, intentFilter);

		// 接收额外信息
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			String reason = extras.getString("reason");
			long sendTime = extras.getLong("time");
			isIntentInTime = ClickUtil.isIntentInTime(sendTime);
			MyLog.v("isIntentInTime:" + isIntentInTime + ",reason:" + reason);
			if (isIntentInTime) {
				if ("autoui_oncreate".equals(reason)) { // 回到主界面
					MyApp.shouldMountRecordFront = true;
					MyApp.shouldMountRecordBack = true;
					// new Thread(new BackHomeWhenBootThread()).start();
				} else if ("acc_on".equals(reason)) {
					MyApp.shouldMountRecordFront = true;
					MyApp.shouldMountRecordBack = true;
					new Thread(new BackHomeWhenAccOnThread()).start();
				}
			}
		}

		licenseInterface = new LicenseInterface();

		authAdas();
		initialAdasInterface();

	}

	@Override
	protected void onResume() {
		setStatusBarVisible(false);
		MyLog.v("onResume");

		ProviderUtil.setValue(context, Name.RECORD_INITIAL, "1");

		try {
			refreshFrontButton(); // 更新录像界面按钮状态
			setupRecordViews();
		} catch (Exception e) {
			e.printStackTrace();
			MyLog.e("onResume catch Exception:" + e.toString());
		}

		if (cameraBeforeBack == 0) {
			String strBackState = ProviderUtil.getValue(context,
					Name.BACK_CAR_STATE, "0");
			if ("1".equals(strBackState)) { // 隐藏格式化对话框
				if (Constant.Module.hasCVBSDetect && !SettingUtil.isCVBSIn()) {
					HintUtil.showToast(context,
							getString(R.string.no_cvbs_detect));
				} else {
					switchCameraTo(Integer.parseInt(strBackState));
					setBackPreviewBig();
					sendBroadcast(new Intent(
							Constant.Broadcast.HIDE_FORMAT_DIALOG));
				}
			} else {
				switchCameraTo(1); // Integer.parseInt(strBackState)
			}
		} else {
			switchCameraTo(cameraBeforeBack);
		}
		super.onResume();
	}

	@Override
	protected void onPause() {
		MyLog.v("onPause,FrontRecording:" + MyApp.isFrontRecording
				+ ",BackRecording:" + MyApp.isBackRecording);

		if (!MyApp.isFrontRecording) {
			MyApp.isFrontLockSecond = false;
		}
		if (!MyApp.isBackRecording) {
			MyApp.isBackLockSecond = false;
		}
		super.onPause();
	}

	@Override
	protected void onStop() {
		MyLog.v("onStop");
		setStatusBarVisible(true);
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		MyLog.v("onDestroy");

		ProviderUtil.setValue(context, Name.RECORD_INITIAL, "0");
		// 释放录像区域
		releaseFrontRecorder();
		releaseBackRecorder();
		closeFrontCamera();
		closeBackCamera();
		// 关闭碰撞侦测服务
		Intent intentCrash = new Intent(context, SensorWatchService.class);
		stopService(intentCrash);

		if (adasInterface != null) {
			adasInterface.ReleaseInterface(); // 释放 ADAS，退出或创建新的对象前调用
			isAdasInitial = false;
		}

		if (mainReceiver != null) {
			unregisterReceiver(mainReceiver);
		}

		if (locationManager != null) {
			locationManager.removeGpsStatusListener(gpsStatusListener);
		}
		super.onDestroy();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		MyLog.i("[onKeyDown]keyCode:" + keyCode);
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			sendHomeKey();
			return true;
		} else
			return super.onKeyDown(keyCode, event);
	}

	/**
	 * 初始化AdasInterface，前提：
	 * 
	 * LicenseInterface.isLicensed == true
	 */
	private void initialAdasInterface() {
		if (licenseInterface.isLicensed(context)) {
			MyLog.i("ADAS", "initialAdasInterface");
			adasInterface = new ADASInterface(480, 640, MainActivity.this);
			isAdasInitial = true;
			AdasUtil.setAdasConfig(context, adasInterface);
		} else {
			MyLog.i("ADAS", "isLicensed == false");
		}
	}

	private void updateSpeedByLocation(Location location) {
		try {
			if (location != null) {
				int tempSpeed = (int) (location.getSpeed() * 3.6); // m/s -->
																	// Km/h
				adasSpeed = tempSpeed;
				recordSpeed = tempSpeed;

				nowLatitude = location.getLatitude();
				nowLongitude = location.getLongitude();

				MyLog.i("GPS", "Speed:" + tempSpeed);
				if (recorderFront != null) {
					if (recordSpeed > 0) {
						recorderFront.setSpeed(recordSpeed);
						recordSpeed = 0; // 清除速度
					}
					recorderFront.setLat(new DecimalFormat("#.000000")
							.format(nowLatitude) + "");
					recorderFront.setLong(new DecimalFormat("#.000000")
							.format(nowLongitude) + "");
				}

				textLatLng.setText("E:"
						+ new DecimalFormat("#.000000").format(nowLongitude)
						+ "   N:"
						+ new DecimalFormat("#.000000").format(nowLatitude));
			}
		} catch (Exception e) {
			MyLog.e("GPS", "updateSpeedByLocation catch:" + e.toString());
		}
	}

	// 位置监听
	private LocationListener locationListener = new LocationListener() {

		/**
		 * 位置信息变化时触发
		 */
		public void onLocationChanged(Location location) {
			// location.getAltitude(); -- 海拔
			updateSpeedByLocation(location);
		}

		/**
		 * GPS状态变化时触发
		 */
		public void onStatusChanged(String provider, int status, Bundle extras) {
			switch (status) {
			case LocationProvider.AVAILABLE: // GPS状态为可见时
				MyLog.i("GPS", "当前GPS状态为可见状态");
				break;

			case LocationProvider.OUT_OF_SERVICE: // GPS状态为服务区外时
				MyLog.i("GPS", "当前GPS状态为服务区外状态");
				break;

			case LocationProvider.TEMPORARILY_UNAVAILABLE: // GPS状态为暂停服务时
				MyLog.i("GPS", "当前GPS状态为暂停服务状态");
				break;
			}
		}

		/**
		 * GPS开启时触发
		 */
		public void onProviderEnabled(String provider) {
			Location location = locationManager.getLastKnownLocation(provider);
			updateSpeedByLocation(location);
		}

		/**
		 * GPS禁用时触发
		 */
		public void onProviderDisabled(String provider) {
			// updateView(null);
		}

	};

	// 状态监听
	GpsStatus.Listener gpsStatusListener = new GpsStatus.Listener() {
		public void onGpsStatusChanged(int event) {
			switch (event) {
			case GpsStatus.GPS_EVENT_FIRST_FIX: // 第一次定位
				MyLog.i("GPS", "GPS_EVENT_FIRST_FIX");
				break;

			case GpsStatus.GPS_EVENT_SATELLITE_STATUS: // 卫星状态改变
				GpsStatus gpsStatus = locationManager.getGpsStatus(null); // 获取当前状态
				int maxSatellites = gpsStatus.getMaxSatellites(); // 获取卫星颗数的默认最大值
				Iterator<GpsSatellite> iters = gpsStatus.getSatellites()
						.iterator(); // 创建一个迭代器保存所有卫星
				int count = 0;
				while (iters.hasNext() && count <= maxSatellites) {
					GpsSatellite s = iters.next();
					count++;
				}
				MyLog.i("GPS", "Satellite Number:" + count);
				break;

			case GpsStatus.GPS_EVENT_STARTED: // 定位启动
				MyLog.i("GPS", "GPS_EVENT_STARTED");
				break;

			case GpsStatus.GPS_EVENT_STOPPED: // 定位结束
				MyLog.i("GPS", "GPS_EVENT_STOPPED");
				break;
			}
		};
	};

	private void setStatusBarVisible(boolean show) {
		if (show) {
			int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
			uiFlags |= 0x00001000;
			getWindow().getDecorView().setSystemUiVisibility(uiFlags);

			sendBroadcast(new Intent(Constant.Broadcast.QUICK_SET_HINT_SHOW));
		} else {
			int uiFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
					| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
					| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
					| View.SYSTEM_UI_FLAG_FULLSCREEN;
			uiFlags |= 0x00001000;
			getWindow().getDecorView().setSystemUiVisibility(uiFlags);

			sendBroadcast(new Intent(Constant.Broadcast.QUICK_SET_HINT_HIDE));
		}
	}

	/**
	 * 设置倒车预览全屏
	 * 
	 * @param big
	 */
	private void setBackPreviewBig() {
		MyLog.i("setBackPreviewBig:");
		surfaceViewBack.setLayoutParams(new RelativeLayout.LayoutParams(
				CAMERA_WIDTH, CAMERA_HEIGHT_BACK));

		setBackLineVisible("1".equals(ProviderUtil.getValue(context,
				Name.BACK_CAR_STATE, "0")));
		layoutBack.setVisibility(View.VISIBLE);
		surfaceViewFront.setLayoutParams(new RelativeLayout.LayoutParams(1, 1));
		layoutFront.setVisibility(View.GONE);

	}

	class BackHomeWhenBootThread implements Runnable {

		@Override
		public void run() {
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			// sendHomeKey()
			// startLauncher();
			String strBackState = ProviderUtil.getValue(context,
					Name.BACK_CAR_STATE, "0");
			if ("1".equals(strBackState)) { // 倒车不返回主页
			} else {
				moveTaskToBack(true);
			}
		}

	}

	class BackHomeWhenAccOnThread implements Runnable {

		@Override
		public void run() {
			try {
				Thread.sleep(8000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			sendHomeKey(); // moveTaskToBack(true);
		}

	}

	private void sendHomeKey() {
		String strBackState = ProviderUtil.getValue(context,
				Name.BACK_CAR_STATE, "0");
		if ("1".equals(strBackState)) { // 倒车不返回主页
		} else {
			sendKeyCode(KeyEvent.KEYCODE_HOME);
		}
	}

	/** ContentProvder监听 */
	public class AutoContentObserver extends ContentObserver {

		public AutoContentObserver(Handler handler) {
			super(handler);
		}

		@Override
		public void onChange(boolean selfChange, Uri uri) {
			String name = uri.getLastPathSegment(); // getPathSegments().get(2);
			if (name.equals("state")) { // insert

			} else { // update
				if (name.startsWith("adas")) { // ADAS
					if (licenseInterface.isLicensed(context)) {
						AdasUtil.setAdasConfig(context, adasInterface);
					}
				} else if (Name.SET_DETECT_CRASH_STATE.equals(name)) {
					String strDetectCrashState = ProviderUtil.getValue(context,
							Name.SET_DETECT_CRASH_STATE, "1");
					if ("0".equals(strDetectCrashState)) {
						MyApp.isCrashOn = false;
					} else {
						MyApp.isCrashOn = true;
					}
				} else if (Name.SET_DETECT_CRASH_LEVEL.equals(name)) {
					String strDetectCrashLevel = ProviderUtil.getValue(context,
							Name.SET_DETECT_CRASH_LEVEL, "1");
					if ("0".equals(strDetectCrashLevel)) {
						MyApp.crashSensitive = 0;
					} else if ("2".equals(strDetectCrashLevel)) {
						MyApp.crashSensitive = 2;
					} else {
						MyApp.crashSensitive = 1;
					}
				} else if (Name.SET_PARK_MONITOR_STATE.equals(name)) {

				} else if (Name.ACC_STATE.equals(name)) {
				} else if (Name.PARK_REC_STATE.equals(name)) {
					if (!MyApp.isAccOn) {
						String strParkRecord = ProviderUtil.getValue(context,
								Name.PARK_REC_STATE, "0");
						if ("1".equals(strParkRecord)) {
							MyApp.isParkRecording = true;
						} else {
							MyApp.isParkRecording = false;
						}
					} else {
						ProviderUtil
								.setValue(context, Name.PARK_REC_STATE, "0");
						MyApp.isParkRecording = false;
					}
				}
			}
			super.onChange(selfChange, uri);
		}

		@Override
		public void onChange(boolean selfChange) {
			super.onChange(selfChange);
		}

	}

	/**
	 * 获取休眠锁
	 * 
	 * PARTIAL_WAKE_LOCK
	 * 
	 * SCREEN_DIM_WAKE_LOCK
	 * 
	 * FULL_WAKE_LOCK
	 * 
	 * ON_AFTER_RELEASE
	 */
	private void acquirePartialWakeLock(long timeout) {
		partialWakeLock = powerManager.newWakeLock(
				PowerManager.PARTIAL_WAKE_LOCK, this.getClass()
						.getCanonicalName());
		partialWakeLock.acquire(timeout);
	}

	private void acquireFullWakeLock() {
		if (!fullWakeLock.isHeld()) {
			fullWakeLock.acquire();
		}
	}

	private void releaseFullWakeLock() {
		if (fullWakeLock.isHeld()) {
			fullWakeLock.release();
		}
	}

	private MainReceiver mainReceiver;
	private int cameraBeforeBack = 1; // 倒车前界面：0-前 1-后 2-前后

	public class MainReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			MyLog.v("MainReceiver.action:" + action);
			if (action.equals(Constant.Broadcast.ACC_OFF)) {
				MyApp.isAccOn = false;
				MyApp.isAccOn = (1 == SettingUtil.getAccStatus());

				takePhoto(true);
			} else if (action.equals(Constant.Broadcast.ACC_ON)) {
				MyApp.isAccOn = true;
				MyApp.shouldWakeRecord = true;

				// 重设视频分段
				String videoTimeStr = sharedPreferences.getString("videoTime",
						"3");
				if ("5".equals(videoTimeStr)) { // 5
					intervalState = 5;
					setRecordInterval(300);
				} else if ("1".equals(videoTimeStr)) { // 1
					intervalState = 1;
					setRecordInterval(60);
				} else { // 3
					intervalState = 3;
					setRecordInterval(180);
				}

				// 碰撞侦测服务
				Intent intentSensor = new Intent(context,
						SensorWatchService.class);
				startService(intentSensor);
			} else if (action.equals(Constant.Broadcast.BACK_CAR_ON)) {
				MyLog.i("cameraBeforeBack:" + cameraBeforeBack);
				if (Constant.Module.hasCVBSDetect && !SettingUtil.isCVBSIn()) {
					HintUtil.showToast(context,
							getString(R.string.no_cvbs_detect));
				} else {
					acquireFullWakeLock();
					setBackLineVisible(true);
					setBackPreviewBig();
				}
			} else if (action.equals(Constant.Broadcast.BACK_CAR_OFF)) {
				releaseFullWakeLock();
				setBackLineVisible(false);
				switchCameraWhenBackOver(cameraBeforeBack);

				String pkgWhenBack = ProviderUtil.getValue(context,
						Name.PKG_WHEN_BACK, "com.xxx.xxx");
				if ("com.tchip.autorecord".equals(pkgWhenBack)) {
				} else {
					moveTaskToBack(true);
				}
			} else if (action.equals(Constant.Broadcast.SPEECH_COMMAND)) {
				String command = intent.getExtras().getString("command");
				if ("open_dvr".equals(command) || "dvr_record".equals(command)) {
					if (MyApp.isAccOn) {
						if (!isFrontRecord()) {
							MyApp.shouldMountRecordFront = true;
						}
						if (!isBackRecord()) {
							MyApp.shouldMountRecordBack = true;
						}
					}
				} else if ("close_dvr".equals(command)) {
					moveTaskToBack(true); // 只关闭界面,不停止录像
				} else if ("start_dvr".equals(command)) {
					if (MyApp.isAccOn) {
						if (!isFrontRecord()) {
							MyApp.shouldMountRecordFront = true;
						}
						if (!isBackRecord()) {
							MyApp.shouldMountRecordBack = true;
						}
					}
				} else if ("stop_dvr".equals(command)
						|| "dvr_stop_record".equals(command)) {
					if (isFrontRecord()) {
						MyApp.shouldStopFrontFromVoice = true;
					}
					if (isBackRecord()) {
						MyApp.shouldStopBackFromVoice = true;
					}
				} else if ("take_photo".equals(command)
						|| "take_photo_wenxin".equals(command)) {
					takePhoto(MyApp.isAccOn);
				} else if ("take_park_photo".equals(command)) { // 停车照片
					takePhoto(MyApp.isAccOn);
				} else if ("take_photo_dsa".equals(command)) { // 语音拍照上传
					takePhoto(MyApp.isAccOn);
				} else if ("dvr_record_lock".equals(command)) { // 录像加锁
					if (!MyApp.isFrontLock) {
						speakVoice(getString(R.string.hint_video_lock));
					}
					MyApp.isFrontLock = true;
					MyApp.isBackLock = true;
					setupRecordViews();
				} else if ("dvr_record_unlock".equals(command)) { // 录像解锁
					if (MyApp.isFrontLock) {
						speakVoice(getString(R.string.hint_video_unlock));
					}
					MyApp.isFrontLock = false;
					MyApp.isBackLock = false;
					setupRecordViews();
				} else if ("dvr_record_mic_open".equals(command)) { // 打开录音-UNMUTE
					// 切换录音/静音状态停止录像，需要重置时间
					if (muteState == Constant.Record.STATE_MUTE) {
						MyApp.shouldVideoRecordWhenChangeMute = MyApp.isFrontRecording;
						setFrontMute(false, true);
						muteState = Constant.Record.STATE_UNMUTE;
						editor.putBoolean("videoMute", false);
						editor.commit();
						setupRecordViews();
						if (MyApp.shouldVideoRecordWhenChangeMute) { // 修改录音/静音后按需还原录像状态
							MyApp.shouldVideoRecordWhenChangeMute = false;
							new Thread(new StartRecordWhenChangeMuteThread())
									.start();
						}
					}
				} else if ("dvr_record_mic_close".equals(command)) { // 关闭录音-MUTE
					// 切换录音/静音状态停止录像，需要重置时间
					if (muteState == Constant.Record.STATE_UNMUTE) {
						MyApp.shouldVideoRecordWhenChangeMute = MyApp.isFrontRecording;
						setFrontMute(true, true);
						muteState = Constant.Record.STATE_MUTE;
						editor.putBoolean("videoMute", true);
						editor.commit();

						setupRecordViews();
						if (MyApp.shouldVideoRecordWhenChangeMute) { // 修改录音/静音后按需还原录像状态
							MyApp.shouldVideoRecordWhenChangeMute = false;
							new Thread(new StartRecordWhenChangeMuteThread())
									.start();
						}
					}
				}
			} else if (action.equals(Constant.Broadcast.MEDIA_FORMAT)) {
				String path = intent.getExtras().getString("path");
				MyLog.e("MEDIA_FORMAT !! Path:" + path);
				if (Constant.Path.SDCARD_1.equals(path)) {
					MyApp.isVideoCardFormat = true;
				}
			} else if (Constant.Broadcast.GOING_SHUTDOWN.equals(action)) {
				MyApp.isGoingShutdown = true;
			} else if (Constant.Broadcast.RELEASE_RECORD.equals(action)) { // 退出录像
				killAutoRecord();
			} else if (Constant.Broadcast.RELEASE_RECORD_TEST.equals(action)) {
				killAutoRecordForTest();
			} else if ("tchip.intent.action.MOVE_RECORD_BACK".equals(action)) {
				moveTaskToBack(true);
			} else if (Constant.Broadcast.GPS_STATUS.equals(action)) {
				try {
					Bundle budle = intent.getExtras();
					adasSpeed = budle.getInt("speed");
					recordSpeed = budle.getInt("speed");
				} catch (Exception e) {
					MyLog.e("GPS", "GPS_STATUS.Catch " + e.toString());
				}
			} else if ("android.net.conn.CONNECTIVITY_CHANGE".equals(action)) {
				if (!licenseInterface.isLicensed(context)) {
					authAdas();
				}
			}
		}
	}

	private void speakVoice(String content) {
		sendBroadcast(new Intent(Constant.Broadcast.TTS_SPEAK).putExtra(
				"content", content));
	}

	/**
	 * 序列任务线程，分步执行：
	 * 
	 * 1.初次启动清空录像文件夹
	 * 
	 * 2.自动录像
	 * 
	 */
	public class AutoThread implements Runnable {

		@Override
		public void run() {
			try {
				StartCheckErrorFileThread();

				Thread.sleep(Constant.Record.autoRecordDelay);
				if (MyApp.isParkRecording) {
					setRecordInterval(3 * 60); // 防止在分段一分钟的时候，停车守卫录出1分和0秒两段视频
				} else {
					switch (intervalState) {
					case 5:
						setRecordInterval(300);
						break;

					case 1:
						setRecordInterval(60);
						break;

					case 3:
					default:
						setRecordInterval(180);
						break;
					}
				}
				// 自动录像:如果已经在录像则不处理
				if (Constant.Record.autoRecordFront && !isFrontRecord()) {
					if (!StorageUtil.isFrontCardExist()) {
						Message message = new Message();
						message.what = 3;
						autoHandler.sendMessage(message);
					} else {
						Message message = new Message();
						message.what = 1;
						autoHandler.sendMessage(message);
					}
				}

				if (!isBackRecord()) {
					Message message = new Message();
					message.what = 2;
					autoHandler.sendMessage(message);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
				MyLog.e("AutoThread: Catch Exception!");
			}
		}
	}

	final Handler autoHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				startRecordFront();
				break;

			case 2:
				startRecordBack();
				break;

			case 3:
				noVideoSDHint();
				break;

			default:
				break;
			}
		}
	};

	/** 后台线程，用以监测是否需要录制碰撞加锁视频(停车侦测) */
	public class BackgroundThread implements Runnable {

		@Override
		public void run() {
			while (true) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				Message message = new Message();
				message.what = 1;
				backgroundHandler.sendMessage(message);
				// 修正标志：不对第二段视频加锁
				if (MyApp.isFrontLockSecond && !MyApp.isFrontRecording) {
					MyApp.isFrontLockSecond = false;
				}
				if (MyApp.isBackLockSecond && !MyApp.isBackRecording) {
					MyApp.isBackLockSecond = false;
				}

				if (!MyApp.isAccOn && !MyApp.isFrontRecording
						&& !MyApp.isBackRecording && !isFroceSleeping) {
					new Thread(new ForceSleepThread()).start();
				}

				if (MyApp.needDeleteLockHint) {
					MyApp.needDeleteLockHint = false;
					Message messageDeleteLock = new Message();
					messageDeleteLock.what = 2;
					backgroundHandler.sendMessage(messageDeleteLock);
				}

				// 开关裁剪
				if (recorderFront != null) {
					if ("1".equals(ProviderUtil.getValue(context,
							Name.ADAS_INDOOR_DEBUG, "0"))) {
						recorderFront.setScaledStreamEnable(true, 640, 480);
					} else {
						if (adasSpeed >= MyApp.adasThreshold) {
							recorderFront.setScaledStreamEnable(true, 640, 480);
						} else {
							recorderFront
									.setScaledStreamEnable(false, 640, 480);
						}
					}

				}
			}
		}
	}

	private boolean isFroceSleeping = false;

	public class ForceSleepThread implements Runnable {

		@Override
		public void run() {
			MyLog.w("[ForceSleepThread]RUN");
			isFroceSleeping = true;
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			if (!MyApp.isAccOn && !MyApp.isFrontRecording
					&& !MyApp.isBackRecording) {
				MyApp.isParkRecording = false;
				ProviderUtil.setValue(context, Name.PARK_REC_STATE, "0");
				killAutoRecord();
				sendBroadcast(new Intent(Constant.Broadcast.KILL_APP).putExtra(
						"name", "com.tchip.autorecord"));
			}
			isFroceSleeping = false;
			MyLog.w("[ForceSleepThread]END");
		}
	}

	/**
	 * 后台线程的Handler,监测：
	 * 
	 * 1.是否需要休眠唤醒
	 * 
	 * 2.停车守卫侦测，启动录像
	 * 
	 * 3.ACC下电，拍照
	 * 
	 * 4.插入录像卡，若ACC在，启动录像
	 */
	final Handler backgroundHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				if (MyApp.shouldWakeRecord) {
					MyApp.shouldWakeRecord = false;
					if (MyApp.isAccOn && !isFrontRecord()) {
						new Thread(new AutoThread()).start(); // 序列任务线程
					}
				}
				if (MyApp.shouldMountRecordFront) {
					MyApp.shouldMountRecordFront = false;
					if (MyApp.isAccOn && !isFrontRecord()) {
						if (!StorageUtil.isFrontCardExist()) {
							// noVideoSDHint();
						} else {
							new Thread(new RecordFrontWhenMountThread())
									.start();
						}
					}
				}
				if (MyApp.shouldMountRecordBack) {
					MyApp.shouldMountRecordBack = false;
					if (MyApp.isAccOn && !isBackRecord()) {
						new Thread(new RecordBackWhenMountThread()).start();
					}
				}
				break;

			case 2:
				this.removeMessages(2);
				HintUtil.showToast(
						context,
						getResources().getString(
								R.string.hint_storage_full_and_delete_lock));
				MyApp.needDeleteLockHint = false;
				this.removeMessages(2);
				break;

			default:
				break;
			}
		}
	};

	/** 插入录像卡录制一个视频线程 */
	public class RecordFrontWhenMountThread implements Runnable {

		@Override
		public void run() {
			MyLog.v("Front.run RecordWhenMountThread");
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			Message message = new Message();
			message.what = 1;
			recordFrontWhenMountHandler.sendMessage(message);
		}

	}

	/** 插入视频卡时录制视频 */
	final Handler recordFrontWhenMountHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				try {
					if (!isFrontRecord()) {
						startFrontRecord();
					}
					MyLog.v("isFrontRecording:" + MyApp.isFrontRecording);
				} catch (Exception e) {
					MyLog.e("recordWhenMountHandler catch exception: "
							+ e.toString());
				}
				break;

			default:
				break;
			}
		}
	};

	/** 插入录像卡录制一个视频线程 */
	public class RecordBackWhenMountThread implements Runnable {

		@Override
		public void run() {
			MyLog.v("Back.run RecordWhenMountThread");
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			Message message = new Message();
			message.what = 1;
			recordBackWhenMountHandler.sendMessage(message);
		}

	}

	/** 插入视频卡时录制视频 */
	final Handler recordBackWhenMountHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				try {
					if (!isBackRecord()) {
						startBackRecord();
					}
					MyLog.v("isBackRecording:" + MyApp.isBackRecording);
				} catch (Exception e) {
					MyLog.e("recordWhenMountHandler catch exception: "
							+ e.toString());
				}
				break;

			default:
				break;
			}
		}
	};

	/** 初始化布局 */
	private void initialLayout() {
		layoutBack = (RelativeLayout) findViewById(R.id.layoutBack);
		layoutBack.setVisibility(View.GONE);
		layoutFront = (RelativeLayout) findViewById(R.id.layoutFront);
		layoutFront.setVisibility(View.VISIBLE);
		scrollPreview = (ScrollView) findViewById(R.id.scrollPreview);
		scrollPreview.setOnTouchListener(new View.OnTouchListener() {

			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return true; // 禁止滑动
			}
		});
		initialRecordSurface();

		textLatLng = (TextView) findViewById(R.id.textLatLng);
		textLatLng.setTypeface(Typefaces.get(this, Constant.Path.FONT
				+ "Font-Helvetica-Neue-LT-Pro.otf"));

		textPositionCenter = (TextView) findViewById(R.id.textPositionCenter);
		textPositionRight = (TextView) findViewById(R.id.textPositionRight);

		textTimeCenter = (TextView) findViewById(R.id.textTimeCenter);
		textTimeCenter.setTypeface(Typefaces.get(this, Constant.Path.FONT
				+ "Font-Quartz-Regular.ttf"));
		textTimeRight = (TextView) findViewById(R.id.textTimeRight);
		textTimeRight.setTypeface(Typefaces.get(this, Constant.Path.FONT
				+ "Font-Quartz-Regular.ttf"));

		textSystemTime = (TextClock) findViewById(R.id.textSystemTime);
		textSystemTime.setTypeface(Typefaces.get(this, Constant.Path.FONT
				+ "Font-Helvetica-Neue-LT-Pro.otf"));

		// 录制
		imageRecordState = (ImageButton) findViewById(R.id.imageRecordState);
		imageRecordState.setOnClickListener(myOnClickListener);

		// 锁定
		imageVideoLock = (ImageButton) findViewById(R.id.imageVideoLock);
		imageVideoLock.setOnClickListener(myOnClickListener);
		textVideoLock = (TextView) findViewById(R.id.textVideoLock);
		textVideoLock.setOnClickListener(myOnClickListener);

		// 前后切换图标
		imageCameraSwitch = (ImageButton) findViewById(R.id.imageCameraSwitch);
		imageCameraSwitch.setOnClickListener(myOnClickListener);
		textCameraSwitch = (TextView) findViewById(R.id.textCameraSwitch);
		textCameraSwitch.setOnClickListener(myOnClickListener);

		// 退出
		imageExit = (ImageButton) findViewById(R.id.imageExit);
		imageExit.setOnClickListener(myOnClickListener);
		textExit = (TextView) findViewById(R.id.textExit);
		textExit.setOnClickListener(myOnClickListener);

		// 拍照
		imagePhotoTake = (ImageButton) findViewById(R.id.imagePhotoTake);
		imagePhotoTake.setOnClickListener(myOnClickListener);

		// 视频尺寸
		imageVideoSize = (ImageButton) findViewById(R.id.imageVideoSize);
		imageVideoSize.setOnClickListener(myOnClickListener);
		textVideoSize = (TextView) findViewById(R.id.textVideoSize);
		textVideoSize.setOnClickListener(myOnClickListener);

		// 视频分段长度
		imageVideoLength = (ImageButton) findViewById(R.id.imageVideoLength);
		imageVideoLength.setOnClickListener(myOnClickListener);
		textVideoLength = (TextView) findViewById(R.id.textVideoLength);
		textVideoLength.setOnClickListener(myOnClickListener);

		// 静音
		imageVideoMute = (ImageButton) findViewById(R.id.imageVideoMute);
		imageVideoMute.setOnClickListener(myOnClickListener);
		textVideoMute = (TextView) findViewById(R.id.textVideoMute);
		textVideoMute.setOnClickListener(myOnClickListener);

		// 倒车线
		layoutBackLine = (LinearLayout) findViewById(R.id.layoutBackLine);
		backLineView = new BackLineView(this);
		layoutBackLineControl = (RelativeLayout) findViewById(R.id.layoutBackLineControl);
		layoutBackLineControl.setVisibility(View.GONE);
		imageBackLineShow = (ImageButton) findViewById(R.id.imageBackLineShow);
		imageBackLineShow.setOnClickListener(myOnClickListener);
		imageBackLineEdit = (ImageButton) findViewById(R.id.imageBackLineEdit);
		imageBackLineEdit.setOnClickListener(myOnClickListener);
		imageBackLineReset = (ImageButton) findViewById(R.id.imageBackLineReset);
		imageBackLineReset.setOnClickListener(myOnClickListener);

		String strBackLineShow = ProviderUtil.getValue(context,
				Name.BACK_LINE_SHOW, "1");
		if ("0".equals(strBackLineShow)) {
			imageBackLineShow.setImageDrawable(getResources().getDrawable(
					R.drawable.back_line_hide, null));
			imageBackLineEdit.setVisibility(View.GONE);
			imageBackLineReset.setVisibility(View.GONE);
		} else {
			imageBackLineShow.setImageDrawable(getResources().getDrawable(
					R.drawable.back_line_show, null));
			if (MyApp.isFrontRecording) {
				imageBackLineEdit.setVisibility(View.VISIBLE);
				imageBackLineReset.setVisibility(View.VISIBLE);
			}
		}
	}

	/** 切换前后摄像画面 */
	private void switchCameraTo(int camera) {
		MyLog.v("switchCameraTo:" + camera);
		switch (camera) {
		case 0: // FRONT
			layoutFront.setVisibility(View.VISIBLE);
			imageAdas.setVisibility(View.VISIBLE);
			surfaceViewFront.setLayoutParams(new RelativeLayout.LayoutParams(
					CAMERA_WIDTH, CAMERA_HEIGHT_FRONT));
			surfaceViewBack.setLayoutParams(new RelativeLayout.LayoutParams(1,
					1));
			layoutBack.setVisibility(View.GONE);
			setBackLineVisible(false);
			textPositionCenter.setVisibility(View.GONE);
			textPositionRight.setVisibility(View.VISIBLE);
			textPositionRight.setText(getString(R.string.hint_position_front));

			if (MyApp.isFrontRecording) {
				textTimeRight.setVisibility(View.VISIBLE);
				textTimeCenter.setVisibility(View.GONE);
			} else {
				textTimeRight.setVisibility(View.GONE);
				textTimeCenter.setVisibility(View.GONE);
			}
			break;

		case 1: // BACK
			layoutBack.setVisibility(View.VISIBLE);
			surfaceViewBack.setLayoutParams(new RelativeLayout.LayoutParams(
					CAMERA_WIDTH, CAMERA_HEIGHT_BACK));
			surfaceViewFront.setLayoutParams(new RelativeLayout.LayoutParams(1,
					1));

			String strBackState = ProviderUtil.getValue(context,
					Name.BACK_CAR_STATE, "0");
			if ("1".equals(strBackState)) {
				setBackLineVisible(true);
				textPositionCenter.setVisibility(View.GONE);
				textPositionRight.setVisibility(View.GONE);
				textTimeRight.setVisibility(View.GONE);
				textTimeCenter.setVisibility(View.GONE);
			} else {
				setBackLineVisible(false);
				textPositionCenter.setVisibility(View.GONE);
				textPositionRight.setVisibility(View.VISIBLE);
				textPositionRight
						.setText(getString(R.string.hint_position_back));

				if (MyApp.isFrontRecording) {
					textTimeRight.setVisibility(View.VISIBLE);
					textTimeCenter.setVisibility(View.GONE);
				} else {
					textTimeRight.setVisibility(View.GONE);
					textTimeCenter.setVisibility(View.GONE);
				}
			}
			break;

		case 2: { // FRONT + BACK
			layoutFront.setVisibility(View.VISIBLE);
			imageAdas.setVisibility(View.GONE);
			surfaceViewFront.setLayoutParams(new RelativeLayout.LayoutParams(
					CAMERA_WIDTH / 2, CAMERA_HEIGHT_FRONT));
			RelativeLayout.LayoutParams backLayoutParams = new RelativeLayout.LayoutParams(
					CAMERA_WIDTH / 2, CAMERA_HEIGHT_FRONT);
			backLayoutParams.leftMargin = CAMERA_WIDTH / 2;
			surfaceViewBack.setLayoutParams(backLayoutParams);
			layoutBack.setVisibility(View.GONE);
			setBackLineVisible(false);

			textPositionCenter.setVisibility(View.VISIBLE);
			textPositionRight.setVisibility(View.VISIBLE);
			textPositionRight.setText(getString(R.string.hint_position_back));

			if (MyApp.isFrontRecording) {
				textTimeRight.setVisibility(View.VISIBLE);
				textTimeCenter.setVisibility(View.VISIBLE);
			} else {
				textTimeRight.setVisibility(View.GONE);
				textTimeCenter.setVisibility(View.GONE);
			}
		}
			break;

		default:
			break;
		}
		// 更新录像界面按钮状态
		refreshFrontButton();
		setupRecordViews();
	}

	/** 切换前后摄像画面 */
	private void switchCameraWhenBackOver(int camera) {
		switch (camera) {
		case 0:
			layoutFront.setVisibility(View.VISIBLE);
			surfaceViewFront.setLayoutParams(new RelativeLayout.LayoutParams(
					CAMERA_WIDTH, CAMERA_HEIGHT_FRONT));
			surfaceViewBack.setLayoutParams(new RelativeLayout.LayoutParams(1,
					1));
			layoutBack.setVisibility(View.GONE);
			setBackLineVisible(false);

			textPositionCenter.setVisibility(View.GONE);
			textPositionRight.setVisibility(View.VISIBLE);
			textPositionRight.setText(getString(R.string.hint_position_front));

			if (MyApp.isFrontRecording) {
				textTimeRight.setVisibility(View.VISIBLE);
				textTimeCenter.setVisibility(View.GONE);
			} else {
				textTimeRight.setVisibility(View.GONE);
				textTimeCenter.setVisibility(View.GONE);
			}
			break;

		case 1:
			layoutFront.setVisibility(View.VISIBLE);
			surfaceViewBack.setLayoutParams(new RelativeLayout.LayoutParams(
					CAMERA_WIDTH, CAMERA_HEIGHT_BACK));
			surfaceViewFront.setLayoutParams(new RelativeLayout.LayoutParams(1,
					1));

			textPositionCenter.setVisibility(View.GONE);
			textPositionRight.setVisibility(View.VISIBLE);
			textPositionRight.setText(getString(R.string.hint_position_back));

			if (MyApp.isFrontRecording) {
				textTimeRight.setVisibility(View.VISIBLE);
				textTimeCenter.setVisibility(View.GONE);
			} else {
				textTimeRight.setVisibility(View.GONE);
				textTimeCenter.setVisibility(View.GONE);
			}
			break;

		case 2: { // FRONT + BACK
			layoutFront.setVisibility(View.VISIBLE);
			imageAdas.setVisibility(View.GONE);
			surfaceViewFront.setLayoutParams(new RelativeLayout.LayoutParams(
					CAMERA_WIDTH / 2, CAMERA_HEIGHT_FRONT));
			RelativeLayout.LayoutParams backLayoutParams = new RelativeLayout.LayoutParams(
					CAMERA_WIDTH / 2, CAMERA_HEIGHT_FRONT);
			backLayoutParams.leftMargin = CAMERA_WIDTH / 2;
			surfaceViewBack.setLayoutParams(backLayoutParams);
			layoutBack.setVisibility(View.GONE);
			setBackLineVisible(false);

			textPositionCenter.setVisibility(View.VISIBLE);
			textPositionRight.setVisibility(View.VISIBLE);
			textPositionRight.setText(getString(R.string.hint_position_back));

			if (MyApp.isFrontRecording) {
				textTimeRight.setVisibility(View.VISIBLE);
				textTimeCenter.setVisibility(View.VISIBLE);
			} else {
				textTimeRight.setVisibility(View.GONE);
				textTimeCenter.setVisibility(View.GONE);
			}
		}
			break;

		default:
			break;
		}
	}

	/**
	 * @param isVisible
	 *            是否倒车
	 */
	private void setBackLineVisible(boolean isVisible) {
		MyLog.v("setBackLineVisible:" + isVisible);
		if (isVisible) {
			// 确保显示后摄,解决倒车线在前摄界面
			scrollPreview.scrollTo(0, 240);

			layoutBack.setVisibility(View.VISIBLE);
			surfaceViewBack.setLayoutParams(new RelativeLayout.LayoutParams(
					CAMERA_WIDTH, CAMERA_HEIGHT_BACK));
			surfaceViewFront.setLayoutParams(new RelativeLayout.LayoutParams(1,
					1));
			layoutFront.setVisibility(View.GONE);

			layoutBackLineControl.setVisibility(View.VISIBLE);
			String strBackLineShow = ProviderUtil.getValue(context,
					Name.BACK_LINE_SHOW, "1");
			if ("0".equals(strBackLineShow)) {
				layoutBackLine.removeAllViews();
			} else {
				backLineView.invalidate(); // 通知view组件重绘
				layoutBackLine.removeAllViews();
				layoutBackLine.addView(backLineView);
			}
		} else {
			scrollPreview.scrollTo(0, 0);

			layoutBackLineControl.setVisibility(View.GONE);
			layoutBackLine.removeAllViews();
		}
	}

	/** 更新倒车线控制图标 */
	private void updateBackLineControlView() {
		String strBackLineShow = ProviderUtil.getValue(context,
				Name.BACK_LINE_SHOW, "1");
		if ("0".equals(strBackLineShow)) {
			imageBackLineShow.setImageDrawable(getResources().getDrawable(
					R.drawable.back_line_hide, null));
			layoutBackLine.removeAllViews();
			imageBackLineEdit.setVisibility(View.GONE);
			imageBackLineReset.setVisibility(View.GONE);
		} else {
			imageBackLineShow.setImageDrawable(getResources().getDrawable(
					R.drawable.back_line_show, null));
			backLineView.invalidate(); // 通知view组件重绘
			layoutBackLine.removeAllViews();
			layoutBackLine.addView(backLineView);

			imageBackLineEdit.setVisibility(View.VISIBLE);
			imageBackLineReset.setVisibility(View.VISIBLE);
			acquireFullWakeLock(); // 处理开机时已经在倒车情形
		}
	}

	private MyOnClickListener myOnClickListener = new MyOnClickListener();

	class MyOnClickListener implements View.OnClickListener {
		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.imageRecordState:
				if (!ClickUtil.isQuickClick(1000)) {
					if (MyApp.isFrontRecording) {
						speakVoice(getResources().getString(
								R.string.hint_record_stop));
						MyLog.v("[onClick]stopRecorder()");
						stopFrontRecorder5Times();
					} else {
						if (!MyApp.isAccOn) {
							HintUtil.showToast(
									context,
									getResources().getString(
											R.string.hint_stop_record_sleeping));
						} else if (StorageUtil.isFrontCardExist()) {
							if (isFrontRecord()) {
								recorderFront.stop();
								ProviderUtil.setValue(context,
										Name.REC_FRONT_STATE, "0");
							}
							speakVoice(getResources().getString(
									R.string.hint_record_start));
							startRecordFront();
						} else {
							noVideoSDHint();
						}
					}

					if (MyApp.isBackRecording) {
						MyLog.v("[onClick]stopRecorder()");
						// stopBackRecorder5Times();
						if (stopBackRecorder() == 0) {
							setBackState(false);
						}
						editor.putBoolean(Constant.MySP.STR_SHOULD_RECORD_BACK,
								false);
						editor.commit();
					} else {
						if (!MyApp.isAccOn) {
							HintUtil.showToast(
									context,
									getResources().getString(
											R.string.hint_stop_record_sleeping));
						} else if (StorageUtil.isBackCardExist()) {
							if (isBackRecord()) {
								recorderBack.stop();
								ProviderUtil.setValue(context,
										Name.REC_BACK_STATE, "0");
							}
							startRecordBack();
						} else {
						}
					}
				}
				break;

			case R.id.imageVideoLock:
			case R.id.textVideoLock:
				if (!ClickUtil.isQuickClick(500)) {
					if (MyApp.isFrontRecording) {
						lockOrUnlockVideo();
					} else { // 未录像
						if (!MyApp.isAccOn) {
							HintUtil.showToast(
									context,
									getResources().getString(
											R.string.hint_stop_record_sleeping));
						} else if (StorageUtil.isFrontCardExist()) {
							if (isFrontRecord()) {
								recorderFront.stop();
								ProviderUtil.setValue(context,
										Name.REC_FRONT_STATE, "0");
							}
							speakVoice(getResources().getString(
									R.string.hint_record_start));
							startRecordFront();

							if (isBackRecord()) {
								recorderBack.stop();
								ProviderUtil.setValue(context,
										Name.REC_BACK_STATE, "0");
							}
							startRecordBack();

							MyApp.isFrontLock = true;
							MyApp.isBackLock = true;
							setupRecordViews();
						} else {
							noVideoSDHint();
						}
					}
				}
				break;

			case R.id.imageVideoSize:
			case R.id.textVideoSize:
				if (!ClickUtil.isQuickClick(3000)) {
					// 切换分辨率录像停止，需要重置时间
					MyApp.shouldVideoRecordWhenChangeSize = MyApp.isFrontRecording;
					MyApp.isFrontRecording = false;
					resetRecordTimeText();
					// textTimeFront.setVisibility(View.INVISIBLE);
					// textTimeBack.setVisibility(View.INVISIBLE);
					if (MyApp.resolutionState == 1080) {
						setFrontResolution(720);
						editor.putString("videoSize", "720");
						MyApp.isFrontRecording = false;
						speakVoice(getResources().getString(
								R.string.hint_video_size_720));
					} else if (MyApp.resolutionState == 720) {
						setFrontResolution(1080);
						editor.putString("videoSize", "1080");
						MyApp.isFrontRecording = false;
						speakVoice(getResources().getString(
								R.string.hint_video_size_1080));
					}
					editor.commit();
				}
				break;

			case R.id.imageVideoLength:
			case R.id.textVideoLength:
				if (!ClickUtil.isQuickClick(500)) {
					if (intervalState == 5) {
						if (setRecordInterval(60) == 0) { // 5 -> 1
							intervalState = 1;
							editor.putString("videoTime", "1");
							speakVoice(getResources().getString(
									R.string.hint_video_time_1));
						}
					} else if (intervalState == 3) { // 3 -> 5
						if (setRecordInterval(300) == 0) {
							intervalState = 5;
							editor.putString("videoTime", "5");
							speakVoice(getResources().getString(
									R.string.hint_video_time_5));
						}
					} else if (intervalState == 1) { // 1 -> 3
						if (setRecordInterval(180) == 0) {
							intervalState = 3;
							editor.putString("videoTime", "3");
							speakVoice(getResources().getString(
									R.string.hint_video_time_3));
						}
					}
					editor.commit();
					setupRecordViews();
				}
				break;

			case R.id.imageVideoMute:
			case R.id.textVideoMute:
				if (!ClickUtil.isQuickClick(1000)) {
					// 切换录音/静音状态停止录像，需要重置时间
					MyApp.shouldVideoRecordWhenChangeMute = MyApp.isFrontRecording;
					if (muteState == Constant.Record.STATE_MUTE) {
						setFrontMute(false, true);
						muteState = Constant.Record.STATE_UNMUTE;
						editor.putBoolean("videoMute", false);
						editor.commit();
					} else if (muteState == Constant.Record.STATE_UNMUTE) {
						setFrontMute(true, true);
						muteState = Constant.Record.STATE_MUTE;
						editor.putBoolean("videoMute", true);
						editor.commit();
					}
					setupRecordViews();
					if (MyApp.shouldVideoRecordWhenChangeMute) { // 修改录音/静音后按需还原录像状态
						MyApp.shouldVideoRecordWhenChangeMute = false;
						new Thread(new StartRecordWhenChangeMuteThread())
								.start();
					}
				}
				break;

			case R.id.imagePhotoTake:
				if (!ClickUtil.isQuickClick(1000)) {
					takePhoto(true);
				}
				break;

			case R.id.surfaceViewBack:
			case R.id.surfaceViewFront:
			case R.id.imageCameraSwitch:
			case R.id.textCameraSwitch:
				if (Constant.Module.hasCVBSDetect && !SettingUtil.isCVBSIn()) {
					HintUtil.showToast(context,
							getString(R.string.no_cvbs_detect));
				} else {
					if (cameraBeforeBack == 0) {
						switchCameraTo(2); // TODO:FIXME
						cameraBeforeBack = 2;
					} else if (cameraBeforeBack == 2) {
						switchCameraTo(1);
						cameraBeforeBack = 1;
					} else {
						switchCameraTo(0);
						cameraBeforeBack = 0;
					}
				}
				break;

			case R.id.imageBackLineShow:
				String strBackLineShow = ProviderUtil.getValue(context,
						Name.BACK_LINE_SHOW, "1");
				if ("1".equals(strBackLineShow)) {
					ProviderUtil.setValue(context, Name.BACK_LINE_SHOW, "0");
				} else {
					ProviderUtil.setValue(context, Name.BACK_LINE_SHOW, "1");
				}
				updateBackLineControlView();
				break;

			case R.id.imageBackLineEdit:
				boolean isModifyMode = backLineView.getModifyMode();
				backLineView.setModifyMode(!isModifyMode);
				backLineView.invalidate();
				break;

			case R.id.imageBackLineReset:
				backLineView.clearPonitConfig();
				backLineView.invalidate();
				break;

			case R.id.imageExit:
			case R.id.textExit:
				moveTaskToBack(true);
				break;

			default:
				break;
			}
		}
	}

	/** 加锁或解锁视频 */
	private void lockOrUnlockVideo() {
		if (!MyApp.isFrontLock) {
			MyApp.isFrontLock = true;
			MyApp.isBackLock = true;
			speakVoice(getResources().getString(R.string.hint_video_lock));
		} else {
			MyApp.isFrontLock = false;
			MyApp.isBackLock = false;
			MyApp.isFrontLockSecond = false;
			MyApp.isBackLockSecond = false;
			speakVoice(getResources().getString(R.string.hint_video_unlock));
		}
		setupRecordViews();
	}

	/** 视频SD卡不存在提示 */
	private void noVideoSDHint() {
		if (MyApp.isAccOn && !ClickUtil.isHintSleepTooQuick(5000)) {
			String strNoSD = getResources().getString(
					R.string.hint_sd_record_not_exist);
			HintUtil.showToast(context, strNoSD);
			speakVoice(strNoSD);
		} else {
		}
	}

	/** 拍照 */
	public void takePhoto(boolean playSound) {
		if (!MyApp.isFrontRecording && !MyApp.isBackRecording
				&& !StorageUtil.isFrontCardExist()) { // 判断SD卡2是否存在，需要耗费一定时间
			noVideoSDHint(); // SDCard不存在
		} else {
			if (recorderFront != null) {
				setFrontDirectory(); // 设置保存路径，否则会保存到内部存储
				HintUtil.playAudio(
						getApplicationContext(),
						com.tchip.tachograph.TachographCallback.FILE_TYPE_IMAGE,
						playSound);
				recorderFront.takePicture();
			}

			if (recorderBack != null) {
				setFrontDirectory(); // 设置保存路径，否则会保存到内部存储
				recorderBack.takePicture();
			}
		}
	}

	class ReleaseFrontStorageThread implements Runnable {

		@Override
		public void run() {
			StorageUtil.releaseFrontStorage(context);
		}

	}

	class ReleaseBackStorageThread implements Runnable {

		@Override
		public void run() {
			StorageUtil.releaseBackStorage(context);
		}

	}

	/** 前录:切换分辨率 */
	private class ChangeFrontSizeThread implements Runnable {

		@Override
		public void run() {
			releaseFrontRecorder();
			closeFrontCamera();
			if (openFrontCamera()) {
				setupFrontRecorder();
			}
			mMainHandler.post(new Runnable() {
				@Override
				public void run() {
					setupRecordViews();
				}
			});
			if (MyApp.shouldVideoRecordWhenChangeSize) { // 修改分辨率后按需启动录像
				MyApp.shouldVideoRecordWhenChangeSize = false;
				try {
					Thread.sleep(1000);
				} catch (Exception e) {
				}
				startFrontRecord();
			}
		}

	}

	private class StartFrontRecordThread implements Runnable {

		@Override
		public void run() {
			if (!isFrontRecord() && MyApp.isFrontPreview
					&& recorderFront != null) {
				MyLog.d("Front.Record Start");
				setFrontDirectory(); // 设置保存路径
				if (sharedPreferences.getBoolean("videoMute",
						Constant.Record.muteDefault)) {
					setFrontMute(true, false); // 设置录像静音
				} else {
					setFrontMute(false, false);
				}
				final boolean isDeleteFrontSuccess = StorageUtil
						.releaseFrontStorage(context);
				if (isDeleteFrontSuccess) {
					if (MyApp.isFrontPreview && recorderFront.start() == 0) {
						mMainHandler.post(new Runnable() {

							@Override
							public void run() {
								resetRecordTimeText();
								HintUtil.playAudio(
										getApplicationContext(),
										com.tchip.tachograph.TachographCallback.FILE_TYPE_VIDEO,
										MyApp.isAccOn);

								setRecordState(true);
							}
						});
					}
				}
			}
		}
	}

	private class StartBackRecordThread implements Runnable {

		@Override
		public void run() {
			if (!isBackRecord() && MyApp.isBackPreview && recorderBack != null) {
				MyLog.d("Back.Start Record");
				setBackDirectory(); // 设置保存路径
				setBackMute(true, false); // 设置录像静音

				final boolean isDeleteBackSuccess = StorageUtil
						.releaseBackStorage(context);
				mMainHandler.post(new Runnable() {

					@Override
					public void run() {
						if (isDeleteBackSuccess) {
							if (MyApp.isBackPreview
									&& recorderBack.start() == 0) {
								setBackState(true);
							}
						}
					}
				});
			}
		}

	}

	/** 停止录像x5 */
	private void stopFrontRecorder5Times() {
		if (isFrontRecord()) {
			new Thread(new StopFrontRecordThread()).start();
			textTimeCenter.setVisibility(View.INVISIBLE);
			textTimeRight.setVisibility(View.INVISIBLE);
			resetRecordTimeText();
		}
	}

	/** 停止录像x5 */
	private void stopBackRecorder5Times() {
		if (isBackRecord()) {
			new Thread(new StopBackRecordThread()).start();
		}
	}

	private class StopFrontRecordThread implements Runnable {

		@Override
		public void run() {
			if (isFrontRecord()) {
				try {
					int tryTime = 0;
					while (stopFrontRecorder() != 0 && tryTime < 5) { // 停止录像
						tryTime++;
						Thread.sleep(500);
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					ProviderUtil.setValue(context, Name.REC_FRONT_STATE, "0");
					mMainHandler.post(new Runnable() {

						@Override
						public void run() {
							setRecordState(false);
						}
					});
				}
			}
			// 处理停车录像过程中，拔卡停止录像或者手动停止录像情况
			if (!MyApp.isAccOn && !MyApp.isBackRecording) {
				if (!MyApp.isParkRecording) {
				} else {
					MyApp.isParkRecording = false;
					ProviderUtil.setValue(context, Name.PARK_REC_STATE, "0");
				}
				new Thread(new CloseRecordThread()).start();
			}
		}

	}

	private class StopBackRecordThread implements Runnable {

		@Override
		public void run() {
			try {
				int tryTime = 0;
				while (stopBackRecorder() != 0 && tryTime < 5) { // 停止录像
					tryTime++;
					Thread.sleep(500);
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				ProviderUtil.setValue(context, Name.REC_BACK_STATE, "0");
				mMainHandler.post(new Runnable() {

					@Override
					public void run() {
						setBackState(false);
					}
				});
			}
			// 处理停车录像过程中，拔卡停止录像或者手动停止录像情况
			if (!MyApp.isAccOn && !MyApp.isFrontRecording) {
				if (!MyApp.isParkRecording) {
				} else {
					MyApp.isParkRecording = false;
					ProviderUtil.setValue(context, Name.PARK_REC_STATE, "0");
				}
				new Thread(new CloseRecordThread()).start();
			}
		}

	}

	/** 检查并删除异常视频文件：SD存在但数据库中不存在的文件 */
	private void StartCheckErrorFileThread() {
		MyLog.v("CheckErrorFile.");
		if (!isVideoChecking) {
			new Thread(new CheckVideoThread()).start();
		}

		new Thread(new CreateThumbnailThread()).start();
	}

	/** 当前是否正在校验错误视频 */
	private boolean isVideoChecking = false;

	private class CheckVideoThread implements Runnable {

		@Override
		public void run() {
			isVideoChecking = true;
			File dirRecord = new File(Constant.Path.RECORD_DIRECTORY);
			StorageUtil.RecursionCheckDotFile(MainActivity.this, dirRecord);
			isVideoChecking = false;
		}

	}

	private class CreateThumbnailThread implements Runnable {
		@Override
		public void run() {
			File dirRecord = new File(Constant.Path.RECORD_DIRECTORY);
			StorageUtil.CreateThumbnailForVideo(MainActivity.this, dirRecord);
		}
	}

	private void sendKeyCode(final int keyCode) {
		new Thread() {
			public void run() {
				try {
					Instrumentation inst = new Instrumentation();
					inst.sendKeyDownUpSync(keyCode);
				} catch (Exception e) {
					MyLog.e("Exception when sendPointerSync:" + e.toString());
				}
			}
		}.start();
	}

	class FrontCallBack implements Callback {

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			MyLog.v("Front.surfaceChanged");
			// surfaceHolder = holder;
		}

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			MyLog.v("Front.surface Created");
			if (cameraFront == null) {
				surfaceHolderFront = holder;
				// Setup Front
				releaseFrontRecorder();
				closeFrontCamera();
				if (openFrontCamera()) {
					setupFrontRecorder();
				}
			} else {
				previewFrontCamera();
			}

		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			MyLog.v("Front.surface Destroyed");
		}

	}

	class BackCallBack implements Callback {

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			MyLog.v("Back.surfaceChanged");
			// surfaceHolder = holder;
		}

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			MyLog.v("Back.surfaceCreated");
			if (cameraBack == null) {
				surfaceHolderBack = holder;

				// Setup Back
				releaseBackRecorder();
				closeBackCamera();
				if (openBackCamera()) {
					setupBackRecorder();
				}
			} else {
				previewBackCamera();
			}

		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			MyLog.v("Back.surfaceDestroyed");
		}

	}

	/** 绘制录像按钮 */
	private void setupRecordViews() {
		// 视频分辨率
		if (MyApp.resolutionState == 720) {
			imageVideoSize.setImageDrawable(getResources().getDrawable(
					R.drawable.video_size_hd, null));
			textVideoSize.setText(getResources().getString(
					R.string.icon_hint_720p));
		} else if (MyApp.resolutionState == 1080) {
			imageVideoSize.setImageDrawable(getResources().getDrawable(
					R.drawable.video_size_fhd, null));
			textVideoSize.setText(getResources().getString(
					R.string.icon_hint_1080p));
		}
		// 录像按钮
		imageRecordState.setImageDrawable(getResources().getDrawable(
				MyApp.isFrontRecording ? R.drawable.video_stop
						: R.drawable.video_start, null));
		// 视频分段
		if (intervalState == 5) { // 5
			imageVideoLength.setImageDrawable(getResources().getDrawable(
					R.drawable.video_length_5m, null));
			textVideoLength.setText(getResources().getString(
					R.string.icon_hint_5_minutes));
		} else if (intervalState == 1) { // 1
			imageVideoLength.setImageDrawable(getResources().getDrawable(
					R.drawable.video_length_1m, null));
			textVideoLength.setText(getResources().getString(
					R.string.icon_hint_1_minute));
		} else { // 3
			imageVideoLength.setImageDrawable(getResources().getDrawable(
					R.drawable.video_length_3m, null));
			textVideoLength.setText(getResources().getString(
					R.string.icon_hint_3_minutes));
		}
		// 视频加锁
		imageVideoLock.setImageDrawable(getResources().getDrawable(
				MyApp.isFrontLock ? R.drawable.video_lock
						: R.drawable.video_unlock, null));
		textVideoLock.setText(getResources().getString(
				MyApp.isFrontLock ? R.string.icon_hint_lock
						: R.string.icon_hint_unlock));
		// 静音按钮
		boolean videoMute = sharedPreferences.getBoolean("videoMute",
				Constant.Record.muteDefault);
		muteState = videoMute ? Constant.Record.STATE_MUTE
				: Constant.Record.STATE_UNMUTE;
		imageVideoMute.setImageDrawable(getResources().getDrawable(
				videoMute ? R.drawable.video_mute : R.drawable.video_unmute,
				null));
		textVideoMute.setText(getResources()
				.getString(
						videoMute ? R.string.icon_hint_mute
								: R.string.icon_hint_unmute));
	}

	/** 启动录像 */
	private void startRecordFront() {
		try {
			if (!isFrontRecord()) {
				if (!MyApp.isAccOn) {
					if (MyApp.isParkRecording) {
						StartCheckErrorFileThread();

						startFrontRecord();
					} else if (!ClickUtil.isHintSleepTooQuick(3000)) {
						HintUtil.showToast(MainActivity.this, getResources()
								.getString(R.string.hint_stop_record_sleeping));
					}
				} else {
					StartCheckErrorFileThread();
					startFrontRecord();
				}
			} else {
				MyLog.v("Front.startRecord.Already record yet");
			}
			setupRecordViews();
			MyLog.v("MyApp.isFrontReording:" + MyApp.isFrontRecording);
		} catch (Exception e) {
			e.printStackTrace();
			MyLog.e("Front.startRecord catch exception: " + e.toString());
		}
	}

	/** 启动录像 */
	private void startRecordBack() {
		try {
			if (!isBackRecord()) {
				if (!MyApp.isAccOn) {
					if (MyApp.isParkRecording) {
						startBackRecord();
					}
				} else {
					startBackRecord();
				}
				setupRecordViews();
			}
			MyLog.v("MyApp.isBackReording:" + MyApp.isBackRecording);
		} catch (Exception e) {
			e.printStackTrace();
			MyLog.e("Back.startRecord catch exception: " + e.toString());
		}
	}

	/**
	 * 开启录像
	 * 
	 * @return 0:成功 -1:失败
	 */
	public void startFrontRecord() {
		new Thread(new StartFrontRecordThread()).start();
	}

	/**
	 * 开启录像
	 * 
	 * @return 0:成功 -1:失败
	 */
	public void startBackRecord() {
		new Thread(new StartBackRecordThread()).start();
	}

	/**
	 * 打开摄像头
	 * 
	 * @return
	 */
	private boolean openFrontCamera() {
		if (cameraFront != null) {
			closeFrontCamera();
		}
		try {
			MyLog.v("Front.Open Camera 0");
			cameraFront = Camera.open(0);
			previewFrontCamera();
			return true;
		} catch (Exception ex) {
			closeFrontCamera();
			MyLog.e("Front.openCamera:Catch Exception!");
			return false;
		}
	}

	/** 打开摄像头 */
	private boolean openBackCamera() {
		if (cameraBack != null) {
			closeBackCamera();
		}
		try {
			MyLog.v("Back.Open Camera 1");
			cameraBack = Camera.open(1);
			previewBackCamera();
			return true;
		} catch (Exception ex) {
			closeBackCamera();
			MyLog.e("Back.openCamera:Catch Exception:" + ex);
			return false;
		}
	}

	/**
	 * Camera预览：
	 * 
	 * lock > setPreviewDisplay > startPreview > unlock
	 */
	private void previewFrontCamera() {
		MyLog.v("Front.preview Camera");
		try {
			cameraFront.lock();
			if (Constant.Module.useSystemCameraParam) { // 设置系统Camera参数
				Camera.Parameters para = cameraFront.getParameters();
				para.unflatten(Constant.Record.CAMERA_PARAMS);
				cameraFront.setParameters(para);
			}
			cameraFront.setPreviewDisplay(surfaceHolderFront);
			// camera.setDisplayOrientation(180);
			cameraFront.startPreview();
			cameraFront.unlock();
		} catch (Exception e) {
			MyApp.isFrontPreview = false;
			e.printStackTrace();
		} finally {
			MyApp.isFrontPreview = true;
		}
	}

	/**
	 * Camera预览：
	 * 
	 * lock > setPreviewDisplay > startPreview > unlock
	 */
	private void previewBackCamera() {
		MyLog.v("Back.preview Camera");
		try {
			cameraBack.lock();
			// if (Constant.Module.useSystemCameraParam) { // 设置系统Camera参数
			// Camera.Parameters para = cameraBack.getParameters();
			// para.unflatten(Constant.Record.CAMERA_PARAMS);
			// cameraBack.setParameters(para);
			// }
			cameraBack.setPreviewDisplay(surfaceHolderBack);
			// camera.setDisplayOrientation(180);
			cameraBack.startPreview();
			cameraBack.unlock();
		} catch (Exception e) {
			MyApp.isBackPreview = false;
			e.printStackTrace();
		} finally {
			MyApp.isBackPreview = true;
		}
	}

	/**
	 * 关闭Camera
	 * 
	 * lock > stopPreview > setPreviewDisplay > release > unlock
	 */
	private boolean closeFrontCamera() {
		MyLog.v("Front.Close Camera");
		if (cameraFront == null)
			return true;
		try {
			cameraFront.lock();
			cameraFront.stopPreview();
			cameraFront.setPreviewDisplay(null);
			cameraFront.release();
			cameraFront.unlock();
			cameraFront = null;
			return true;
		} catch (Exception e) {
			cameraFront = null;
			MyLog.e("Front.closeCamera:Catch Exception:" + e.toString());
			return false;
		}
	}

	/**
	 * 关闭Camera
	 * 
	 * lock > stopPreview > setPreviewDisplay > release > unlock
	 */
	private boolean closeBackCamera() {
		MyLog.v("Back.Close Camera");
		if (cameraBack == null)
			return true;
		try {
			cameraBack.lock();
			cameraBack.stopPreview();
			cameraBack.setPreviewDisplay(null);
			cameraBack.release();
			cameraBack.unlock();
			cameraBack = null;
			return true;
		} catch (Exception e) {
			cameraBack = null;
			MyLog.e("Back.closeCamera:Catch Exception:" + e.toString());
			return false;
		}
	}

	/** 设置当前录像状态 */
	private void setRecordState(boolean isVideoRecord) {
		ProviderUtil.setValue(context, Name.REC_FRONT_STATE,
				isVideoRecord ? "1" : "0");
		if (isVideoRecord) {
			if (!MyApp.isFrontRecording) {
				MyApp.isFrontRecording = true;
				textTimeRight.setVisibility(View.VISIBLE);
				if (cameraBeforeBack == 2) {
					textTimeCenter.setVisibility(View.VISIBLE);
				} else {
					textTimeCenter.setVisibility(View.GONE);
				}

				startUpdateRecordTimeThread();
				setupRecordViews();
			}
		} else {
			if (MyApp.isFrontRecording) {
				MyApp.isFrontRecording = false;
				textTimeRight.setVisibility(View.INVISIBLE);
				textTimeCenter.setVisibility(View.INVISIBLE);
				resetRecordTimeText();
				MyApp.isRecordTimeUpdating = false;
				setupRecordViews();
			}
		}
	}

	private void setBackState(boolean isVideoRecord) {
		MyLog.v("Back.setBackState:" + isVideoRecord);
		ProviderUtil.setValue(context, Name.REC_BACK_STATE, isVideoRecord ? "1"
				: "0");
		if (isVideoRecord) {
			if (!MyApp.isBackRecording) {
				MyApp.isBackRecording = true;
				setupRecordViews();
			}
		} else {
			if (MyApp.isBackRecording) {
				MyApp.isBackRecording = false;
				setupRecordViews();
			}
		}
	}

	private int recordTimeCount = -1;

	/**
	 * 录制时间秒钟复位:
	 * 
	 * 1.停止录像{@link #stopRecorder()}
	 * 
	 * 2.录像过程中更改录像分辨率
	 * 
	 * 3.录像过程中更改静音状态
	 * 
	 * 4.视频保存失败{@link #onError(int)}
	 * 
	 * 5.开始录像{@link #startRecordTask()}
	 * 
	 */
	private void resetRecordTimeText() {
		recordTimeCount = -1;
		textTimeRight.setText("00 : 00");
		textTimeCenter.setText("00 : 00");
	}

	/** 开启录像跑秒线程 */
	private void startUpdateRecordTimeThread() {
		if (!MyApp.isRecordTimeUpdating) {
			new Thread(new UpdateRecordTimeThread()).start(); // 更新录制时间
		} else {
			MyLog.e("UpdateRecordTimeThread already run");
		}
	}

	public class UpdateRecordTimeThread implements Runnable {

		@Override
		public void run() {
			// 解决录像时，快速点击录像按钮两次，线程叠加跑秒过快的问题
			do {
				MyApp.isRecordTimeUpdating = true;
				if (MyApp.isFrontCrashed) {
					Message messageVideoLock = new Message();
					messageVideoLock.what = 4;
					updateRecordTimeHandler.sendMessage(messageVideoLock);
				}
				if (MyApp.isAppException) { // 程序异常,停止录像
					MyApp.isAppException = false;
					MyLog.e("Front.App exception, stop record!");
					Message messageException = new Message();
					messageException.what = 8;
					updateRecordTimeHandler.sendMessage(messageException);
					return;
				} else if (MyApp.isVideoCardEject) { // 录像时视频SD卡拔出停止录像
					MyLog.e("Front.SD card remove badly or power unconnected, stop record!");
					Message messageEject = new Message();
					messageEject.what = 2;
					updateRecordTimeHandler.sendMessage(messageEject);
					return;
				} else if (MyApp.isVideoCardFormat) { // 录像SD卡格式化
					MyApp.isVideoCardFormat = false;
					MyLog.e("Front.SD card is format, stop record!");
					Message messageFormat = new Message();
					messageFormat.what = 7;
					updateRecordTimeHandler.sendMessage(messageFormat);
					return;
				} else if (MyApp.isGoingShutdown) {
					MyApp.isGoingShutdown = false;
					MyLog.e("Front.Going shutdown, stop record!");
					Message messageFormat = new Message();
					messageFormat.what = 9;
					updateRecordTimeHandler.sendMessage(messageFormat);
					return;
				} else if (MyApp.shouldStopFrontFromVoice) {
					MyApp.shouldStopFrontFromVoice = false;
					Message messageStopRecordFromVoice = new Message();
					messageStopRecordFromVoice.what = 6;
					updateRecordTimeHandler
							.sendMessage(messageStopRecordFromVoice);
					return;
				} else if (!MyApp.isAccOn && !MyApp.isParkRecording) { // ACC下电停止录像
					MyLog.e("Front.Stop Record:isSleeping = true");
					Message messageSleep = new Message();
					messageSleep.what = 5;
					updateRecordTimeHandler.sendMessage(messageSleep);
					return;
				} else {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					Message messageSecond = new Message();
					messageSecond.what = 1;
					updateRecordTimeHandler.sendMessage(messageSecond);
				}
			} while (MyApp.isFrontRecording);
			MyApp.isRecordTimeUpdating = false;
		}

	}

	final Handler updateRecordTimeHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				this.removeMessages(1);
				if (!ClickUtil.isPlusFrontTimeTooQuick(900)) {
					recordTimeCount++;
					if (MyApp.isFrontRecording && recordTimeCount % 10 == 0) {
						acquirePartialWakeLock(10 * 1000);
						if (recordTimeCount % 30 == 0) {
							ProviderUtil.setValue(context,
									Name.REC_FRONT_STATE, "1");
							if (MyApp.isBackRecording) {
								ProviderUtil.setValue(context,
										Name.REC_BACK_STATE, "1");
							}
						}
					}
				}

				if (!MyApp.isAccOn) { // 处理停车守卫录像
					if (MyApp.isParkRecording) {
						if (MyApp.isFrontRecording
								&& recordTimeCount == Constant.Record.parkVideoLength) {
							MyLog.v("Front.updateFrontTimeHandler.Stop Park Record");
							stopFrontRecorder5Times(); // 停止录像
							stopBackRecorder5Times();
							SettingUtil.setAirplaneMode(context, true);

							// 重设视频分段
							String videoTimeStr = sharedPreferences.getString(
									"videoTime", "3");
							if ("5".equals(videoTimeStr)) { // 5
								intervalState = 5;
								setRecordInterval(300);
							} else if ("1".equals(videoTimeStr)) { // 1
								intervalState = 1;
								setRecordInterval(60);
							} else { // 3
								intervalState = 3;
								setRecordInterval(180);
							}

							ProviderUtil.setValue(context, Name.PARK_REC_STATE,
									"0");
							MyApp.isParkRecording = false;
						}
					} else {
					}
				}
				switch (intervalState) { // 重置时间

				case 5:
					if (recordTimeCount >= 300) {
						recordTimeCount = 0;
					}
					break;

				case 3:
					if (recordTimeCount >= 180) {
						recordTimeCount = 0;
					}
					break;

				case 1:
				default:
					if (recordTimeCount >= 60) {
						recordTimeCount = 0;
					}
					break;
				}
				String recordTime = DateUtil
						.getFormatTimeBySecond(recordTimeCount);
				textTimeRight.setText(recordTime);
				textTimeCenter.setText(recordTime);

				this.removeMessages(1);
				break;

			case 2:// SD卡异常移除：停止录像
				this.removeMessages(2);
				MyLog.v("Front.UpdateRecordTimeHandler.stopRecorder() 2,Video SD Removed");
				stopFrontRecorder5Times();
				stopBackRecorder5Times();
				hintCardEject();
				this.removeMessages(2);
				break;

			case 4:
				this.removeMessages(4);
				MyApp.isFrontCrashed = false;
				MyApp.isBackCrashed = false;
				setupRecordViews();

				// 碰撞后判断是否需要加锁第二段视频
				if (intervalState == 5) {
					if (recordTimeCount > 295) {
						MyApp.isFrontLockSecond = true;
						MyApp.isBackLockSecond = true;
					}
				} else if (intervalState == 1) { // 1
					if (recordTimeCount > 55) {
						MyApp.isFrontLockSecond = true;
						MyApp.isBackLockSecond = true;
					}
				} else { // 3
					if (recordTimeCount > 175) {
						MyApp.isFrontLockSecond = true;
						MyApp.isBackLockSecond = true;
					}
				}
				this.removeMessages(4);
				break;

			case 5: // 进入休眠，停止录像
				this.removeMessages(5);
				MyLog.v("Front.UpdateRecordTimeHandler.stopRecorder() 5");
				stopFrontRecorder5Times();
				stopBackRecorder5Times();
				this.removeMessages(5);
				break;

			case 6: // 语音命令：停止录像
				this.removeMessages(6);
				MyLog.v("Front.UpdateRecordTimeHandler.stopRecorder() 6");
				stopFrontRecorder5Times();
				stopBackRecorder5Times();
				this.removeMessages(6);
				break;

			case 7: // 格式化存储卡，停止录像
				this.removeMessages(7);
				MyLog.v("Front.UpdateRecordTimeHandler.stopRecorder() 7");
				stopFrontRecorder5Times();
				stopBackRecorder5Times();
				String strVideoCardFormat = getResources().getString(
						R.string.hint_sd2_format);
				HintUtil.showToast(MainActivity.this, strVideoCardFormat);
				speakVoice(strVideoCardFormat);
				this.removeMessages(7);
				break;

			case 8: // 程序异常，停止录像
				this.removeMessages(8);
				MyLog.v("Front.UpdateRecordTimeHandler.stopRecorder() 8");
				stopFrontRecorder5Times();
				stopBackRecorder5Times();
				this.removeMessages(8);
				break;

			case 9: // 系统关机，停止录像
				this.removeMessages(9);
				MyLog.v("Front.UpdateRecordTimeHandler.stopRecorder() 9");
				// String strGoingShutdown = getResources().getString(
				// R.string.hint_going_shutdown);
				// HintUtil.showToast(MainActivity.this, strGoingShutdown);
				// speakVoice(strGoingShutdown);

				stopFrontRecorder5Times();
				stopBackRecorder5Times();
				this.removeMessages(9);
				break;

			default:
				break;
			}
		}
	};

	class CloseRecordThread implements Runnable {

		@Override
		public void run() {
			sendHomeKey();
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if (!MyApp.isAccOn) {
				killAutoRecord();
				sendBroadcast(new Intent(Constant.Broadcast.KILL_APP).putExtra(
						"name", "com.tchip.autorecord"));
			}
		}

	}

	/** 更改录音/静音状态后重启录像 */
	public class StartRecordWhenChangeMuteThread implements Runnable {

		@Override
		public void run() {
			if (isFrontRecord()) {
				if (stopFrontRecorder() == 0) { // 停止录像
					mMainHandler.post(new Runnable() {
						@Override
						public void run() {
							setRecordState(false);
						}
					});
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					if (!MyApp.isFrontRecording && recorderFront != null) {
						MyLog.d("StartRecordWhenChangeMuteThread.Record Start");
						setFrontDirectory(); // 设置保存路径
						// 开始录像前设置静音，过程中无法设置
						if (muteState == Constant.Record.STATE_UNMUTE) {
							if (recorderFront != null) {
								recorderFront.setMute(false);
							}
						} else if (muteState == Constant.Record.STATE_MUTE) {
							if (recorderFront != null) {
								recorderFront.setMute(true);
							}
						}
						if (MyApp.isFrontPreview && recorderFront.start() == 0) {
							mMainHandler.post(new Runnable() {

								@Override
								public void run() {
									resetRecordTimeText();
									HintUtil.playAudio(
											getApplicationContext(),
											com.tchip.tachograph.TachographCallback.FILE_TYPE_VIDEO,
											MyApp.isAccOn);
									setRecordState(true);
								}
							});
						}
					}
				}
			}
		}

	}

	private void initialRecordSurface() {
		MyLog.v("initialRecordSurface");
		surfaceViewFront = (SurfaceView) findViewById(R.id.surfaceViewFront);
		surfaceViewFront.getHolder().addCallback(new FrontCallBack());
		surfaceViewFront.setOnClickListener(myOnClickListener);
		surfaceViewFront.setOnLongClickListener(new View.OnLongClickListener() {

			@Override
			public boolean onLongClick(View v) {
				MyLog.i("ADAS", "reCalibration");
				if (adasInterface != null) {
					adasInterface.reCalibration(); // 强制校准
				}
				return true;
			}
		});

		surfaceViewBack = (SurfaceView) findViewById(R.id.surfaceViewBack);
		surfaceViewBack.setOnClickListener(myOnClickListener);
		surfaceViewBack.getHolder().addCallback(new BackCallBack());
	}

	/** 设置保存路径 */
	public int setFrontDirectory() {
		if (recorderFront != null) {
			return recorderFront.setDirectory(Constant.Path.SDCARD_1);
		}
		return -1;
	}

	/** 设置保存路径 */
	public int setBackDirectory() {
		if (recorderBack != null) {
			return recorderBack.setDirectory(Constant.Path.SDCARD_1);
		}
		return -1;
	}

	/** 设置录像静音，需要已经初始化recorderFront */
	private int setFrontMute(boolean mute, boolean speakVoice) {
		int result = -1;
		if (recorderFront != null) {
			if (speakVoice) {
				speakVoice(getResources().getString(
						mute ? R.string.hint_video_mute_on
								: R.string.hint_video_mute_off));
			}
			editor.putBoolean("videoMute", mute);
			editor.commit();
			muteState = mute ? Constant.Record.STATE_MUTE
					: Constant.Record.STATE_UNMUTE;
			result = recorderFront.setMute(mute);
		}
		MyLog.v("setFrontMute:" + result);
		return result;
	}

	/** 设置录像静音，需要已经初始化recorderBack */
	private int setBackMute(boolean mute, boolean isFromUser) {
		if (recorderBack != null) {
			return recorderBack.setMute(true);
		}
		return -1;
	}

	/** 重置预览区域 */
	private void recreateFrontCameraZone() {
		if (cameraFront == null) {
			// surfaceHolder = holder;
			releaseFrontRecorder();
			closeFrontCamera();
			if (openFrontCamera()) {
				setupFrontRecorder();
			}
		} else {
			try {
				cameraFront.lock();
				cameraFront.setPreviewDisplay(surfaceHolderFront);
				cameraFront.startPreview();
				cameraFront.unlock();
			} catch (Exception e) {
				// e.printStackTrace();
			}
		}
	}

	/** 重置预览区域 */
	private void recreateBackCameraZone() {
		if (cameraBack == null) {
			// surfaceHolder = holder;
			releaseBackRecorder();
			closeBackCamera();
			if (openBackCamera()) {
				setupBackRecorder();
			}
		} else {
			try {
				cameraBack.lock();
				cameraBack.setPreviewDisplay(surfaceHolderBack);
				cameraBack.startPreview();
				cameraBack.unlock();
			} catch (Exception e) {
				// e.printStackTrace();
			}
		}
	}

	/** 关闭录像程序 */
	private void killAutoRecord() {
		if (0 == SettingUtil.getAccStatus()) {
			releaseFrontCameraZone();
			releaseBackCameraZone();
			finish();
			android.os.Process.killProcess(android.os.Process.myPid());
			System.exit(1);
		}
	}

	/** 关闭录像程序 */
	private void killAutoRecordForTest() {
		// Reset Record State
		ProviderUtil.setValue(context, Name.REC_FRONT_STATE, "0");
		ProviderUtil.setValue(context, Name.REC_BACK_STATE, "0");
		releaseFrontCameraZone();
		releaseBackCameraZone();
		finish();
		android.os.Process.killProcess(android.os.Process.myPid());
		System.exit(1);
	}

	/** 释放Camera */
	private void releaseFrontCameraZone() {
		if (!MyApp.isAccOn) {
			// 释放录像区域
			releaseFrontRecorder();
			closeFrontCamera();
			// surfaceHolder = null;
			if (cameraFront != null) {
				cameraFront.stopPreview();
			}
			MyLog.v("Front.releaseCameraZone");
			MyApp.isFrontPreview = false;
		}
	}

	/**
	 * 释放Camera
	 */
	private void releaseBackCameraZone() {
		if (!MyApp.isAccOn) {
			// 释放录像区域
			releaseBackRecorder();
			closeBackCamera();
			// surfaceHolder = null;
			if (cameraBack != null) {
				cameraBack.stopPreview();
			}
			MyLog.v("Back.releaseCameraZone");
			MyApp.isBackPreview = false;
		}
	}

	/** 设置录制初始值 */
	private void setupFrontDefaults() {
		refreshFrontButton();

		MyApp.isFrontRecording = false;

		// 录音,静音;默认录音
		boolean videoMute = sharedPreferences.getBoolean("videoMute",
				Constant.Record.muteDefault);
		muteState = videoMute ? Constant.Record.STATE_MUTE
				: Constant.Record.STATE_UNMUTE;
	}

	/** 设置录制初始值 */
	private void setupBackDefaults() {
		MyApp.isBackRecording = false;
	}

	private void refreshFrontButton() {
		String videoSizeStr = sharedPreferences.getString("videoSize", "1080");
		MyApp.resolutionState = Integer.parseInt(videoSizeStr);

		String videoTimeStr = sharedPreferences.getString("videoTime", "3"); // 视频分段

		if ("5".equals(videoTimeStr)) { // 5
			intervalState = 5;
		} else if ("1".equals(videoTimeStr)) { // 1
			intervalState = 1;
		} else { // 3
			intervalState = 3;

		}
	}

	/** 设置分辨率 */
	public void setFrontResolution(int state) {
		if (state != MyApp.resolutionState) {
			MyApp.resolutionState = state;
			new Thread(new ChangeFrontSizeThread()).start();
		}
	}

	/** 设置分辨率 */
	public int setBackResolution(int state) {
		if (state != MyApp.resolutionState) {
			MyApp.resolutionState = state;
			// 释放录像区域
			releaseBackRecorder();
			closeBackCamera();
			if (openBackCamera()) {
				setupBackRecorder();
			}
		}
		return -1;
	}

	/** 设置视频分段:前置后置 */
	public int setRecordInterval(int seconds) {
		if (recorderBack != null) {
			recorderBack.setVideoSeconds(seconds);
		}
		return (recorderFront != null) ? recorderFront.setVideoSeconds(seconds)
				: -1;
	}

	/** 设置视频重叠 */
	public int setFrontOverlap(int seconds) {
		return (recorderFront != null) ? recorderFront.setVideoOverlap(seconds)
				: -1;
	}

	/** 设置视频重叠 */
	public int setBackOverlap(int seconds) {
		return (recorderBack != null) ? recorderBack.setVideoOverlap(seconds)
				: -1;
	}

	/** 停止录像 */
	public int stopFrontRecorder() {
		if (recorderFront != null) {
			MyLog.d("Front.StopRecorder");
			// 停车守卫不播放声音
			HintUtil.playAudio(getApplicationContext(),
					com.tchip.tachograph.TachographCallback.FILE_TYPE_VIDEO,
					MyApp.isAccOn);
			return recorderFront.stop();
		}
		return -1;
	}

	/** 停止录像 */
	public int stopBackRecorder() {
		if (recorderBack != null) {
			MyLog.d("Back.StopRecorder");
			return recorderBack.stop();
		}
		return -1;
	}

	private void setupFrontRecorder() {
		MyLog.v("Front.SetupRecorder");
		releaseFrontRecorder();
		// try {
		recorderFront = new TachographRecorder();
		recorderFront.setTachographCallback(new FrontTachographCallback());
		recorderFront.setCamera(cameraFront);
		// 前缀，后缀
		recorderFront.setMediaFilenameFixs(TachographCallback.FILE_TYPE_VIDEO,
				"", "_0");
		recorderFront.setMediaFilenameFixs(
				TachographCallback.FILE_TYPE_SHARE_VIDEO, "", "");
		recorderFront.setMediaFilenameFixs(TachographCallback.FILE_TYPE_IMAGE,
				"", "_0");
		// 路径
		recorderFront.setMediaFileDirectory(TachographCallback.FILE_TYPE_VIDEO,
				"VideoFront/Unlock");
		recorderFront.setMediaFileDirectory(
				TachographCallback.FILE_TYPE_SHARE_VIDEO, "Share");
		recorderFront.setMediaFileDirectory(TachographCallback.FILE_TYPE_IMAGE,
				"Image");

		recorderFront.setClientName(this.getPackageName());
		if (MyApp.resolutionState == 720) { // 分辨率
			recorderFront.setVideoSize(1280, 720);
			recorderFront.setVideoFrameRate(Constant.Record.FRONT_FRAME_720P);
			recorderFront.setVideoBiteRate(Constant.Record.FRONT_BITRATE_720P);
		} else {
			recorderFront.setVideoSize(1920, 1080);
			recorderFront.setVideoFrameRate(Constant.Record.FRONT_FRAME_1080P);
			recorderFront.setVideoBiteRate(Constant.Record.FRONT_BITRATE_1080P);
		}
		if (intervalState == 5) { // 分段
			recorderFront.setVideoSeconds(300);
		} else if (intervalState == 1) {
			recorderFront.setVideoSeconds(60);
		} else {
			recorderFront.setVideoSeconds(180);
		}
		recorderFront.setVideoOverlap(0); // 重叠
		if (null != sharedPreferences) { // 录音
			recorderFront.setMute(sharedPreferences.getBoolean("videoMute",
					Constant.Record.muteDefault));
		} else {
			recorderFront.setMute(true);
		}
		recorderFront.setAudioSampleRate(48000);

		recorderFront.setScaledStreamEnable(true, 640, 480);
		recorderFront.setScaledStreamCallback(scaledStreamCallback);

		recorderFront.prepare();
		// } catch (Exception e) {
		// MyLog.e("setupRecorder: Catch Exception：" + e.toString());
		// }
	}

	private ScaledStreamCallback scaledStreamCallback = new ScaledStreamCallback() {

		@Override
		public void onScaledStream(byte[] data, int width, int height) {
			// MyLog.i("ADAS", "onScaledStream");
			if (MyApp.isAccOn) {
				if (isAdasInitial) {
					double speed = adasSpeed;
					if ("1".equals(ProviderUtil.getValue(context,
							Name.ADAS_INDOOR_DEBUG, "0"))) {
						speed = 80.0;
					}
					adasBitmap.eraseColor(Color.TRANSPARENT);

					if (speed >= MyApp.adasThreshold) {
						if (adasInterface.process_yuv(data, speed, adasOutput,
								ADASInterface.YUV_FORMAT_YV12) == 0) {
							Canvas mCanvas = new Canvas(adasBitmap);
							mCanvas.drawText("授权码错误", 100, 480 - 100, paint);
						} else {
							AdasUtil.sendBroadcastByOutput(context, adasOutput);
						}
						adasInterface.Draw853480(adasBitmap, adasOutput);
						imageAdas.setImageBitmap(adasBitmap);
					}
				} else { // AdasInterface未初始化
					MyLog.i("ADAS", "isAdasInitial == false");
					if (licenseInterface.isLicensed(context)) {
						initialAdasInterface();
					}
				}
			}
		}
	};

	private void authAdas() {
		if (TelephonyUtil.isNetworkConnected(context)) {
			new Thread(new AuthAdasThread()).start();
		}
	}

	class AuthAdasThread implements Runnable {

		@Override
		public void run() {
			if (licenseInterface.isLicensed(context)) {
				MyLog.e("ADAS", "ADAS already licensed.");
			} else {
				int licenseState = licenseInterface.getLicense(context);
				MyLog.i("ADAS", "licenseState:" + licenseState);
				if (4 == licenseState) {
					int restoreState = licenseInterface.restoreLicense(context);
					MyLog.i("ADAS", "restoreState:" + restoreState);
				}
			}
		}

	}

	private void setupBackRecorder() {
		MyLog.v("setup Back Recorder");
		releaseBackRecorder();
		try {
			recorderBack = new TachographRecorder();
			recorderBack.setTachographCallback(new BackTachographCallback());
			recorderBack.setCamera(cameraBack);
			// 前缀，后缀
			recorderBack.setMediaFilenameFixs(
					TachographCallback.FILE_TYPE_VIDEO, "", "_1");
			recorderBack.setMediaFilenameFixs(
					TachographCallback.FILE_TYPE_SHARE_VIDEO, "", "");
			recorderBack.setMediaFilenameFixs(
					TachographCallback.FILE_TYPE_IMAGE, "", "_1");
			// 路径
			recorderBack.setMediaFileDirectory(
					TachographCallback.FILE_TYPE_VIDEO, "VideoBack/Unlock");
			recorderBack.setMediaFileDirectory(
					TachographCallback.FILE_TYPE_SHARE_VIDEO, "Share");
			recorderBack.setMediaFileDirectory(
					TachographCallback.FILE_TYPE_IMAGE, "Image");
			recorderBack.setClientName(this.getPackageName());
			recorderBack.setVideoSize(640, 480); // (640, 480)(1280,720)
			recorderBack.setVideoFrameRate(Constant.Record.BACK_FRAME);
			recorderBack.setVideoBiteRate(Constant.Record.BACK_BITRATE);
			if (intervalState == 5) {
				recorderBack.setVideoSeconds(300);
			} else if (intervalState == 1) {
				recorderBack.setVideoSeconds(60);
			} else {
				recorderBack.setVideoSeconds(180);
			}
			recorderBack.setVideoOverlap(0);
			recorderBack.prepare();
		} catch (Exception e) {
			MyLog.e("setupRecorder: Catch Exception!");
		}

	}

	/** 释放Recorder */
	private void releaseFrontRecorder() {
		MyLog.v("release Front Recorder");
		try {
			if (recorderFront != null) {
				recorderFront.stop();
				ProviderUtil.setValue(context, Name.REC_FRONT_STATE, "0");
				recorderFront.close();
				recorderFront.release();
				recorderFront = null;
				MyLog.d("Record Release");
			}
		} catch (Exception e) {
			MyLog.e("releaseRecorder: Catch Exception!");
		}
	}

	/** 释放Recorder */
	private void releaseBackRecorder() {
		MyLog.v("release Back Recorder");
		try {
			if (recorderBack != null) {
				recorderBack.stop();
				ProviderUtil.setValue(context, Name.REC_BACK_STATE, "0");
				recorderBack.close();
				recorderBack.release();
				recorderBack = null;
				MyLog.d("Record Release");
			}
		} catch (Exception e) {
			MyLog.e("releaseRecorder: Catch Exception!");
		}
	}

	class FrontTachographCallback implements TachographCallback {

		@Override
		public void onError(int error) {
			switch (error) {
			case TachographCallback.ERROR_SAVE_VIDEO_FAIL:
				String strSaveVideoErr = getResources().getString(
						R.string.hint_save_video_error);
				HintUtil.showToast(MainActivity.this, strSaveVideoErr);
				MyLog.e("Front Record Error : ERROR_SAVE_VIDEO_FAIL");
				// 视频保存失败，原因：存储空间不足，清空文件夹，视频被删掉
				// resetRecordTimeText();
				// MyLog.v("[onError]stopRecorder()");
				// if (stopRecorder() == 0) {
				// setRecordState(false);
				// }
				speakVoice(strSaveVideoErr);

				StartCheckErrorFileThread();
				break;

			case TachographCallback.ERROR_SAVE_IMAGE_FAIL:
				HintUtil.showToast(MainActivity.this,
						getResources()
								.getString(R.string.hint_save_photo_error));
				MyLog.e("Front Record Error : ERROR_SAVE_IMAGE_FAIL");

				StartCheckErrorFileThread();
				break;

			case TachographCallback.ERROR_RECORDER_CLOSED:
				MyLog.e("Front Record Error : ERROR_RECORDER_CLOSED");
				break;

			default:
				break;
			}
		}

		/**
		 * 文件保存回调，注意：存在延时，不能用作重置录像跑秒时间
		 * 
		 * @param type
		 *            0-图片 1-视频
		 * 
		 * @param path
		 *            视频：/storage/sdcard1/DrivingRecord/VideoFront/Unlock/2016-
		 *            05- 04_155010_0.mp4
		 *            图片:/storage/sdcard1/DrivingRecord/Image/2015-
		 *            07-01_105536.jpg
		 */
		@Override
		public void onFileSave(int type, String path) {
			try {
				if (type == 1) { // 视频
					new Thread(new ReleaseFrontStorageThread()).start();
					String videoName = path.split("/")[6];

					if (MyApp.isFrontLock) {
						MyApp.isFrontLock = false; // 还原
						StorageUtil.lockVideo(true, videoName);

						if (MyApp.isFrontRecording && MyApp.isFrontLockSecond) {
							MyApp.isFrontLock = true;
							MyApp.isFrontLockSecond = false; // 不录像时修正加锁图标
						}
					}

					setupRecordViews(); // 更新录制按钮状态

					StartCheckErrorFileThread(); // 执行onFileSave时，此file已经不隐藏，下个正在录的为隐藏
				} else { // 图片
					HintUtil.showToast(MainActivity.this, getResources()
							.getString(R.string.hint_photo_save));

					new Thread(new WriteImageExifThread(path)).start();

					// 通知语音
					Intent intentImageSave = new Intent(
							Constant.Broadcast.ACTION_IMAGE_SAVE);
					intentImageSave.putExtra("path", path);
					sendBroadcast(intentImageSave);
				}
				// 更新Media Database
				sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
						Uri.parse("file://" + path)));
				MyLog.d("Front.onFileSave.Type=" + type + ",Save path:" + path);
			} catch (Exception e) {
				e.printStackTrace();
				MyLog.e("Front.onFileSave.catch Exception:" + e.toString());
			}
		}

		@Override
		public void onFileStart(int type, String path) {
			MyLog.v("Front.onFileStart.Path:" + path);
		}

	}

	class BackTachographCallback implements TachographCallback {

		@Override
		public void onError(int error) {
			switch (error) {
			case TachographCallback.ERROR_SAVE_VIDEO_FAIL:
				String strSaveVideoErr = getResources().getString(
						R.string.hint_save_video_error);
				HintUtil.showToast(MainActivity.this, strSaveVideoErr);
				MyLog.e("Back Record Error : ERROR_SAVE_VIDEO_FAIL");
				// 视频保存失败，原因：存储空间不足，清空文件夹，视频被删掉
				// resetRecordTimeText();
				// MyLog.v("[onError]stopRecorder()");
				// if (stopRecorder() == 0) {
				// setRecordState(false);
				// }
				speakVoice(strSaveVideoErr);

				StartCheckErrorFileThread();
				break;

			case TachographCallback.ERROR_SAVE_IMAGE_FAIL:
				HintUtil.showToast(MainActivity.this,
						getResources()
								.getString(R.string.hint_save_photo_error));
				MyLog.e("Back Record Error : ERROR_SAVE_IMAGE_FAIL");

				StartCheckErrorFileThread();
				break;

			case TachographCallback.ERROR_RECORDER_CLOSED:
				MyLog.e("Back Record Error : ERROR_RECORDER_CLOSED");
				break;

			default:
				break;
			}
		}

		/**
		 * 文件保存回调，注意：存在延时，不能用作重置录像跑秒时间
		 * 
		 * @param type
		 *            0-图片 1-视频
		 * 
		 * @param path
		 *            视频：/storage/sdcard1/DrivingRecord/VideoBack/2016-05-
		 *            04_155010_1.mp4
		 */
		@Override
		public void onFileSave(int type, String path) {
			try {
				if (type == 1) { // 视频
					new Thread(new ReleaseBackStorageThread()).start();

					String videoName = path.split("/")[6];

					if (MyApp.isBackLock) {
						MyApp.isBackLock = false; // 还原
						StorageUtil.lockVideo(false, videoName);

						if (MyApp.isBackRecording && MyApp.isBackLockSecond) {
							MyApp.isBackRecording = true;
							MyApp.isBackLockSecond = false; // 不录像时修正加锁图标
						}
					}
					setupRecordViews(); // 更新录制按钮状态

					StartCheckErrorFileThread(); // 执行onFileSave时，此file已经不隐藏，下个正在录的为隐藏
				} else { // 图片
				}

				sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
						Uri.parse("file://" + path))); // 更新Media
														// Database
				MyLog.d("Back.onFileSave.Type=" + type + ",Save path:" + path);
			} catch (Exception e) {
				e.printStackTrace();
				MyLog.e("Back.onFileSave.catch Exception:" + e.toString());
			}
		}

		@Override
		public void onFileStart(int type, String path) {
			MyLog.v("Back.onFileStart.Path:" + path);
		}

	}

	private void hintCardEject() {
		if (!ClickUtil.isHintSdEjectTooQuick(2000)) {
			String strVideoCardEject = getResources().getString(
					R.string.hint_sd_remove_badly);
			HintUtil.showToast(MainActivity.this, strVideoCardEject);
			// speakVoice(strVideoCardEject);
			MyLog.e("showCardEjectMessage");
		}
	}

	public boolean isFrontRecord() {
		if (recorderFront != null) {
			int intFrontRecording = recorderFront.isRecording();
			MyLog.d("Tachograph.isFrontRecord:" + intFrontRecording);
			return 1 == intFrontRecording;
		} else
			return false;
	}

	public boolean isBackRecord() {
		if (recorderBack != null) {
			int intBackRecording = recorderBack.isRecording();
			MyLog.d("Tachograph.isBackRecord:" + intBackRecording);
			return 1 == intBackRecording;
		} else
			return false;
	}

}
