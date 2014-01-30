package com.example.peeler;

import java.util.List;

import android.app.ListActivity;
import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class PlaylistListView extends ListActivity {
	private List<Playlist> list;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		list = Playlist.allPlaylists(this);
		setListAdapter(new ArrayAdapter<Playlist>(this, R.layout.list_item, list));

		getListView().setTextFilterEnabled(true);
	}
	
	protected void onListItemClick(ListView l, View v, int position, long id) {
		((Peeler)getParent()).setSongList(list.get(position).songs(getParent()));
		((Peeler)getParent()).switchToCurrentTab();
	}
}