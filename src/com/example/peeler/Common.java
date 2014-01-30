package com.example.peeler;

import java.util.List;
import java.util.ArrayList;

import android.provider.MediaStore;
import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;

class Song {
	int id;
	String key;
	String title;
	String display_name;
	Artist artist;
	Album album;
	int track_no;
	int year;
	int duration;
	String uri;
	String mimeType;
	int bytes;

	Song(int id, String key, String title, String display_name, Artist artist, Album album, int track_no, int year, int duration, String uri, String mimeType, int bytes) {
		this.id = id;
		this.key = key;
		this.title = title;
		this.display_name = display_name;
		this.artist = artist;
		this.album = album;
		this.track_no = track_no;
		this.year = year;
		this.duration = duration;
		this.uri = uri;
		this.mimeType = mimeType;
		this.bytes = bytes;
	}
	
	public String toString() {
		return title;
	}

	static List<Song> allSongs(Activity act) {
		return songList(act);
	}

	static List<Song> songList(Activity act, Uri contentUri, String selection) {
		Cursor cursor = act.managedQuery(contentUri, null, selection, null, null);
		if (cursor != null && cursor.moveToFirst()) {
			ArrayList<Song> list = new ArrayList<Song>(cursor.getCount());

			int id = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
			int key = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE_KEY);
			int title = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
			int display_name = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME);
			int artist = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
			int artist_key = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST_KEY);
			int artist_id = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST_ID);
			int album = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
			int album_key = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_KEY);
			int album_id = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
			int album_art = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ART);
			int duration = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
			int track = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK);
			int year = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR);
			int data = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
			int mime_type = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE);
			int size = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE);

			do {
				list.add(new Song(
					cursor.getInt(id),
					cursor.getString(key),
					cursor.getString(title),
					cursor.getString(display_name),
					new Artist(
						cursor.getInt(artist_id),
						cursor.getString(artist_key),
						cursor.getString(artist)
					),
					new Album(
						cursor.getInt(album_id),
						cursor.getString(album_key),
						cursor.getString(album),
						album_art > -1 ? cursor.getString(album_art) : null
					),
					cursor.getInt(track),
					cursor.getInt(year),
					cursor.getInt(duration),
					cursor.getString(data),
					cursor.getString(mime_type),
					cursor.getInt(size)
				));
			} while (cursor.moveToNext());

			list.trimToSize();
			return list;
		} else
			return new ArrayList<Song>();
	}

	static List<Song> songList(Activity act, Uri contentUri) {
		return songList(act, contentUri, null);
	}

	static List<Song> songList(Activity act, String selection) {
		return songList(act, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, selection);
	}

	static List<Song> songList(Activity act) {
		return songList(act, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null);
	}
}

class Artist {
	int id;
	String key;
	String name;
	int numberOfAlbums;
	int numberOfTracks;
	
	Artist(int id, String key, String name, int numberOfAlbums, int numberOfTracks) {
		this.id = id;
		this.key = key;
		this.name = (name.equals("<unknown>") ? "Unknown artist" : name);
		this.numberOfAlbums = numberOfAlbums;
		this.numberOfTracks = numberOfTracks;
	}
	
	Artist(int id, String key, String name) {
		this(id, key, name, -1, -1);
	}
	
	public String toString() {
		return name;
	}

	List<Album> albums(Activity act) {
		return Album.albumList(act, MediaStore.Audio.Artists.Albums.getContentUri("external", id));
	}

	List<Song> songs(Activity act) {
		return Song.songList(act, MediaStore.Audio.Media.ARTIST_ID + "=" + id);
	}

	static List<Artist> allArtists(Activity act) {
		Cursor cursor = act.managedQuery(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, null, null, null, null);
		if (cursor != null && cursor.moveToFirst()) {
			ArrayList<Artist> list = new ArrayList<Artist>(cursor.getCount());

			int id = cursor.getColumnIndex(MediaStore.Audio.Artists._ID);
			int artist = cursor.getColumnIndex(MediaStore.Audio.Artists.ARTIST);
			int artist_key = cursor.getColumnIndex(MediaStore.Audio.Artists.ARTIST_KEY);

			do {
				if (cursor.getString(artist).equals("<unknown>")) continue;
				list.add(new Artist(
					cursor.getInt(id),
					cursor.getString(artist_key),
					cursor.getString(artist)
				));
			} while (cursor.moveToNext());

			list.trimToSize();
			return list;
		} else
			return new ArrayList<Artist>();
	}
}

class Album {
	int id;
	String key;
	String title;
	String artist;
	int numberOfTracks;
	String art_uri;
	
	Album(int id, String key, String title, String artist, int numberOfTracks, String art_uri) {
		this.id = id;
		this.key = key;
		this.title = (title.equals("sdcard") ? "Unknown album" : title);
		this.artist = artist;
		this.numberOfTracks = numberOfTracks;
		this.art_uri = art_uri;
	}

	Album(int id, String key, String title, String art_uri) {
		this(id, key, title, null, -1, art_uri);
	}
	
	public String toString() {
		return title;
	}

	List<Song> songs(Activity act) {
		return Song.songList(act, MediaStore.Audio.Media.ALBUM_ID + "=" + id);
	}

	static List<Album> allAlbums(Activity act) {
		return albumList(act, MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI);
	}
	
	static List<Album> albumList(Activity act, Uri contentUri) {
		Cursor cursor = act.managedQuery(contentUri, null, null, null, null);
		if (cursor != null && cursor.moveToFirst()) {
			ArrayList<Album> list = new ArrayList<Album>(cursor.getCount());

			int id = cursor.getColumnIndex(MediaStore.Audio.Albums._ID);
			int album = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM);
			int album_key = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_KEY);
			int album_art = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM_ART);
			int artist = cursor.getColumnIndex(MediaStore.Audio.Albums.ARTIST);
			int numberOfTracks = cursor.getColumnIndex(MediaStore.Audio.Albums.NUMBER_OF_SONGS);

			do {
				if (cursor.getString(album).equals("sdcard")) continue;
				list.add(new Album(
					cursor.getInt(id),
					cursor.getString(album_key),
					cursor.getString(album),
					cursor.getString(artist),
					cursor.getInt(numberOfTracks),
					album_art > -1 ? cursor.getString(album_art) : null
				));
			} while (cursor.moveToNext());

			list.trimToSize();
			return list;
		} else
			return new ArrayList<Album>();
	}
}

class Playlist {
	int id;
	String name;
	String uri;

	Playlist(int id, String name, String uri) {
		this.id = id;
		this.name = name;
		this.uri = uri;
	}
	
	public String toString() {
		return name;
	}

	List<Song> songs(Activity act) {
		return Song.songList(act, MediaStore.Audio.Playlists.Members.getContentUri("external", id));
	}

	static List<Playlist> allPlaylists(Activity act) {
		Cursor cursor = act.managedQuery(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, null, null, null, null);
		if (cursor != null && cursor.moveToFirst()) {
			ArrayList<Playlist> list = new ArrayList<Playlist>(cursor.getCount());

			int id = cursor.getColumnIndex(MediaStore.Audio.Playlists._ID);
			int name = cursor.getColumnIndex(MediaStore.Audio.Playlists.NAME);
			int uri = cursor.getColumnIndex(MediaStore.Audio.Playlists.DATA);

			do {
				list.add(new Playlist(
					cursor.getInt(id),
					cursor.getString(name),
					cursor.getString(uri)
				));
			} while (cursor.moveToNext());

			list.trimToSize();
			return list;
		} else
			return new ArrayList<Playlist>();
	}
}

class Genre {
	int id;
	String name;

	Genre(int id, String name) {
		this.id = id;
		this.name = (isNone(name) ? "None" : name);
	}

	public String toString() {
		return name;
	}

	List<Song> songs(Activity act) {
		return Song.songList(act, MediaStore.Audio.Genres.Members.getContentUri("external", id));
	}

	static List<Genre> allGenres(Activity act) {
		Cursor cursor = act.managedQuery(MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI, null, null, null, null);
		if (cursor != null && cursor.moveToFirst()) {
			ArrayList<Genre> list = new ArrayList<Genre>(cursor.getCount());

			int id = cursor.getColumnIndex(MediaStore.Audio.Genres._ID);
			int name = cursor.getColumnIndex(MediaStore.Audio.Genres.NAME);

			do {
				// mp3s return a genre of 255 if the field is defined but set to 'none'
				if (isNone(cursor.getString(name))) continue;
				list.add(new Genre(
					cursor.getInt(id),
					cursor.getString(name)
				));
			} while (cursor.moveToNext());

			list.trimToSize();
			return list;
		} else
			return new ArrayList<Genre>();
	}
	
	static private boolean isNone(String name) {
		return name.equals("") || name.equals("255");
	}
}