package com.example.peeler;

import java.util.List;
import java.util.concurrent.*;

import android.app.ListActivity;
import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.util.Log;

public class DeviceView extends ListActivity {
	List<RAOPDevice> devices;
	ScheduledThreadPoolExecutor search;

	private AirPlayer player;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		getListView().setTextFilterEnabled(true);

		player = ((Peeler)getParent()).streamer.player;
	}

	public void onResume() {
		super.onResume();

		//search = new ScheduledThreadPoolExecutor(1);
		//search.scheduleAtFixedRate(new Runnable() {
		//	public void run() {
				updateDevices();
		//	}
		//}, 0, 10, TimeUnit.SECONDS);
	}

	public void onPause() {
		super.onPause();
		//search.shutdownNow();
	}
	
	private void updateDevices() {
		// TODO display a loading graphic
		devices = AirPlayer.findDevices();
		//setListAdapter(new ArrayAdapter<RAOPDevice>(this, R.layout.list_item, devices));
		player.setActiveDevices(devices);
	}

	/*protected void onListItemClick(ListView l, View v, int position, long id) {
		
	}*/
}