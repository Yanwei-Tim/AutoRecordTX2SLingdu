package com.tchip.autorecord.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.tchip.autorecord.Constant;
import com.tchip.autorecord.MyApp;
import com.tchip.autorecord.R;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.os.Looper;
import android.os.StatFs;
import android.provider.MediaStore;
import android.widget.Toast;

public class StorageUtil {

	/**
	 * 获得SD卡总大小
	 * 
	 * @return 总大小，单位：字节B
	 */
	public static long getSDTotalSize(String SDCardPath) {
		StatFs stat = new StatFs(SDCardPath);
		long blockSize = stat.getBlockSize();
		long totalBlocks = stat.getBlockCount();
		return blockSize * totalBlocks;
	}

	/**
	 * 获得sd卡剩余容量，即可用大小
	 * 
	 * @return 剩余空间，单位：字节B
	 */
	public static long getSDAvailableSize(String SDCardPath) {
		// StatFs stat = new StatFs("/storage/sdcard1");
		StatFs stat = new StatFs(SDCardPath);
		long blockSize = stat.getBlockSize();
		long availableBlocks = stat.getAvailableBlocks();
		return blockSize * availableBlocks;
	}

	/** 录像SD卡是否存在 */
	public static boolean isFrontCardExist() {
		boolean isVideoCardExist = false;
		try {
			String pathVideo = Constant.Path.VIDEO_FRONT_TOTAL;
			File fileVideo = new File(pathVideo);
			boolean isSuccess = fileVideo.mkdirs();
			MyLog.v("StorageUtil.isVideoCardExists,mkdirs isSuccess:"
					+ isSuccess);
			File file = new File(pathVideo);
			if (!file.exists()) {
				isVideoCardExist = false;
			} else {
				isVideoCardExist = true;
			}
		} catch (Exception e) {
			MyLog.e("StorageUtil.isVideoCardExists:Catch Exception!");
			isVideoCardExist = false;
		}
		MyLog.v("StorageUtil.isVideoCardExists:" + isVideoCardExist);
		return isVideoCardExist;
	}

	/** 录像SD卡是否存在 */
	public static boolean isBackCardExist() {
		boolean isVideoCardExist = false;
		try {
			String pathVideo = Constant.Path.VIDEO_BACK_TOTAL;
			File fileVideo = new File(pathVideo);
			boolean isSuccess = fileVideo.mkdirs();
			MyLog.v("StorageUtil.isVideoCardExists,mkdirs isSuccess:"
					+ isSuccess);
			File file = new File(pathVideo);
			if (!file.exists()) {
				isVideoCardExist = false;
			} else {
				isVideoCardExist = true;
			}
		} catch (Exception e) {
			MyLog.e("StorageUtil.isVideoCardExists:Catch Exception!");
			isVideoCardExist = false;
		}
		MyLog.v("StorageUtil.isVideoCardExists:" + isVideoCardExist);
		return isVideoCardExist;
	}

	/** 创建前后录像存储卡目录 */
	public static void createRecordDirectory() {
		try {
			new File(Constant.Path.VIDEO_FRONT_TOTAL).mkdirs();
			new File(Constant.Path.VIDEO_FRONT_UNLOCK).mkdirs();
			new File(Constant.Path.VIDEO_FRONT_LOCK).mkdirs();

			new File(Constant.Path.VIDEO_BACK_TOTAL).mkdirs();
			new File(Constant.Path.VIDEO_BACK_UNLOCK).mkdirs();
			new File(Constant.Path.VIDEO_BACK_LOCK).mkdirs();

			new File(Constant.Path.IMAGE_BOTH).mkdirs();
			new File(Constant.Path.VIDEO_THUMBNAIL).mkdirs();
		} catch (Exception e) {
		}
	}

	/**
	 * 递归删除文件和文件夹
	 * 
	 * @param file
	 *            要删除的根目录
	 */
	public static void RecursionDeleteFile(File file) {
		try {
			if (file.isFile()) {
				if (!file.getName().startsWith(".")) { // 不删除正在录制的视频
					file.delete();
				}
				return;
			}
			if (file.isDirectory()) {
				File[] childFile = file.listFiles();
				if (childFile == null || childFile.length == 0) {
					file.delete();
					return;
				}
				for (File f : childFile) {
					RecursionDeleteFile(f);
				}
				file.delete();
			}
		} catch (Exception e) {
			MyLog.e("StorageUtil.RecursionDeleteFile:Catch Exception!");
		}
	}

	/** 将加锁视频移动到加锁文件夹 */
	public static void lockVideo(boolean isFront, String videoName) {
		String rawPath = isFront ? Constant.Path.VIDEO_FRONT_UNLOCK
				: Constant.Path.VIDEO_BACK_UNLOCK;
		String lockPath = isFront ? Constant.Path.VIDEO_FRONT_LOCK
				: Constant.Path.VIDEO_BACK_LOCK;
		File rawFile = new File(rawPath + File.separator + videoName);
		if (rawFile.exists() && rawFile.isFile()) {
			File lockDir = new File(lockPath);
			if (!lockDir.exists()) {
				lockDir.mkdirs();
			}
			File lockFile = new File(lockDir + File.separator + videoName);
			rawFile.renameTo(lockFile);
			MyLog.v("StorageUtil.lockVideo:" + videoName);
		}
	}

	/**
	 * 删除前录文件，同时删除对应时段后录视频
	 * 
	 * @param context
	 * @param frontVideoName
	 */
	private static void deleteBackByFront(Context context, String frontVideoName) {
		try {
			if (null != frontVideoName && frontVideoName.trim().length() > 19
					&& !frontVideoName.startsWith(".")) {
				String videoPrefix = frontVideoName.substring(0, 15);
				MyLog.v("[deleteBackByFront]videoPrefix:" + videoPrefix);
				File dirBack = new File(Constant.Path.VIDEO_BACK_UNLOCK);
				File[] childFiles = dirBack.listFiles();
				for (File childFile : childFiles) {
					if (childFile.getName().startsWith(videoPrefix)
							&& childFile.exists()) {
						childFile.delete();
						deleteThumbnailByVideo(childFile);

						MyLog.w("[deleteBackByFront]Delete Back:"
								+ childFile.getPath());
						for (File childFileOld : childFiles) { // 比删除视频的日期旧的视频也删除
							// 2016-08-22_203957_1.mp4
							// 0123456789A12345678
							String childFileName = childFileOld.getName();
							if (null != childFileName
									&& childFileName.trim().length() > 19
									&& !childFileName.startsWith(".")) {
								int childYear = Integer.parseInt(childFileName
										.substring(0, 4));
								int thisYear = Integer.parseInt(frontVideoName
										.substring(0, 4));
								if (childYear < thisYear) {
									if (childFileOld.exists()) {
										MyLog.w("[deleteBackByFront]Delete Back OLD:"
												+ childFileOld.getPath());
										childFileOld.delete();
										deleteThumbnailByVideo(childFileOld);
									}
								} else if (childYear == thisYear) {
									int childMonth = Integer
											.parseInt(childFileName.substring(
													5, 7));
									int thisMonth = Integer
											.parseInt(frontVideoName.substring(
													5, 7));
									if (childMonth < thisMonth) {
										if (childFileOld.exists()) {
											MyLog.w("[deleteBackByFront]Delete Back OLD:"
													+ childFileOld.getPath());
											childFileOld.delete();
											deleteThumbnailByVideo(childFileOld);
										}
									} else if (childMonth == thisMonth) {
										int childDay = Integer
												.parseInt(childFileName
														.substring(8, 10));
										int thisDay = Integer
												.parseInt(frontVideoName
														.substring(8, 10));

										if (childDay < thisDay) {
											if (childFileOld.exists()) {
												MyLog.w("[deleteBackByFront]Delete Back OLD:"
														+ childFileOld
																.getPath());
												childFileOld.delete();
												deleteThumbnailByVideo(childFileOld);
											}
										} else if (childDay == thisDay) {
											int childHour = Integer
													.parseInt(childFileName
															.substring(11, 13));
											int thisHour = Integer
													.parseInt(frontVideoName
															.substring(11, 13));
											if (childHour < thisHour) {
												if (childFileOld.exists()) {
													MyLog.w("[deleteBackByFront]Delete Back OLD:"
															+ childFileOld
																	.getPath());
													childFileOld.delete();
													deleteThumbnailByVideo(childFileOld);
												}
											} else if (childHour == thisHour) {
												int childMinute = Integer
														.parseInt(childFileName
																.substring(13,
																		15));
												int thisMinute = Integer
														.parseInt(frontVideoName
																.substring(13,
																		15));
												if (childMinute < thisMinute) {
													if (childFileOld.exists()) {
														MyLog.w("[deleteBackByFront]Delete Back OLD:"
																+ childFileOld
																		.getPath());
														childFileOld.delete();
														deleteThumbnailByVideo(childFileOld);
													}
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		} catch (Exception e) {
			MyLog.e("deleteBackByFront catch:" + e.toString());
		}
	}

	/**
	 * 删除后录文件，同时删除对应时段前录视频
	 * 
	 * @param context
	 * @param backVideoName
	 */
	private static void deleteFrontByBack(Context context, String backVideoName) {
		try {
			if (null != backVideoName && backVideoName.trim().length() > 19
					&& !backVideoName.startsWith(".")) {
				String videoPrefix = backVideoName.substring(0, 15);
				MyLog.v("[deleteFrontByBack]videoPrefix:" + videoPrefix);
				File dirBack = new File(Constant.Path.VIDEO_FRONT_UNLOCK);
				File[] childFiles = dirBack.listFiles();
				for (File childFile : childFiles) {
					if (childFile.getName().startsWith(videoPrefix)
							&& childFile.exists()) {
						childFile.delete();
						deleteThumbnailByVideo(childFile);

						MyLog.w("[deleteFrontByBack]Delete Front:"
								+ childFile.getPath());
						for (File childFileOld : childFiles) { // 比删除视频的日期旧的视频也删除
							// 2016-08-22_203957_0.mp4
							// 0123456789A12345678
							String childFileName = childFileOld.getName();
							if (null != childFileName
									&& childFileName.trim().length() > 19
									&& !childFileName.startsWith(".")) {
								int childYear = Integer.parseInt(childFileName
										.substring(0, 4));
								int thisYear = Integer.parseInt(backVideoName
										.substring(0, 4));
								if (childYear < thisYear) {
									if (childFileOld.exists()) {
										MyLog.w("[deleteFrontByBack]Delete Front OLD:"
												+ childFileOld.getPath());
										childFileOld.delete();
										deleteThumbnailByVideo(childFileOld);
									}
								} else if (childYear == thisYear) {
									int childMonth = Integer
											.parseInt(childFileName.substring(
													5, 7));
									int thisMonth = Integer
											.parseInt(backVideoName.substring(
													5, 7));
									if (childMonth < thisMonth) {
										if (childFileOld.exists()) {
											MyLog.w("[deleteFrontByBack]Delete Front OLD:"
													+ childFileOld.getPath());
											childFileOld.delete();
											deleteThumbnailByVideo(childFileOld);
										}
									} else if (childMonth == thisMonth) {
										int childDay = Integer
												.parseInt(childFileName
														.substring(8, 10));
										int thisDay = Integer
												.parseInt(backVideoName
														.substring(8, 10));

										if (childDay < thisDay) {
											if (childFileOld.exists()) {
												MyLog.w("[deleteFrontByBack]Delete Front OLD:"
														+ childFileOld
																.getPath());
												childFileOld.delete();
												deleteThumbnailByVideo(childFileOld);
											}
										} else if (childDay == thisDay) {
											int childHour = Integer
													.parseInt(childFileName
															.substring(11, 13));
											int thisHour = Integer
													.parseInt(backVideoName
															.substring(11, 13));
											if (childHour < thisHour) {
												if (childFileOld.exists()) {
													MyLog.w("[deleteFrontByBack]Delete Front OLD:"
															+ childFileOld
																	.getPath());
													childFileOld.delete();
													deleteThumbnailByVideo(childFileOld);
												}
											} else if (childHour == thisHour) {
												int childMinute = Integer
														.parseInt(childFileName
																.substring(13,
																		15));
												int thisMinute = Integer
														.parseInt(backVideoName
																.substring(13,
																		15));
												if (childMinute < thisMinute) {
													if (childFileOld.exists()) {
														MyLog.w("[deleteFrontByBack]Delete Front OLD:"
																+ childFileOld
																		.getPath());
														childFileOld.delete();
														deleteThumbnailByVideo(childFileOld);
													}
												}
											}
										}
									}
								}
							}
						}
					}
				}
			}
		} catch (Exception e) {
			MyLog.e("deleteFrontByBack catch:" + e.toString());
		}
	}

	/**
	 * 未录像时删除点视频
	 * 
	 * @param context
	 * @param file
	 */
	public static void RecursionCheckDotFile(Context context, File file) {
		if (file.exists()) {
			try {
				String fileName = file.getName();
				if (file.isFile()) {
					if (fileName.endsWith(".mp4")) {
						if (fileName.startsWith(".")) {
							// Delete file start with dot but not the recording
							// one
							if (!MyApp.isFrontRecording
									&& fileName.endsWith("_0.mp4")) {
								file.delete();
								MyLog.v("StorageUtil.RecursionCheckFile-Delete DOT File:"
										+ fileName);
							}
							if (!MyApp.isBackRecording
									&& fileName.endsWith("_1.mp4")) {
								file.delete();
								MyLog.v("StorageUtil.RecursionCheckFile-Delete DOT File:"
										+ fileName);
							}
						}
						return;
					} else if (!fileName.endsWith(".jpg")
							&& !fileName.endsWith(".tmp")) {
						file.delete();
					}
				} else if (file.isDirectory()) {
					File[] childFile = file.listFiles();
					if (childFile == null || childFile.length == 0) {
						return;
					}
					for (File f : childFile) {
						RecursionCheckDotFile(context, f);
					}
				}
			} catch (Exception e) {
				MyLog.e("StorageUtil.RecursionCheckFile-Catch Exception:"
						+ e.toString());
			}

		}
	}

	/**
	 * 删除对应THUMBNAIL
	 * 
	 * @param fileVideo
	 */
	private static boolean deleteThumbnailByVideo(File fileVideo) {
		String thumbnailPath = Constant.Path.VIDEO_THUMBNAIL
				+ fileVideo.getName().replace(".mp4", ".jpg");
		File fileThumbnail = new File(thumbnailPath);
		if (fileThumbnail.exists() && fileThumbnail.isFile()) {
			MyLog.i("deleteThumbnailByVideo:" + fileThumbnail.getName());
			return fileThumbnail.delete();
		} else
			return false;
	}

	/**
	 * 为没有缩略图的录像创建
	 * 
	 * @param context
	 * @param file
	 */
	public static void CreateThumbnailForVideo(Context context, File file) {
		if (file.exists()) {
			try {
				String fileName = file.getName();
				if (file.isFile()) {
					if (!fileName.startsWith(".") && fileName.endsWith(".mp4")) {
						String thumbnailPath = Constant.Path.VIDEO_THUMBNAIL
								+ fileName.replace(".mp4", ".jpg");
						if (!new File(thumbnailPath).exists()) {
							MyLog.i("CreateThumbnailForVideo:" + thumbnailPath);
							Bitmap bitmapThumbnail = getVideoThumbnail(
									file.getPath(), 160, 90,
									MediaStore.Video.Thumbnails.MINI_KIND);
							new File(Constant.Path.VIDEO_THUMBNAIL).mkdirs();
							saveOneBitmap(bitmapThumbnail, thumbnailPath);
						}
						return;
					}
				} else if (file.isDirectory()) {
					File[] childFile = file.listFiles();
					if (childFile == null || childFile.length == 0) {
						return;
					}
					for (File f : childFile) {
						CreateThumbnailForVideo(context, f);
					}
				}
			} catch (Exception e) {
				MyLog.e("StorageUtil.RecursionCheckFile-Catch Exception:"
						+ e.toString());
			}

		}
	}

	/**
	 * 获取视频的缩略图
	 * 
	 * @param videoPath
	 *            视频路径
	 * @param width
	 * @param height
	 * @param kind
	 *            eg:MediaStore.Video.Thumbnails.MICRO_KIND MINI_KIND: 512 x
	 *            384，MICRO_KIND: 96 x 96
	 * @return
	 */
	private static Bitmap getVideoThumbnail(String videoPath, int width,
			int height, int kind) {
		Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(videoPath, kind);
		bitmap = ThumbnailUtils.extractThumbnail(bitmap, width, height,
				ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
		return bitmap;
	}

	/** 保存一张bitmap */
	public static void saveOneBitmap(Bitmap bitmap, String path) {
		File file = new File(path);
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

	private static void speakVoice(Context context, String content) {
		context.sendBroadcast(new Intent(Constant.Broadcast.TTS_SPEAK)
				.putExtra("content", content));
	}

	// Below is new function to release storage

	/**
	 * 删除最旧视频，调用此函数的地方：
	 * 
	 * 1.开启录像 {@link MainActivity#startRecordTask}
	 * 
	 * 2.文件保存回调{@link MainActivity#onFileSave}
	 */
	public static boolean releaseFrontStorage(Context context) {
		if (!StorageUtil.isFrontCardExist()) {
			MyLog.e("Storageutil.releaseFrontStorage:No Video Card");
			return false;
		}
		try {
			while (FileUtil.isFrontStorageLess()) {
				List<File> listFrontUnLock = listFileSortByModifyTime(Constant.Path.VIDEO_FRONT_UNLOCK);
				if (listFrontUnLock.size() > 0) { // 未加锁视频
					File fileOldest = listFrontUnLock.get(0);
					String fileName = fileOldest.getName();
					if (fileOldest.exists() && fileOldest.isFile()
							&& fileName.endsWith(".mp4")
							&& !fileName.startsWith(".")) {
						boolean isSuccess = fileOldest.delete();
						deleteThumbnailByVideo(fileOldest);

						MyLog.i("releaseFrontStorage.DEL Unlock:" + fileName
								+ " " + isSuccess);

						deleteBackByFront(context, fileName); // 删除同时段后录视频
					}
				} else { // 加锁视频
					List<File> listFrontLock = listFileSortByModifyTime(Constant.Path.VIDEO_FRONT_LOCK);
					if (listFrontLock.size() > 0) {
						File fileOldest = listFrontLock.get(0);
						String fileName = fileOldest.getName();
						if (fileOldest.exists() && fileOldest.isFile()
								&& fileName.endsWith(".mp4")
								&& !fileName.startsWith(".")) {
							boolean isSuccess = fileOldest.delete();
							deleteThumbnailByVideo(fileOldest);
							MyLog.i("releaseFrontStorage.DEL Lock:" + fileName
									+ " " + isSuccess);

							MyApp.needDeleteLockHint = true;
						}
					} else {
						if (FileUtil.isFrontStorageLess()) { // 此时若空间依然不足,提示用户清理存储（已不是行车视频的原因）
							MyLog.e("StorageUtil:Storage is full...");
							// TODO:显示格式化对话框
							speakVoice(context, context.getResources()
									.getString(R.string.sd_storage_too_low));
							return false;
						}
					}
				}
			}
			while (FileUtil.isFrontLockLess()) { // 前置加锁
				List<File> listFrontLock = listFileSortByModifyTime(Constant.Path.VIDEO_FRONT_LOCK);
				if (listFrontLock.size() > 0) {
					File fileOldest = listFrontLock.get(0);
					String fileName = fileOldest.getName();
					if (fileOldest.exists() && fileOldest.isFile()
							&& fileName.endsWith(".mp4")
							&& !fileName.startsWith(".")) {
						boolean isSuccess = fileOldest.delete();
						deleteThumbnailByVideo(fileOldest);
						MyLog.i("releaseFrontStorage.DEL Lock:" + fileName
								+ " " + isSuccess);
					}
				}
			}
			return true;
		} catch (Exception e) {
			/* 异常原因：1.文件由用户手动删除 */
			MyLog.e("StorageUtil.deleteOldestUnlockVideo:Catch Exception:"
					+ e.toString());
			e.printStackTrace();
			return true;
		}
	}

	/**
	 * 删除最旧视频，调用此函数的地方：
	 * 
	 * 1.开启录像 {@link MainActivity#startRecordTask}
	 * 
	 * 2.文件保存回调{@link MainActivity#onFileSave}
	 * 
	 */
	public static boolean releaseBackStorage(Context context) {
		if (!StorageUtil.isBackCardExist()) {
			MyLog.e("Storageutil.deleteOldestUnlockVideo:No Video Card");
			return false;
		}
		try {
			while (FileUtil.isBackStorageLess()) {
				List<File> listBackUnLock = listFileSortByModifyTime(Constant.Path.VIDEO_BACK_UNLOCK);
				if (listBackUnLock.size() > 0) { // 未加锁视频
					File fileOldest = listBackUnLock.get(0);
					String fileName = fileOldest.getName();
					if (fileOldest.exists() && fileOldest.isFile()
							&& fileName.endsWith(".mp4")
							&& !fileName.startsWith(".")) {
						boolean isSuccess = fileOldest.delete();
						deleteThumbnailByVideo(fileOldest);
						MyLog.i("releaseBackStorage.DEL Unlock:" + fileName
								+ " " + isSuccess);

						deleteFrontByBack(context, fileName); // 删除同时段后录视频
					}
				} else { // 加锁视频
					List<File> listBackLock = listFileSortByModifyTime(Constant.Path.VIDEO_BACK_LOCK);
					if (listBackLock.size() > 0) {
						File fileOldest = listBackLock.get(0);
						String fileName = fileOldest.getName();
						if (fileOldest.exists() && fileOldest.isFile()
								&& fileName.endsWith(".mp4")
								&& !fileName.startsWith(".")) {
							boolean isSuccess = fileOldest.delete();
							deleteThumbnailByVideo(fileOldest);
							MyLog.i("releaseBackStorage.DEL Lock:" + fileName
									+ " " + isSuccess);

							MyApp.needDeleteLockHint = true;
						}
					} else {
						if (FileUtil.isBackStorageLess()) { // 此时若空间依然不足,提示用户清理存储（已不是行车视频的原因）
							MyLog.e("StorageUtil:Storage is full...");
							// TODO:显示格式化对话框
							speakVoice(context, context.getResources()
									.getString(R.string.sd_storage_too_low));
							return false;
						}
					}
				}
			}
			while (FileUtil.isBackLockLess()) { // 后置加锁
				List<File> listBackLock = listFileSortByModifyTime(Constant.Path.VIDEO_BACK_LOCK);
				if (listBackLock.size() > 0) {
					File fileOldest = listBackLock.get(0);
					String fileName = fileOldest.getName();
					if (fileOldest.exists() && fileOldest.isFile()
							&& fileName.endsWith(".mp4")
							&& !fileName.startsWith(".")) {
						boolean isSuccess = fileOldest.delete();
						deleteThumbnailByVideo(fileOldest);
						MyLog.i("releaseBackStorage.DEL Lock:" + fileName + " "
								+ isSuccess);
					}
				}
			}
			return true;
		} catch (Exception e) {
			/*
			 * 异常原因：1.文件由用户手动删除
			 */
			MyLog.e("StorageUtil.deleteOldestUnlockVideo:Catch Exception:"
					+ e.toString());
			e.printStackTrace();
			return true;
		}
	}

	/**
	 * 获取目录下所有文件(按时间排序)
	 * 
	 * @param path
	 * @return
	 */
	public static List<File> listFileSortByModifyTime(String path) {
		List<File> list = getFiles(path, new ArrayList<File>());
		if (list != null && list.size() > 0) {
			Collections.sort(list, new Comparator<File>() {
				public int compare(File file, File newFile) {
					if (file.lastModified() < newFile.lastModified()) {
						return -1;
					} else if (file.lastModified() == newFile.lastModified()) {
						return 0;
					} else {
						return 1;
					}
				}
			});
		}
		return list;
	}

	/**
	 * 
	 * 获取目录下所有文件
	 * 
	 * @param realpath
	 * @param files
	 * @return
	 */
	public static List<File> getFiles(String realpath, List<File> files) {
		File realFile = new File(realpath);
		if (realFile.isDirectory()) {
			File[] subfiles = realFile.listFiles();
			for (File file : subfiles) {
				if (file.isDirectory()) {
					getFiles(file.getAbsolutePath(), files);
				} else {
					String fileName = file.getName();
					if (file.exists() && file.isFile()
							&& fileName.endsWith(".mp4")
							&& !fileName.startsWith(".")) {
						files.add(file);
					}
				}
			}
		}
		return files;
	}

}
