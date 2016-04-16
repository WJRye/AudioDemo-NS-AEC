package com.wj.audiodemo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import com.wj.audiodemo.R;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.NoiseSuppressor;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity {

	private static final String TAG = "TAG";
	private short[] mAudioRecordData;
	private short[] mAudioTrackData;

	private Button mStart;
	private Button mStop;
	private Button mPlay;

	private File mAudioFile;
	private AudioRecord mAudioRecord;
	private AudioTrack mAudioTrack;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		init();

		registerListener();
	}

	private void registerListener() {

		mStart.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				new Thread(new Runnable() {
					public void run() {
						android.os.Process
								.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
						try {
							mAudioRecord.startRecording();
							DataOutputStream dos = new DataOutputStream(
									new BufferedOutputStream(
											new FileOutputStream(mAudioFile)));
							while (mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
								int number = mAudioRecord.read(
										mAudioRecordData, 0,
										mAudioRecordData.length);
								for (int i = 0; i < number; i++) {
									dos.writeShort(mAudioRecordData[i]);
								}
								if (AudioRecord.ERROR_BAD_VALUE == number
										|| AudioRecord.ERROR == number) {
									Log.d(TAG,
											"Error:" + String.valueOf(number));
									break;
								}
							}
							dos.flush();
							dos.close();
							Log.d(TAG, "dos.close()");
						} catch (FileNotFoundException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}).start();
			}
		});

		mStop.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				if (mAudioRecord != null
						&& mAudioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
					mAudioRecord.stop();
				}
			}
		});

		mPlay.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {

				new Thread(new Runnable() {
					public void run() {
						android.os.Process
								.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
						try {
							mAudioTrack.play();
							DataInputStream dis = new DataInputStream(
									new BufferedInputStream(
											new FileInputStream(mAudioFile)));
							Log.d(TAG, "dis.available=" + dis.available());
							int i = 0;
							while (mAudioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING
									&& dis.available() > 0) {
								while (dis.available() > 0
										&& i < mAudioTrackData.length) {
									mAudioTrackData[i] = dis.readShort();
									i++;
								}
								mAudioTrack.write(mAudioTrackData, 0,
										mAudioTrackData.length);
								i = 0;
							}
							mAudioTrack.stop();
							dis.close();
							Log.d(TAG, "dis.close()");
						} catch (FileNotFoundException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}).start();
			}
		});

	}

	@SuppressLint("NewApi")
	private void init() {
		AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		am.setMode(AudioManager.MODE_IN_COMMUNICATION); // 听筒播放录音

		mStart = (Button) findViewById(R.id.start);
		mStop = (Button) findViewById(R.id.stop);
		mPlay = (Button) findViewById(R.id.play);
		if (Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			File file = new File(Environment.getExternalStorageDirectory()
					.getAbsolutePath() + "/audio/");
			if (!file.exists()) {
				file.mkdirs();
			}
			mAudioFile = new File(file, System.currentTimeMillis() + ".pcm");
		} else {
			Toast.makeText(this, "The SDCard doesn't exist!", Toast.LENGTH_LONG)
					.show();
		}

		try {
			int sampleRateInHz = 8000;// 22050, 16000, 11025,44100
			int recordBufferSizeInBytes = AudioRecord.getMinBufferSize(
					sampleRateInHz, AudioFormat.CHANNEL_IN_MONO,
					AudioFormat.ENCODING_PCM_16BIT);
			Log.d(TAG, "recordBufferSizeInBytes=" + recordBufferSizeInBytes);
			mAudioRecordData = new short[recordBufferSizeInBytes];
			mAudioRecord = new AudioRecord(
					MediaRecorder.AudioSource.VOICE_COMMUNICATION,
					sampleRateInHz, AudioFormat.CHANNEL_IN_MONO,
					AudioFormat.ENCODING_PCM_16BIT, recordBufferSizeInBytes * 2);

			if (isAECAailable()) {
				AcousticEchoCanceler acousticEchoCanceler = AcousticEchoCanceler
						.create(mAudioRecord.getAudioSessionId());
				int resultCode = acousticEchoCanceler.setEnabled(true);
				if (AudioEffect.SUCCESS == resultCode) {
					Log.d(TAG, "aec-->success");
				}
			}

			if (isNSAvailable()) {
				NoiseSuppressor noiseSuppressor = NoiseSuppressor
						.create(mAudioRecord.getAudioSessionId());
				int resultCode = noiseSuppressor.setEnabled(true);
				if (AudioEffect.SUCCESS == resultCode) {
					Log.d(TAG, "ns-->success");
				}
			}

			int trackBufferSizeInBytes = AudioTrack.getMinBufferSize(
					sampleRateInHz, AudioFormat.CHANNEL_OUT_MONO,
					AudioFormat.ENCODING_PCM_16BIT);
			mAudioTrackData = new short[trackBufferSizeInBytes];
			mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
					sampleRateInHz, AudioFormat.CHANNEL_OUT_MONO,
					AudioFormat.ENCODING_PCM_16BIT, trackBufferSizeInBytes,
					AudioTrack.MODE_STREAM, mAudioRecord.getAudioSessionId());
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		}

	}

	/**
	 * �Ƿ�֧�ֻ���������API�ĵ���ַ��http://android.toolib.net/reference/android/
	 * media/ audiofx/AcousticEchoCanceler.html
	 */
	@SuppressLint("NewApi")
	private static boolean isAECAailable() {
		return AcousticEchoCanceler.isAvailable();
	}

	/**
	 * �Ƿ�֧�־�������
	 */
	@SuppressLint("NewApi")
	private static boolean isNSAvailable() {
		return NoiseSuppressor.isAvailable();
	}

	@Override
	protected void onStop() {
		if (mAudioFile != null && mAudioFile.exists()) {
			mAudioFile.delete();
		}
		if (mAudioRecord != null) {
			mAudioRecord.release();
			mAudioRecord = null;
		}
		if (mAudioTrack != null) {
			mAudioTrack.release();
			mAudioTrack = null;
		}
		super.onStop();
	}

}
