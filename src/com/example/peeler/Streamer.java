package com.example.peeler;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import android.util.Log;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import android.media.MediaPlayer;
import android.media.AudioManager;
import java.io.*;
import android.net.Uri;

public class Streamer extends Service {
	private Peeler peeler;

	AirPlayer player;
	private List<Song> list;
	private int index;
	private boolean repeat = false;

	public enum State { STOPPED, PLAYING, PAUSED };
	private State state = State.STOPPED;

	public void onCreate() {
		super.onCreate();

		player = new AirPlayer();
		/*player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
			public void onCompletion(MediaPlayer mp) {
				if (!repeat && isLastSong())
					stop();
				else
					Streamer.this.next();
			}
		});*/

		state = State.STOPPED;
	}

	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}

	/* XXX can this be done some other way? seems a bit of a hack... */
	public class StreamBinder extends Binder {
		Streamer exchangeRefs(Peeler peeler) {
			Streamer.this.peeler = peeler;
			return Streamer.this;
		}
	}

	private final IBinder binder = new StreamBinder();

	public IBinder onBind(Intent intent) {
		return binder;
	}

	void setSongList(List<Song> list, int index) {
		// XXX mutex needed
		stop();
		this.list = list;
		this.index = index;
		play();
	}

	void setSongList(List<Song> list) {
		setSongList(list, 0);
	}

	// set the song list but maintain the currently playing song's position and state.
	// this song must be part of the new list
	void setSongListAndMaintainState(List<Song> list) {
		// XXX mutex needed
		Song song = currentSong();
		this.list = list;
		this.index = (song == null ? 0 : list.indexOf(song));
	}

	void play() {
		if (isPlaying()) return;
		if (startStreamer())
			state = State.PLAYING;
		else
			next();
	}

	void start() {
		play();
	}

	void pause() {
		if (isStopped() || isPaused()) return;
		pauseStreamer();
		state = State.PAUSED;
	}

	void stop() {
		stopStreamer();
		list = null;
		state = State.STOPPED;
	}

	void next() {
		while (true) {
			// XXX mutex needed
			if (isLastSong()) {
				if (!repeat) {
					stop();
					return;
				}
				index = 0;
			} else
				index++;

			if (restartStreamer())
				break;
			else
				continue;
		}
	}

	void prev() {
		while (true) {
			// XXX mutex needed
			if (isFirstSong()) {
				if (!repeat) {
					stop();
					return;
				}
				index = list.size() - 1;
			} else
				index--;

			if (restartStreamer())
				break;
			else
				continue;
		}
	}

	Song currentSong() {
		// XXX mutex needed
		return isEmpty() ? null : list.get(index);
	}

	int currentPosition() {
		return player.currentPosition();
	}

	void setPosition(int pos) {
		;//player.seekTo(pos);
	}

	void setRepeat(boolean repeat) {
		this.repeat = repeat;
	}

	boolean toggleRepeat() {
		return repeat = !repeat;
	}

	boolean isStopped() { return state == State.STOPPED; }
	boolean isPlaying() { return state == State.PLAYING; }
	boolean isPaused() { return state == State.PAUSED; }
	boolean isFirstSong() { return index <= 0; }
	boolean isLastSong() { return index >= list.size() - 1; }
	boolean isEmpty() { return list == null || list.size() == 0; }

	boolean startStreamer() {
		if (isStopped()) {
			if (!setupStreamer())
				return false;
		}
Log.e("peeler", "Starting");
		
		new Thread(new Runnable() {
			public void run() {
				try { player.start(); }
				catch (NoAudioInput e) {
					Log.e("peeler", "No audio input set on AirPlayer");
				}
			}
		}).start();

		return true;
	}

	void pauseStreamer() {
		player.pause();
	}

	void stopStreamer() {
		if (!isStopped()) player.stop();
	}

	boolean restartStreamer() {
		if (!setupStreamer()) return false;
		if (isPlaying()) startStreamer();
		return true;
	}

	boolean setupStreamer() {
		// XXX mutex needed
		if (isEmpty())
			state = State.STOPPED;
		else {
Log.e("peeler", "Setting up");
			stopStreamer();

			try { player.setAudioInput(currentSong().uri); }
			catch (FileNotFoundException e) { Log.e("peeler", "Audio file not found"); return false; }

			if (isStopped()) state = State.PAUSED;
		}
		return true;
	}
}