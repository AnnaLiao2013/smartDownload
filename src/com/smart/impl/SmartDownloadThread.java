package com.smart.impl;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

import com.smart.db.FileService;

//download for one thread
public class SmartDownloadThread extends Thread {
	private static final String TAG = "SmartDownloadThread";
	private File saveFile;
	private URL downUrl;
	private int block;
	private int threadId = -1;
	private int downLength;
	private boolean finish = false;

	private SmartFileDownloader downloader;
	private FileService fileService;

	public SmartDownloadThread(SmartFileDownloader downloader, URL downUrl,
			File saveFile, int block, int downLength, int threadId) {
		this.downUrl = downUrl;
		this.saveFile = saveFile;
		this.block = block;
		this.downloader = downloader;
		this.threadId = threadId;
		this.downLength = downLength;
	}

	@Override
	public void run() {
		if (downLength < block) {// download unfinished
			try {
				HttpURLConnection http = (HttpURLConnection) downUrl
						.openConnection();
				http.setConnectTimeout(5 * 1000);
				http.setRequestMethod("GET");
				http.setRequestProperty(
						"Accept",
						"image/gif, image/jpeg, image/pjpeg, image/pjpeg, application/x-shockwave-flash, application/xaml+xml, application/vnd.ms-xpsdocument, application/x-ms-xbap, application/x-ms-application, application/vnd.ms-excel, application/vnd.ms-powerpoint, application/msword, */*");
				http.setRequestProperty("Accept-Language", "en-GB");
				http.setRequestProperty("Referer", downUrl.toString());
				http.setRequestProperty("Charset", "UTF-8");
				int startPos = block * (threadId - 1) + downLength;// start
																	// position
				int endPos = block * threadId - 1;// end position
				http.setRequestProperty("Range", "bytes=" + startPos + "-"
						+ endPos);// get the range of data
				http.setRequestProperty(
						"User-Agent",
						"Mozilla/4.0 (compatible; MSIE 8.0; Windows NT 5.2; Trident/4.0; .NET CLR 1.1.4322; .NET CLR 2.0.50727; .NET CLR 3.0.04506.30; .NET CLR 3.0.4506.2152; .NET CLR 3.5.30729)");
				http.setRequestProperty("Connection", "Keep-Alive");

				InputStream inStream = http.getInputStream();
				byte[] buffer = new byte[1024];
				int len = 0;

				RandomAccessFile threadfile = new RandomAccessFile(
						this.saveFile, "rwd");
				threadfile.seek(startPos);

				while ((len = inStream.read(buffer, 0, 1024)) != -1) {
					if (downloader.getStatus()) {
						// make the thread wait
						synchronized (fileService) {
							try {
								fileService.wait();
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
					}
					threadfile.write(buffer, 0, len);
					downLength += len;
					downloader.update(this.threadId, downLength);
					downloader.saveLogFile();
					downloader.append(len);

				}
				threadfile.close();
				inStream.close();
				this.finish = true;
			} catch (Exception e) {
				this.downLength = -1;
			}
		}
	}

	public boolean isFinish() {
		return finish;
	}

	public long getDownLength() { // when download failure, return -1
		return downLength;
	}

}
