package com.example.peeler;

/* TODO
 * check how the stream service handles an empty song list. it may throw an exception
 */
 
import java.lang.Thread;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import android.util.Log;

import android.app.TabActivity;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import android.widget.TextView;
import android.os.Bundle;
import android.os.IBinder;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;

import android.provider.MediaStore;
import android.database.Cursor;
import android.net.Uri;

public class Peeler extends TabActivity {
	public Streamer streamer;
	private Intent streamerIntent;
	private ServiceConnection conn;
	
	private TabHost tabHost;
	
	private List<Song> songList;
	private boolean shuffle;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		streamerIntent = new Intent(this, Streamer.class);
		if (startService(streamerIntent) == null) {
			Log.e("Peeler", "Couldn't start the streamer service");
			finish();
			return;
		}

		conn = new ServiceConnection() {
			public void onServiceConnected(ComponentName name, IBinder binder) {
				streamer = ((Streamer.StreamBinder)binder).exchangeRefs(Peeler.this);
				start();
			}

			public void onServiceDisconnected(ComponentName name) {
			}
		};

		bindService(streamerIntent, conn, 0);
    }

	public void onDestroy() {
		super.onDestroy();
		unbindService(conn);
	}

	public void start() {
		setContentView(R.layout.main);
		tabHost = getTabHost();
		TabSpec tab;
		
		tab = tabHost.newTabSpec("current");
		tab.setIndicator("Current");
		tab.setContent(new Intent(this, CurrentSongView.class));
		tabHost.addTab(tab);

		tab = tabHost.newTabSpec("songs");
		tab.setIndicator("Songs");
		tab.setContent(new Intent(this, SongListView.class));
		tabHost.addTab(tab);

		tab = tabHost.newTabSpec("artists");
		tab.setIndicator("Artists");
		tab.setContent(new Intent(this, ArtistListView.class));
		tabHost.addTab(tab);

		tab = tabHost.newTabSpec("albums");
		tab.setIndicator("Albums");
		tab.setContent(new Intent(this, AlbumListView.class));
		tabHost.addTab(tab);

		tab = tabHost.newTabSpec("playlists");
		tab.setIndicator("Playlists");
		tab.setContent(new Intent(this, PlaylistListView.class));
		tabHost.addTab(tab);

		tab = tabHost.newTabSpec("genres");
		tab.setIndicator("Genres");
		tab.setContent(new Intent(this, GenreListView.class));
		tabHost.addTab(tab);

		tab = tabHost.newTabSpec("devices");
		tab.setIndicator("Devices");
		tab.setContent(new Intent(this, DeviceView.class));
		tabHost.addTab(tab);
	}

	public void switchToCurrentTab() {
		tabHost.setCurrentTabByTag("current");
	}

	public void setSongList(List<Song> list, int index) {
		songList = list;
		if (shuffle)
			shuffleList(false);
		else
			streamer.setSongList(songList, index);
	}

	public void setSongList(List<Song> list) {
		setSongList(list, 0);
	}

	public void toggleShuffle() {
		shuffle = !shuffle;
		if (songList != null) {
			if (shuffle)
				shuffleList(true);
			else
				streamer.setSongListAndMaintainState(songList);
		}
	}

	private void shuffleList(boolean maintainState) {
		ArrayList<Song> list = new ArrayList(songList);
		Collections.shuffle(list);

		if (maintainState) {
			Song song = (streamer.isEmpty() ? songList.get(0) : streamer.currentSong());
			if (list.remove(song)) list.add(0, song);
			streamer.setSongListAndMaintainState(list);
		} else
			streamer.setSongList(list);
	}
}
