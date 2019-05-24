package com.acecorp.hxmsender.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class HxmClient {

    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in ;

    public void startConnection(String ip, int port) {
        try {
			clientSocket = new Socket(ip, port);
			out = new PrintWriter(clientSocket.getOutputStream(), true);
			in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			System.out.println("Failed to connect to server. Connection refused.");
		}
    }
    
    public void listen() {
    	Runnable r = new Runnable() {			
			@Override
			public void run() {
		    	try {
		    		String input;
					while((input = in.readLine()) != null) {
						System.out.println("[acecorp.hxmserver.response]=" + input);
					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					//e.printStackTrace();
				}
		    	System.out.println("lost server connection.");
			}
		};
		new Thread(r).start();
    }

    public void send(String msg) {
    	if(out != null) {
    		System.out.println("[acecorp.hxmclient.send]= " + msg);
        	out.println(msg);
        	out.flush();
    	}
    }

    public void stopConnection() {
        try {
			in .close();
	        out.close();
	        clientSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
}