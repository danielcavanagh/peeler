package com.example.peeler;

import java.util.List;
import java.lang.Thread;

import android.util.Log;

import android.app.Activity;
import android.os.Bundle;
import android.os.AsyncTask;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

public class CurrentSongView extends Activity {
	private Peeler peeler;
	private Streamer streamer;

	private TextView artist, album, title, currentTime, totalTime;
	private SeekBar position;
	private Button prevButton, playButton, pauseButton, nextButton;

	private Song currentSong;

	private UpdateTask updateTask;

	private class UpdateTask extends AsyncTask<Void, Void, Void> {
		protected Void doInBackground(Void... params) {
			while (true) {
				if (streamer.isStopped()) return null;

				try { Thread.sleep(100); } catch (InterruptedException e) { }

				publishProgress();
			}
		}

		protected void onProgressUpdate(Void... params) {
			if (currentSong == streamer.currentSong())
				updatePosition();
			else {
				updateDetails();
			}
		}

		protected void onPostExecute(Void presult) { updateDetails(); }
	}

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		setContentView(R.layout.current_song);
		title = (TextView)findViewById(R.id.songTitle);
		album = (TextView)findViewById(R.id.album);
		artist = (TextView)findViewById(R.id.artist);
		prevButton = (Button)findViewById(R.id.prev);
		playButton = (Button)findViewById(R.id.play);
		pauseButton = (Button)findViewById(R.id.pause);
		nextButton = (Button)findViewById(R.id.next);
		currentTime = (TextView)findViewById(R.id.currentTime);
		totalTime = (TextView)findViewById(R.id.totalTime);
		position = (SeekBar)findViewById(R.id.position);
		
		position.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			public void onProgressChanged(SeekBar seekBar, int pos, boolean fromUser) {
				if (fromUser) {
					streamer.setPosition(pos);
					updatePosition();
				}
			}
			
			public void onStartTrackingTouch(SeekBar seekBar) { }
			public void onStopTrackingTouch(SeekBar seekBar) { }
		});

		peeler = (Peeler)getParent();
		streamer = peeler.streamer;
	}
	
	public void onResume() {
		super.onResume();

		updateDetails();
		if (currentSong != null) {
			updateTask = new UpdateTask();
			updateTask.execute();
		}
	}
	
	public void onPause() {
		super.onPause();
		if (updateTask != null) updateTask.cancel(true);
	}

	public void prev(View view) {
		streamer.prev();
		updateDetails();
	}

	public void play(View view) {
		streamer.play();
		updateDetails();
	}

	public void pause(View view) {
		streamer.pause();
		updateDetails();
	}

	public void next(View view) {
		streamer.next();
		updateDetails();
	}
	
	public void toggleShuffle(View view) {
		peeler.toggleShuffle();
	}
	
	public void toggleRepeat(View view) {
		streamer.toggleRepeat();
	}

	void updateDetails() {
		currentSong = streamer.currentSong();
		if (currentSong == null) {
			artist.setText("");
			album.setText("Nothing playing");
			title.setText("");

			prevButton.setEnabled(false);
			playButton.setEnabled(false);
			nextButton.setEnabled(false);
			showPlayButton();

			position.setEnabled(false);
			position.setProgress(0);
			totalTime.setText("");
			currentTime.setText("");
		} else {
			artist.setText("Artist: " + currentSong.artist.name);
			album.setText("Album: " + currentSong.album.title);
			title.setText("Title: " + currentSong.title);

			prevButton.setEnabled(true);
			playButton.setEnabled(true);
			nextButton.setEnabled(true);
			if (streamer.isPlaying())
				showPauseButton();
			else
				showPlayButton();

			position.setEnabled(true);
			position.setMax(currentSong.duration);
			totalTime.setText(timeToStr(currentSong.duration));
			updatePosition();
		}
	}

	void updatePosition() {
		currentTime.setText(timeToStr(streamer.currentPosition()));
		position.setProgress(streamer.currentPosition());
	}

	void showPauseButton() {
		playButton.setVisibility(View.GONE);
		pauseButton.setVisibility(View.VISIBLE);
	}

	void showPlayButton() {
		pauseButton.setVisibility(View.GONE);
		playButton.setVisibility(View.VISIBLE);
	}

	static String timeToStr(int time) {
		int msecs = time % 1000;
		int secs = (time /= 1000) % 60;
		int mins = (time /= 60) % 60;
		int hours = (time /= 60);
		String str = "";
		if (hours > 0) str += hours + ":";
		str += mins + ":";
		return str + (secs < 10 ? "0" : "") + secs;
	}
}