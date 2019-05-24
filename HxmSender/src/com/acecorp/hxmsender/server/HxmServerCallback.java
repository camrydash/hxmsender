package com.acecorp.hxmsender.server;

public abstract class HxmServerCallback {
	public void onStarted() {
	}
	public void onDisconnected() {
	}
	public void onDataSend(String data) {
	}
	public void onWarning() {
	}
}