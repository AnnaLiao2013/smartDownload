package com.smart.impl;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.smart.db.FileService;

public class SmartFileDownloader {
	private static final String TAG = "SmartDownloader";

	private Context context;
	private String downloadUrl;
	private FileService fileService;
	private int downloadSize = 0;// already downloaded size
	private int fileSize = 0;// original file size
	private SmartDownloadThread[] threads;
	private File saveFile;
	private Map<Integer, Integer> data = new ConcurrentHashMap<Integer, Integer>();// cache
																					// for
																					// each
																					// thread
	private int block;// download length for each thread
	private boolean isPause;// a flag to indicate download status
	private long startDown;

	public int getFileSize() {
		return fileSize;
	}

	public int getThreadSize() {
		return threads.length;
	}

	protected synchronized void append(int size) {// Accumulated download size
		downloadSize += size;
	}

	protected void update(int threadId, int pos) {
		this.data.put(threadId, pos);
	}

	protected synchronized void saveLogFile() {
		this.fileService.update(this.downloadUrl, this.data);
	}

	public SmartFileDownloader(Context context, String downloadUrl,
			File fileSaveDir, int threadNum) {
		try {
			this.context = context;
			this.downloadUrl = downloadUrl;
			fileService = new FileService(this.context);
			URL url = new URL(this.downloadUrl);
			if (!fileSaveDir.exists())
				fileSaveDir.mkdirs();// check exist
			this.threads = new SmartDownloadThread[threadNum];
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(5000);// set connect host timeout
			conn.setRequestMethod("GET");
			conn.setRequestProperty(
					"Accept",
					"image/gif, image/jpeg, image/pjpeg, image/pjpeg, application/x-shockwave-flash, application/xaml+xml, application/vnd.ms-xpsdocument, application/x-ms-xbap, application/x-ms-application, application/vnd.ms-excel, application/vnd.ms-powerpoint, application/msword, */*");
			conn.setRequestProperty("Accept-Language", "en-GB");
			conn.setRequestProperty("Referer", downloadUrl);
			conn.setRequestProperty("Charset", "UTF-8");
			conn.setRequestProperty(
					"User-Agent",
					"Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 5.2; Trident/4.0; .NET CLR 1.1.4322; .NET CLR 2.0.50727; .NET CLR 3.0.04506.30; .NET CLR 3.0.4506.2152; .NET CLR 3.5.30729)");
			conn.setRequestProperty("Connection", "Keep-Alive");
			conn.connect();
			if (conn.getResponseCode() == 200) {
				this.fileSize = conn.getContentLength();// get the size of the
														// file
				if (this.fileSize <= 0)
					throw new RuntimeException("Unkown file size ");

				String filename = getFileName(conn);
				this.saveFile = new File(fileSaveDir, filename);// save
				Map<Integer, Integer> logdata = fileService
						.getData(downloadUrl);
				if (logdata.size() > 0) {
					for (Map.Entry<Integer, Integer> entry : logdata.entrySet())
						data.put(entry.getKey(), entry.getValue());
				}
				this.startDown = System.currentTimeMillis();// start download
															// time
				this.block = (this.fileSize % this.threads.length) == 0 ? this.fileSize
						/ this.threads.length
						: this.fileSize / this.threads.length + 1;// decide the
																	// download
																	// size for
																	// each
																	// thread
				if (this.data.size() == this.threads.length) {
					for (int i = 0; i < this.threads.length; i++) {
						this.downloadSize += this.data.get(i + 1);
					}
				}
			} else {
				throw new RuntimeException("server no response ");
			}
		} catch (Exception e) {
			throw new RuntimeException("Don't connect this url");
		}
	}

	private String getFileName(HttpURLConnection conn) {
		String filename = this.downloadUrl.substring(this.downloadUrl
				.lastIndexOf('/') + 1);// the last'/' of the url is file name
		if (filename == null || "".equals(filename.trim())) {// if cannot get
																// filename
			for (int i = 0;; i++) {
				String mine = conn.getHeaderField(i);
				if (mine == null)
					break;
				if ("content-disposition".equals(conn.getHeaderFieldKey(i)
						.toLowerCase())) {
					Matcher m = Pattern.compile(".*filename=(.*)").matcher(
							mine.toLowerCase());
					if (m.find())
						return m.group(1);
				}
			}
			filename = UUID.randomUUID() + ".tmp";
		}
		return filename;
	}

	public int download(SmartDownloadProgressListener listener)
			throws Exception {
		try {
			Log.i(TAG, checkNetworkStat() + "");
			RandomAccessFile randOut = new RandomAccessFile(this.saveFile, "rw");
			if (this.fileSize > 0)
				randOut.setLength(this.fileSize);
			randOut.close();
			URL url = new URL(this.downloadUrl);
			if (this.data.size() != this.threads.length) {
				this.data.clear();// data clear
				for (int i = 0; i < this.threads.length; i++) {
					this.data.put(i + 1, 0);
				}
			}
			for (int i = 0; i < this.threads.length; i++) {
				int downLength = this.data.get(i + 1);
				if (downLength < this.block
						&& this.downloadSize < this.fileSize) { // if not finish
																// download,
																// continue
					this.threads[i] = new SmartDownloadThread(this, url,
							this.saveFile, this.block, this.data.get(i + 1),
							i + 1);
					this.threads[i].setPriority(7);
					this.threads[i].start();
				} else {
					this.threads[i] = null;
				}
			}
			this.fileService.save(this.downloadUrl, this.data);
			boolean notFinish = true;
			while (notFinish) {// check IfFinish
				Thread.sleep(900);
				notFinish = false;
				for (int i = 0; i < this.threads.length; i++) {
					if (this.threads[i] != null && !this.threads[i].isFinish()) {
						notFinish = true;
						if (this.threads[i].getDownLength() == -1) {// if error,
																	// re-download
							this.threads[i] = new SmartDownloadThread(this,
									url, this.saveFile, this.block,
									this.data.get(i + 1), i + 1);
							this.threads[i].setPriority(7);
							this.threads[i].start();
						}
					}
				}
				if (listener != null)
					listener.onDownloadSize(this.downloadSize);
			}
			fileService.delete(this.downloadUrl);
		} catch (Exception e) {
			throw new Exception("file download fail");
		}
		return this.downloadSize;
	}

	// pause and resume
	public void pause() {
		isPause = true;
	}

	public void restart() {
		isPause = false;
		// resume all thread
		synchronized (fileService) {
			fileService.notifyAll();
		}
	}

	public boolean getStatus() {
		return isPause;
	}

	public long getstartDown() {// get start download time
		return startDown;
	}

	private boolean checkNetworkStat() {
		ConnectivityManager manager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (manager != null) {
			NetworkInfo[] infos = manager.getAllNetworkInfo();
			if (infos != null) {
				for (NetworkInfo ni : infos) {
					if (ni.isConnected()) {
						return true;
					}
				}
			}
		}
		return false;
	}
}
