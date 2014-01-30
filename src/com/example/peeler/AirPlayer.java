package com.example.peeler;

import android.util.Log;

import java.io.*;
import java.net.*;

import java.lang.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.math.BigInteger;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.security.spec.*;

class AirPlayer {
	static public void main(String[] args) throws FileNotFoundException {
		if (args.length == 0 || args.length > 2) {
			System.out.println("airplayer <file> [<volume>]");
			return;
		}

		AirPlayer player = new AirPlayer();
		List<RAOPDevice> devices = AirPlayer.findDevices();

		player.setActiveDevices(devices);
		if (args.length == 2) player.setVolume(Integer.valueOf(args[1]).intValue());
		player.start(args[0]);

		while (true) {
			try { Thread.sleep(0); }
			catch (InterruptedException e) { }
		}

		//player.closeAll();
	}

	static public int samplesPerSecond = 44100;
	static public int bytesPerMilliSec = (samplesPerSecond << 2) / 1000;

	static public int samplesPerPacket = 4096;
	static public int bytesPerPacket = samplesPerPacket << 2;
	static public int deviceBufferSize = (samplesPerSecond << 2) * 2; // size of buffer on remote device

	static final private String[] standardTypes = {
		"_airport._tcp.local",
		"_appletv._tcp.local"
	};

	static final byte[] airportPubKeyModBytes = {
		(byte)0xe7, (byte)0xd7, (byte)0x44, (byte)0xf2, (byte)0xa2, (byte)0xe2, (byte)0x78, (byte)0x8b,
		(byte)0x6c, (byte)0x1f, (byte)0x55, (byte)0xa0, (byte)0x8e, (byte)0xb7, (byte)0x05, (byte)0x44,
		(byte)0xa8, (byte)0xfa, (byte)0x79, (byte)0x45, (byte)0xaa, (byte)0x8b, (byte)0xe6, (byte)0xc6,
		(byte)0x2c, (byte)0xe5, (byte)0xf5, (byte)0x1c, (byte)0xbd, (byte)0xd4, (byte)0xdc, (byte)0x68,
		(byte)0x42, (byte)0xfe, (byte)0x3d, (byte)0x10, (byte)0x83, (byte)0xdd, (byte)0x2e, (byte)0xde,
		(byte)0xc1, (byte)0xbf, (byte)0xd4, (byte)0x25, (byte)0x2d, (byte)0xc0, (byte)0x2e, (byte)0x6f,
		(byte)0x39, (byte)0x8b, (byte)0xdf, (byte)0x0e, (byte)0x61, (byte)0x48, (byte)0xea, (byte)0x84,
		(byte)0x85, (byte)0x5e, (byte)0x2e, (byte)0x44, (byte)0x2d, (byte)0xa6, (byte)0xd6, (byte)0x26,
		(byte)0x64, (byte)0xf6, (byte)0x74, (byte)0xa1, (byte)0xf3, (byte)0x04, (byte)0x92, (byte)0x9a,
		(byte)0xde, (byte)0x4f, (byte)0x68, (byte)0x93, (byte)0xef, (byte)0x2d, (byte)0xf6, (byte)0xe7,
		(byte)0x11, (byte)0xa8, (byte)0xc7, (byte)0x7a, (byte)0x0d, (byte)0x91, (byte)0xc9, (byte)0xd9,
		(byte)0x80, (byte)0x82, (byte)0x2e, (byte)0x50, (byte)0xd1, (byte)0x29, (byte)0x22, (byte)0xaf,
		(byte)0xea, (byte)0x40, (byte)0xea, (byte)0x9f, (byte)0x0e, (byte)0x14, (byte)0xc0, (byte)0xf7,
		(byte)0x69, (byte)0x38, (byte)0xc5, (byte)0xf3, (byte)0x88, (byte)0x2f, (byte)0xc0, (byte)0x32,
		(byte)0x3d, (byte)0xd9, (byte)0xfe, (byte)0x55, (byte)0x15, (byte)0x5f, (byte)0x51, (byte)0xbb,
		(byte)0x59, (byte)0x21, (byte)0xc2, (byte)0x01, (byte)0x62, (byte)0x9f, (byte)0xd7, (byte)0x33,
		(byte)0x52, (byte)0xd5, (byte)0xe2, (byte)0xef, (byte)0xaa, (byte)0xbf, (byte)0x9b, (byte)0xa0,
		(byte)0x48, (byte)0xd7, (byte)0xb8, (byte)0x13, (byte)0xa2, (byte)0xb6, (byte)0x76, (byte)0x7f,
		(byte)0x6c, (byte)0x3c, (byte)0xcf, (byte)0x1e, (byte)0xb4, (byte)0xce, (byte)0x67, (byte)0x3d,
		(byte)0x03, (byte)0x7b, (byte)0x0d, (byte)0x2e, (byte)0xa3, (byte)0x0c, (byte)0x5f, (byte)0xff,
		(byte)0xeb, (byte)0x06, (byte)0xf8, (byte)0xd0, (byte)0x8a, (byte)0xdd, (byte)0xe4, (byte)0x09,
		(byte)0x57, (byte)0x1a, (byte)0x9c, (byte)0x68, (byte)0x9f, (byte)0xef, (byte)0x10, (byte)0x72,
		(byte)0x88, (byte)0x55, (byte)0xdd, (byte)0x8c, (byte)0xfb, (byte)0x9a, (byte)0x8b, (byte)0xef,
		(byte)0x5c, (byte)0x89, (byte)0x43, (byte)0xef, (byte)0x3b, (byte)0x5f, (byte)0xaa, (byte)0x15,
		(byte)0xdd, (byte)0xe6, (byte)0x98, (byte)0xbe, (byte)0xdd, (byte)0xf3, (byte)0x59, (byte)0x96,
		(byte)0x03, (byte)0xeb, (byte)0x3e, (byte)0x6f, (byte)0x61, (byte)0x37, (byte)0x2b, (byte)0xb6,
		(byte)0x28, (byte)0xf6, (byte)0x55, (byte)0x9f, (byte)0x59, (byte)0x9a, (byte)0x78, (byte)0xbf,
		(byte)0x50, (byte)0x06, (byte)0x87, (byte)0xaa, (byte)0x7f, (byte)0x49, (byte)0x76, (byte)0xc0,
		(byte)0x56, (byte)0x2d, (byte)0x41, (byte)0x29, (byte)0x56, (byte)0xf8, (byte)0x98, (byte)0x9e,
		(byte)0x18, (byte)0xa6, (byte)0x35, (byte)0x5b, (byte)0xd8, (byte)0x15, (byte)0x97, (byte)0x82,
		(byte)0x5e, (byte)0x0f, (byte)0xc8, (byte)0x75, (byte)0x34, (byte)0x3e, (byte)0xc7, (byte)0x82,
		(byte)0x11, (byte)0x76, (byte)0x25, (byte)0xcd, (byte)0xbf, (byte)0x98, (byte)0x44, (byte)0x7b
	};
	static final byte[] airportPubKeyExpBytes = { 0x01, 0x00, 0x01 };
	static final BigInteger airportPubKeyMod = new BigInteger(1, airportPubKeyModBytes);
	static final BigInteger airportPubKeyExp = new BigInteger(1, airportPubKeyExpBytes);

	private List<RAOPDevice> devices = new ArrayList<RAOPDevice>();
	private Cipher cipher;
	private byte[] encAESKey;
	private int volume;
	private FileInputStream input = null;
	private long currentPos = 0;
	private int bytesBuffered = 0;
	private Thread streamer;
	private boolean streaming = false;

	AirPlayer() {
		try {
			KeyGenerator keyGen = KeyGenerator.getInstance("AES");
			keyGen.init(128);
			SecretKey aesKey = keyGen.generateKey();
			cipher = Cipher.getInstance("AES/CBC/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, aesKey);

			RSAPublicKeySpec rsaKeySpec = new RSAPublicKeySpec(airportPubKeyMod, airportPubKeyExp);
			Cipher rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA1AndMGF1Padding");
			rsaCipher.init(Cipher.ENCRYPT_MODE, KeyFactory.getInstance("RSA").generatePublic(rsaKeySpec));
			encAESKey = rsaCipher.doFinal(aesKey.getEncoded());
		}
		catch (NoSuchAlgorithmException e) { System.out.println(e); /* won't happen */ }
		catch (NoSuchPaddingException e) { System.out.println(e); /* won't happen */ }
		catch (BadPaddingException e) { System.out.println(e); /* won't happen */ }
		catch (InvalidKeySpecException e) { System.out.println(e); /* won't happen */ }
		catch (InvalidKeyException e) { System.out.println(e); /* won't happen */ }
		catch (IllegalBlockSizeException e) { System.out.println(e); /* won't happen */ }
	}

	static public List<RAOPDevice> findDevices() {
		List<MDNSRecord> services = MDNSSD.findServices(standardTypes);
		List<RAOPDevice> devices = new ArrayList<RAOPDevice>();

		for (MDNSRecord s : services) {
			String[] parts = s.name.split("\\.", 3);
			for (InetAddress a : s.addresses) {
				try {
					RAOPDevice d = new RAOPDevice(a, parts[0], parts[1]);
					devices.add(d);
					break;
				}
				catch (Exception e) { 
					Log.e("peeler", "RAOP Device error: " + e);
					continue;
				}
			}
		}

		return devices;
	}

	public void setActiveDevices(List<RAOPDevice> devices) {
		if (devices == null) return;

		this.devices = devices;
		for (RAOPDevice d : devices)
			d.setup(encAESKey, cipher.getIV());
	}

	/* add a new device to the list, potentially while streaming */
	public void addActiveDevice(RAOPDevice device) {
		try {
			device.setup(encAESKey, cipher.getIV());
			device.setVolume(volume);
		}
		catch (IOException e) { return; }
		catch (RTSPException e) { return; }

		if (streamer.isAlive()) {
			RAOPDevice lastDevice;
			for (RAOPDevice d : devices) {
				d.queue.lock();
				lastDevice = d;
			}

			for (ByteBuffer b : devices.get(devices.size() - 1).queue)
				device.queue.offer(b);
			device.queue.lock();
			devices.add(device);

			for (RAOPDevice d : devices)
				d.queue.unlock();
		} else
			devices.add(device);
	}

	public void setVolume(int volume) {
		this.volume = volume;
		for (RAOPDevice d : devices) {
			try { d.setVolume(volume); }
			catch (IOException e) { }
			catch (RTSPException e) { }
		}
	}

	public void setAudioInput(String path) throws FileNotFoundException {
		input = new FileInputStream(path);
		currentPos = 0;
	}

	public void start() throws NoAudioInput {
		if (input == null) throw new NoAudioInput();

		startStreaming();

		for (RAOPDevice d : devices) {
			try { d.start(); }
			catch (IOException e) { /* should remove from the list of active devices */ }
			catch (RTSPException e) { /* should remove from the list of active devices */ }
		}
	}

	public void start(String path) throws FileNotFoundException {
		setAudioInput(path);
		try { start(); } catch (NoAudioInput e) { /* won't happen */ }
	}
	
	private void startStreaming() {
		streamer = new Thread(new Runnable() {
			public void run() {
				streaming = true;

				while (streaming) {
					try {
						int totalBytes = 0;
						byte[] data = new byte[bytesPerPacket];

						while (totalBytes < bytesPerPacket) {
							int readBytes = input.read(data, totalBytes, bytesPerPacket - totalBytes);
							if (readBytes == -1) break;
							totalBytes += readBytes;
						}

						if (bytesBuffered == deviceBufferSize)
							currentPos += totalBytes;
						else {
							bytesBuffered += totalBytes;
							if (bytesBuffered > deviceBufferSize) bytesBuffered = deviceBufferSize;
						}

						data = ALAC.encode(data);

						try { cipher.doFinal(data, 0, data.length / 16 * 16, data); }
						catch (ShortBufferException e) { System.out.println(e); /* won't happen */ }
						catch (IllegalBlockSizeException e) { System.out.println(e); /* won't happen */ }
						catch (BadPaddingException e) { System.out.println(e); /* won't happen */ }

						ByteBuffer buf = ByteBuffer.wrap(data);
						for (RAOPDevice d : devices)
							d.queue.put(buf);
					} catch (Exception e) { streaming = false; }
				}
			}
		});
		streamer.start();
	}

	public int currentPosition() {
		return (int)(currentPos / bytesPerMilliSec);
	}

	public void play() {
		for (RAOPDevice d : devices)
			d.play();
	}
	
	private void stopStreaming() {
		streamer.interrupt();
		try { streamer.join(); } catch (InterruptedException e) { }
	}

	public void pause() {
		stopStreaming();

		for (RAOPDevice d : devices) {
			try { d.pause(); }
			catch (IOException e) { /* should remove from the list of active devices */ }
			catch (RTSPException e) { /* should remove from the list of active devices */ }
		}

		currentPos -= bytesBuffered;
		try { input.getChannel().position(currentPos); }
		catch (IOException e) {
			/* couldn't rewind. too bad. just accept the loss of <bytesBuffered> worth of audio and continue */
			Log.e("peeler", "Couldn't rewind audio stream on pause. " + (bytesBuffered >> 2) + " samples lost");
			currentPos += bytesBuffered;
		}
		bytesBuffered = 0;

		streamer.start(); // startStreaming();
	}

	public boolean seek(int newPos) throws IOException {
		stopStreaming();

		long seekTo = newPos * bytesPerMilliSec;
		try {
			input.getChannel().position(seekTo);

			for (RAOPDevice d : devices)
				d.pause();
		} catch (IOException e) {
			/* couldn't seek. too bad really... */
			Log.e("peeler", "Couldn't seek in audio stream");
			return false;
		} catch (RTSPException e) {
			Log.e("peeler", "Couldn't seek in audio stream");
			return false;
		} finally { streamer.start(); }

		for (RAOPDevice d : devices)
			d.play();
		
		return true;
	}

	public void stop() {
		/* XXX implement */
	}

	public void close() {
		stopStreaming();
		for (RAOPDevice d : devices)
			d.close();
		input = null;
	}
}

class NoAudioInput extends Exception {
	NoAudioInput() {
		super("The audio input has not been set");
	}
}

class ALAC {
	static private byte[] alacHeader = {
		0x20, // bits 5-7: num of channels (0 mono, 1 stereo); rest: unknown
		0x00, // unknown
		0x02  // bit 1: 'not compressed' flag; bit 4: 'size included' flag; rest unknown
	};

	static public byte[] encode(byte[] data) {
		int padLen = (4 - (data.length % 4)) % 4;
		byte[] alacData = new byte[3 + data.length + padLen];
		System.arraycopy(alacHeader, 0, alacData, 0, 3);

		// byte-swap raw samples (little-endian to big-endian) and shift everything left by one bit
		for (int i = 0; i < data.length; i += 2) {
			byte highByte = (i + 1 < data.length ? data[i + 1] : 0);
			alacData[i + 2] |= (highByte & 0xff) >> 7;
			alacData[i + 3] = (byte)((highByte & 0xff) << 1);
			alacData[i + 3] |= (data[i] & 0xff) >> 7;
			alacData[i + 4] = (byte)((data[i] & 0xff) << 1);
		}

		return alacData;
	}
}