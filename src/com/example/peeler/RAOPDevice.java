package com.example.peeler;

import android.util.Log;

import java.io.*;
import java.net.*;

import java.lang.*;
import java.nio.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.regex.*;
import java.math.BigInteger;

/* TODO clock syncing should obviously be done in the AirPlayer class */

class RAOPDevice {
	static public final int samplesPerSecond = 44100;
	static public final int samplesPerPacket = 4096;

	static final String optionsChallenge = "Apple-Challenge: X/GmLMLuFvgWf8Y1bQuUug\r\n";

	static final String announceContent = "v=0\r\n" +
		"o=iTunes %s 0 IN IP4 %s\r\n" +
		"s=iTunes\r\n" +
		"c=IN IP4 %s\r\n" +
		"t=0 0\r\n" +
		"m=audio 0 RTP/AVP 96\r\n" +
		"a=rtpmap:96 AppleLossless\r\n" +
		"a=fmtp:96 " + samplesPerPacket + " 0 16 40 10 14 2 255 0 0 " + samplesPerSecond + "\r\n" +
		"a=rsaaeskey:%s\r\n" +
		"a=aesiv:%s\r\n";

	public LockableBlockingQueue<ByteBuffer> queue;

	private InetAddress dest;
	private String name;
	private String type;
	private RTSP rtsp;
	private Socket tcpServer;
	private DatagramSocket udpServer, control, timing;
	private int streamingPort, controlPort, timingPort;
	private int localControlPort = 6001;
	private int localTimingPort = 6002;
	private ScheduledFuture streamingTask;
	private Thread controlThread, timingThread;
	private String localUrl;
	private String connId = new BigInteger(32, new Random()).toString();
	private String session = null;
	private int seq = new BigInteger(16, new Random()).intValue(); // XXX is 16 right?
	private int numAudioPackets = 0;
	private long rtpTime;
	private boolean streaming = false;
	private int volume = 50;
	private boolean firstPacket = true;
	private InputStream input;
	private int version = 1;

	RAOPDevice(InetAddress dest, String name, String type) throws IOException, RTSPException, UnsupportedRAOPDevice {
		this.dest = dest;
		this.name = name;

		if (type.equals("_airport")) type = "Airport";
		else if (type.equals("_appletv")) type = "AppleTV";
		this.type = type;

		// confirm it's an airport express. is this really necessary since we don't care what we're streaming to?
		try { rtsp = new RTSP(dest, "*"); }
		catch (IOException e) { throw new UnsupportedRAOPDevice(); }

		try {
			rtsp.options(optionsChallenge);
			localUrl = "rtsp://" + rtsp.getLocalAddress().getHostAddress() + "/" + connId;
			rtsp.close();
		} catch (RTSPException e) { throw new UnsupportedRAOPDevice(); }
	}
	
	public boolean setup(byte[] encAESKey, byte[] aesIV) {
		try {
			rtsp = new RTSP(dest, localUrl);

			rtsp.announce(null, String.format(
				announceContent,
				connId,
				rtsp.getLocalAddress().getHostAddress(),
				dest.getHostAddress(),
				new String(Base64.encode(encAESKey)),
				new String(Base64.encode(aesIV))
			));

			if (version == 1) {
				tcpServer = new Socket();
				rtsp.setup("Transport: RTP/AVP/TCP;unicast;interleaved=0-1;mode=record;control_port=0;timing_port=0\r\n");
			} else {
				udpServer = new DatagramSocket();
				try {
					control = new DatagramSocket(localControlPort);
				} catch (SocketException e) {
					control = new DatagramSocket();
					localControlPort = control.getLocalPort();
				}
				try {
					timing = new DatagramSocket(localTimingPort);
				} catch (SocketException e) {
					timing = new DatagramSocket();
					localTimingPort = timing.getLocalPort();
				}
				rtsp.setup("Transport: RTP/AVP/UDP;unicast;interleaved=0-1;mode=record;control_port=" +
					localControlPort + ";timing_port=" + localTimingPort + "\r\n");
			}

			streamingPort = matchInt(rtsp.response, "server_port=(\\d+)");
			controlPort = matchInt(rtsp.response, "control_port=(\\d+)");
			timingPort = matchInt(rtsp.response, "(?i)timing_port=(\\d+)");
			session = matchString(rtsp.response, "(?i)\n(session:.+)\r?\n");
			if (session == null)
				throw new RAOPException("Setup failed: Session ID missing");
			session += "\r\n";

			newQueue();
		} catch (Exception e) {
		Log.e("peeler", "setup: " + e);
			return false;
		}
		return true;
	}

	static private int matchInt(String str, String match) {
		Pattern p = Pattern.compile(match);
		Matcher m = p.matcher(str);
		if (m.find()) {
			try {
				return new java.lang.Integer(m.group(1)).intValue();
			} catch (Exception e) {	}
		}
		return 0;
	}

	static private String matchString(String str, String match) {
		Pattern p = Pattern.compile(match);
		Matcher m = p.matcher(str);
		if (m.find()) {
			try {
				return m.group(1);
			} catch (Exception e) { }
		}
		return null;
	}

	private void newQueue() {
		queue = new LockableBlockingQueue<ByteBuffer>((int)(samplesPerSecond * 2 / samplesPerPacket) + 1);
	}

	public void start() throws IOException, RTSPException {
		if (version >= 2) startTimeSync();

		rtpTime = System.currentTimeMillis() / 1000 - 1000000000;
		rtsp.record(session + "Range: npt=0-\r\n" + "RTP-Info: seq=" + seq + ";rtptime=" + rtpTime + "\r\n");

		if (version == 1)
				tcpServer.connect(new InetSocketAddress(dest, streamingPort));
		else {
			try {
				control.connect(dest, controlPort);
			} catch (Exception e ) {
				System.out.println("control connect failed: " + e);
			}

			try {
				udpServer.connect(dest, streamingPort);
			} catch (Exception e ) {
				System.out.println("server connect failed: " + e);
			}

			byte[] streamId = new byte[4];
			new Random().nextBytes(streamId);
			System.arraycopy(streamId, 0, audioPacketHeaderv2, 8, 4);

			sendAudioSync();
		}

		startStream();
	}

	private void startTimeSync() throws SocketException {
		try {
			timing.connect(dest, timingPort);
		} catch (Exception e ) {
			System.out.println("timing connect failed");
		}
		
		sessionStart = BigInteger.valueOf(System.nanoTime());

		timingThread = new Thread(new Runnable() {
			public void run() {
				byte[] data = new byte[32];
				DatagramPacket packet = new DatagramPacket(data, 32);

				byte[] replyData = new byte[32];
				replyData[0] = (byte)0x80;
				replyData[1] = (byte)0xd3;
				replyData[3] = 0x07;
				DatagramPacket replyPacket = new DatagramPacket(replyData, 32);

				while (true) {
					try {
						timing.receive(packet);
						byte[] receiveTime = currentNTPTime();
						if (data[0] != (byte)0x80 || data[1] != (byte)0xd2) continue;

						System.arraycopy(data, 24, replyData, 8, 8);
						System.arraycopy(receiveTime, 0, replyData, 16, 8);
						System.arraycopy(currentNTPTime(), 0, replyData, 24, 8);
						timing.send(replyPacket);
					} catch (IOException e) {
						System.out.println("timing send/receive failed: " + e);
						continue;
					}
				}
			}
		});
		timingThread.start();
	}

	static final byte[] deltaBytes = {
		(byte)0x83, (byte)0xaa, 0x7e, (byte)0x80, // time since 01/01/1900 in 200 picosecs
		0x00, 0x00, 0x00, 0x00
	};
	static final BigInteger ntpUnixTimeDelta = new BigInteger(1, deltaBytes);
	static final BigInteger ntpScale = BigInteger.valueOf(0x10c6); // XXX assumes nanoTime() returns microsecs
	static final BigInteger byteMask = BigInteger.valueOf(0xff);
	private BigInteger sessionStart;

	private byte[] currentNTPTime() {
		BigInteger time = BigInteger.valueOf(System.nanoTime());
		time = time.subtract(sessionStart).multiply(ntpScale).add(ntpUnixTimeDelta);
		byte[] data = new byte[8];
		data[0] = (byte)time.shiftRight(56).and(byteMask).intValue();
		data[1] = (byte)time.shiftRight(48).and(byteMask).intValue();
		data[2] = (byte)time.shiftRight(40).and(byteMask).intValue();
		data[3] = (byte)time.shiftRight(32).and(byteMask).intValue();
		data[4] = (byte)time.shiftRight(24).and(byteMask).intValue();
		data[5] = (byte)time.shiftRight(16).and(byteMask).intValue();
		data[6] = (byte)time.shiftRight(8).and(byteMask).intValue();
		data[7] = (byte)time.and(byteMask).intValue();
		return data;
	}

	static byte[] audioPacketHeaderv1 = {
        0x24, 0x00, 0x00, 0x00,
        (byte)0xF0, (byte)0xFF, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00
	};
	static byte[] audioPacketHeaderv2 = {
		(byte)0x80, (byte)0x60, // type (audio data)
		(byte)0x00, (byte)0x00, // rtp sequence num
		(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, // rtp timestamp
		(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00 // stream id? [randomised at start of stream, constant til paused]
	};

	static final byte[] alacHeader = { (byte)0x00, (byte)0x00, (byte)0x00 };
	private byte[] audioBuf = new byte[samplesPerPacket << 2]; // num samples x 16 bit sample x 2 channels
	private Random audioRand = new Random();

	private void startStream() {
		if (streaming) return;

		streamingTask = new ScheduledThreadPoolExecutor(1).scheduleAtFixedRate(new Runnable() {
			public void run() {
				streaming = true;
				
				try {
					OutputStream out = null;
					if (version == 1) out = tcpServer.getOutputStream();

					while (streaming) {
						ByteBuffer buf;
						buf = queue.take();
					Log.e("peeler", "got audio data, " + buf.array().length + " bytes");

						byte[] header = (version == 1 ? audioPacketHeaderv1 : audioPacketHeaderv2);

						int len = header.length + buf.array().length;
						byte[] data = new byte[len];
						System.arraycopy(header, 0, data, 0, header.length);
						System.arraycopy(buf.array(), 0, data, header.length, buf.array().length);

						if (version == 1) {
							data[2] = (byte)((len >> 8) & 0xff);
							data[3] = (byte)(len & 0xff);

							out.write(data);
					Log.e("peeler", "data written");
						} else {
							if (++numAudioPackets % 126 == 0) {
								numAudioPackets = 0;
								sendAudioSync();
							}

							if (firstPacket) {
								data[1] = (byte)0xe0;
								firstPacket = false;
							}
							data[2] = (byte)((seq >> 8) & 0xff);
							data[3] = (byte)(seq & 0xff);
							data[4] = (byte)((rtpTime >> 24) & 0xff);
							data[5] = (byte)((rtpTime >> 16) & 0xff);
							data[6] = (byte)((rtpTime >> 8) & 0xff);
							data[7] = (byte)(rtpTime & 0xff);

							udpServer.send(new DatagramPacket(data, data.length));
						}

						seq = (seq + 1) & 0xffff;
						rtpTime = (rtpTime + (buf.capacity() >> 2)) & 0xffffffff;
					}
				} catch (Exception e) {
				Log.e("peeler", "streaming error: " + e);
					streaming = false;
				}
			}
		}, 0, (long)(1 / (((float)samplesPerSecond) / samplesPerPacket) * 1000000), TimeUnit.MICROSECONDS);
	}

	private byte[] audioSyncData = {
		(byte)0x80, (byte)0xd4, // type (audio sync)
		(byte)0x00, (byte)0x07, // unknown
		(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, // rtp timestamp of currently playing sample
		(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, // ntp timestamp
		(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
		(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00 // rtp timestamp of next sample to be sent
	};
	private DatagramPacket audioSyncPacket = new DatagramPacket(audioSyncData, 20);

	private void sendAudioSync() throws IOException {
		audioSyncData[0] = (byte)(firstPacket ? 0x90 : 0x80);
		long prevRtpTime = rtpTime - 88200; // references negative time to start with, effectively leading to a 2-sec buffer
		audioSyncData[4] = (byte)((prevRtpTime >> 24) & 0xff);
		audioSyncData[5] = (byte)((prevRtpTime >> 16) & 0xff);
		audioSyncData[6] = (byte)((prevRtpTime >> 8) & 0xff);
		audioSyncData[7] = (byte)(rtpTime & 0xff);
		System.arraycopy(currentNTPTime(), 0, audioSyncData, 8, 8);
		audioSyncData[16] = (byte)((rtpTime >> 24) & 0xff);
		audioSyncData[17] = (byte)((rtpTime >> 16) & 0xff);
		audioSyncData[18] = (byte)((rtpTime >> 8) & 0xff);
		audioSyncData[19] = (byte)(rtpTime & 0xff);
		control.send(audioSyncPacket);
	}

	public int getVolume() {
		return this.volume;
	}

	public void setVolume(int volume) throws IOException, RTSPException {
		this.volume = volume;
		sendVolume();
	}

	private int readVolume(String vol) {
		return Double.valueOf(Math.pow(10.0, (Double.valueOf(vol) / 20)) * 100).intValue();
	}

	private void sendVolume() throws IOException, RTSPException {
		rtsp.setParameter(session, "volume: " + (volume == 0 ? -144 : 20 * Math.log10(volume / 100.0)) + "\r\n");
	}

	public void play() {
		startStream();
	}

	public void pause() throws IOException, RTSPException {
		if (!streaming) return;
		streamingTask.cancel(true);
		rtsp.flush(session + "RTP-Info: seq=" + seq + ";rtptime=" + rtpTime + "\r\n");
		newQueue();
	}

	public void stop() throws IOException, RTSPException {
		pause();
	}

	public void close() {
		try {
			if (streaming) {
				try { rtsp.teardown(session); }
				catch (RTSPException e) { /* doesn't matter */ }
				streamingTask.cancel(true);
				control.close();
				timing.close();
			}
			rtsp.close();
		} catch (IOException e) { /* doesn't matter */ }
	}
	
	public String toString() {
		return name + " (" + type + ")";
	}
}

class UnsupportedRAOPDevice extends Exception {
	UnsupportedRAOPDevice() {
		super("Device could not be connected to or is not a supported RAOP device");
	}
}

class RAOPDeviceNotSetup extends Exception {
	RAOPDeviceNotSetup() {
		super("Device has not been setup");
	}
}

class RAOPException extends Exception {
	RAOPException(String message) {
		super(message);
	}
}

class LockableBlockingQueue<E> extends ArrayBlockingQueue<E> {
	ReentrantLock lock = new ReentrantLock();

	LockableBlockingQueue(int capacity) {
		super(capacity);
	}

	public E take() {
		while (true) {
			E e = null;

			lock.lock();
			try { e = poll(); }
			finally { lock.unlock(); }

			if (e != null) return e;

			Thread.yield();
		}
	}

	public void put(E e) {
		while (true) {
			boolean res = false;

			lock.lock();
			try { res = offer(e); }
			finally { lock.unlock(); }

			if (res) return;

			Thread.yield();
		}
	}

	public void lock() {
		lock.lock();
	}

	public void unlock() {
		lock.unlock();
	}
}

class Base64 {
	static private final byte[] mappings = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".getBytes();

	static public byte[] encode(byte[] data) {
		int paddingCount = (3 - (data.length % 3)) % 3;
		byte[] padded = new byte[data.length + paddingCount];
		System.arraycopy(data, 0, padded, 0, data.length);

		byte[] encoded = new byte[(padded.length / 3) * 4];
		int i = 0, j = 0;
		while (i < data.length) {
			int packed = (padded[i++] & 0xff) << 16;
			packed |= (padded[i++] & 0xff) << 8;
			packed |= padded[i++] & 0xff;
			encoded[j++] = mappings[(packed >> 18) & 0x3f];
			encoded[j++] = mappings[(packed >> 12) & 0x3f];
			encoded[j++] = mappings[(packed >> 6) & 0x3f];
			encoded[j++] = mappings[packed & 0x3f];
		}

		while (paddingCount > 0)
			encoded[encoded.length - paddingCount--] = '=';

		return encoded;
	}
}