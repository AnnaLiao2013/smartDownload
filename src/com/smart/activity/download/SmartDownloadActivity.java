package com.smart.activity.download;

import java.io.File;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import com.smart.activity.download.R;
import com.smart.impl.SmartDownloadProgressListener;
import com.smart.impl.SmartFileDownloader;

//Main activity
public class SmartDownloadActivity extends Activity {
	private ProgressBar downloadbar;
	private EditText pathText;
	private TextView resultView;
	private Button button;
	private long startTime;

	private SmartFileDownloader loader;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		button = (Button) this.findViewById(R.id.button);
		button.setText(R.string.download);

		downloadbar = (ProgressBar) this.findViewById(R.id.downloadbar);
		pathText = (EditText) this.findViewById(R.id.path);
		resultView = (TextView) this.findViewById(R.id.result);
		button.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				downloadbar.setVisibility(View.VISIBLE);
				if ("Pause".equals(button.getText())) {
					button.setText(R.string.resume);
					if (loader != null) {
						loader.pause();
					}
				} else {
					if ("Download".equals(button.getText())) {
						button.setText(R.string.pause);
						String path = pathText.getText().toString();
						if (Environment.getExternalStorageState().equals(
								Environment.MEDIA_MOUNTED)) {// find if the
																// cellphone
																// has storage.
							File dir = Environment
									.getExternalStorageDirectory();
							download(path, dir);
						} else {
							Toast.makeText(SmartDownloadActivity.this,
									R.string.sdcarderror, Toast.LENGTH_LONG)
									.show();
						}
					} else if ("Resume".equals(button.getText())) {
						if (loader != null) {
							loader.restart();
						}
						button.setText(R.string.pause);
					}
					startTime = System.currentTimeMillis();
				}
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
		Toast.makeText(
				SmartDownloadActivity.this,
				checkNetworkStatus() ? R.string.wifi_connected
						: R.string.wifi_unconnected, Toast.LENGTH_LONG).show();
	}

	private void download(final String path, final File dir) {
		final Handler handler = new Handler() {
			@Override
			public void handleMessage(Message msg) {// record progress

				switch (msg.what) {
				case 1:
					if (!isWifiConnected()) {
						Toast.makeText(SmartDownloadActivity.this,
								"WIFI Disconnected", Toast.LENGTH_LONG).show();
					}
					int size = msg.getData().getInt("size");
					downloadbar.setProgress(size);
					float result = (float) downloadbar.getProgress()
							/ (float) downloadbar.getMax();
					int p = (int) (result * 100);
					float latency = (float) (loader.getstartDown() - startTime) / 1000;
					long curTime = System.currentTimeMillis();
					int usedTime = (int) ((curTime - startTime) / 1000);// second as unit
					if (usedTime != 0 && !loader.getStatus()) {
						float speed = (size / usedTime) / 1024;// average
																// download
																// speed
						int estimateTime = (downloadbar.getMax() - downloadbar
								.getProgress())
								/ (downloadbar.getProgress() / usedTime);
						if (estimateTime > 0) {// display speed and estimate
												// needed
							// download time
							resultView.setText(p + "%  " + speed
									+ "Kb/s  \nEstimate download time: "
									+ estimateTime + "s\n  Latency:" + latency
									+ "s");
						} else {
							resultView.setText(p + "%  " + speed + "Kb/s  \n");
						}
					} else if (loader.getStatus()) {
						resultView.setText("Already download " + p + "%  ");
					}

					if (downloadbar.getProgress() == downloadbar.getMax()) {
						Toast.makeText(SmartDownloadActivity.this,
								R.string.success, Toast.LENGTH_LONG).show();
						button.setText(R.string.done);
					}
					break;

				case -1:
					Toast.makeText(SmartDownloadActivity.this, R.string.error,
							Toast.LENGTH_LONG).show();
					break;
				}

			}
		};
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					loader = new SmartFileDownloader(
							SmartDownloadActivity.this, path, dir, 3);// set download path and thread number
					int length = loader.getFileSize();
					downloadbar.setMax(length);
					loader.download(new SmartDownloadProgressListener() {
						@Override
						public void onDownloadSize(int size) {
							Message msg = new Message();
							msg.what = 1;
							msg.getData().putInt("size", size);
							handler.sendMessage(msg);
						}
					});
				} catch (Exception e) {
					Message msg = new Message();// message notification
					msg.what = -1;
					msg.getData().putString("error", "Download failure");
					handler.sendMessage(msg);
				}
			}
		}).start();// start
	}

	private boolean checkNetworkStatus() {
		ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		if (manager != null) {
			NetworkInfo[] infos = manager.getAllNetworkInfo();
			if (infos != null) {
				for (NetworkInfo networkInfo : infos) {
					if (networkInfo.isConnected()) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private boolean isWifiConnected() {
		ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		if (manager != null) {
			NetworkInfo networkInfo = manager.getActiveNetworkInfo();
			if (networkInfo != null
					&& networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
				return true;
			}
		}
		return false;
	}
}