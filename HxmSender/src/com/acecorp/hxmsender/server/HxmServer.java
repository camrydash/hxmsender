package com.acecorp.hxmsender.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class HxmServer {
	
    private ServerSocket serverSocket;
    private List<HxmClientHandler> clients;
    private int port;
    private boolean enable = true;
    private Object mStateLock = new Object();
    private Object mThreadLock = new Object();
    private HxmServerCallback callback;
    private boolean isRunning = false;
    private Thread mThread;
    
    public HxmServer(int port) {
    	this.port = port;
    	this.clients = new ArrayList<HxmServer.HxmClientHandler>();
    }
    
    public boolean isRunning() {
    	return isRunning;
    }
    
    boolean isportavailable(int port) {
    	Socket socket=null;
    	try
    	{
    		socket = new Socket("127.0.0.1", port);
    		System.out.println("port: " + port + " is not available.");
    		return false;
    	}
    	catch(IOException e) {
    		System.out.println("port: " + port + " is available.");
    		return true;
    	}
    	finally {
    		if(socket != null) {
    			try {
					socket.close();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
    		}
    	}
    }
    
    void init(final HxmServerCallback callback) {
    		try {
    			synchronized(mStateLock) {
    				if(!isportavailable(port)) {
    					enable = false;
    					return;
    				}
	    			serverSocket = new ServerSocket(port);
	    			if(serverSocket==null) {
	    				System.out.println("Failed to create server socket.");
	    				return;
	    			}
	    			if(!serverSocket.isClosed()) {
	    				isRunning = true;
	    				callback.onStarted();
	    			}
    			}
    	    	while(enable && !serverSocket.isClosed()) {
	        		Socket socket = serverSocket.accept();
	        		System.out.println("Connected client: " + socket.getRemoteSocketAddress());
	        		
	            	HxmClientHandler client = new HxmClientHandler(socket);
	            	client.start();
	            	clients.add(client);
	            	
	            	Thread.sleep(1000);
	    		}
    			
	    	} catch (IOException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
	    		System.out.println("IOException occured.");
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				//e.printStackTrace();
			}finally {
				isRunning =false;
	    		try {
	    			if(serverSocket != null)
	    				serverSocket.close();
				} catch (IOException e) {
					System.out.println("Error closing server socket.");
				}
	    		callback.onDisconnected();
			}
    	
    	System.out.println("Closing server socket.");
    }
    
    public void start(final HxmServerCallback callback) {
    	this.callback = callback;
    	Runnable r = new Runnable() {		
			@Override
			public void run() {
				init(callback);
			}
		};
		
		synchronized(mThreadLock) {
			mThread = new Thread(r);
			mThread.start();
		}
    }
    
    /** sends data to all client connected sockets
     * 
     * @param msg
     */
    public void send(String msg) {
    	synchronized(mStateLock) {
	    	if(this.enable && this.isRunning) {
		    	for(HxmClientHandler client: clients) {
	    			if(client.send(msg)) {
	    				callback.onDataSend(msg);
	    			}
		    	}
	    	} else {
	    		System.out.println("Server is disabled or not running");
	    	}
    	}
    }
    
    public void waitforstart() {
    	int retryAttempts = 10;
    	while(true && retryAttempts >= 0) {
    		if(isRunning) break;
    		try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    		finally {
    			retryAttempts--;
    		}
    	}
    	if(!isRunning)
    		System.out.println("Timeout in waiting for server start.");
    }

    /**
     * stops the server running thread
     */
    public void stop() {
        try {
        	synchronized(mThreadLock) {
        		if(isRunning) {
        			serverSocket.close();
    	            this.clients.clear();
        		}
        	}
        } catch (IOException e) {
			// TODO Auto-generated catch block
        	System.out.println("Error stopping server instance thread.");
        	if(serverSocket != null) {
        		try {
        			if(!serverSocket.isClosed())
        				serverSocket.close();
				} catch (IOException e1) {
					// TODO Auto-generated catch block
					System.out.println("Error closing server socket.");
				}
        	}
		}
        System.out.println("Stopped server.");
    }
    
    private static class HxmClientHandler {
        private Socket clientSocket;
        private PrintWriter out;
        private BufferedReader in;
        private boolean isDisconnected;
        
        public HxmClientHandler(Socket socket) throws IOException {
        	this.clientSocket = socket;
			this.out = new PrintWriter(clientSocket.getOutputStream(), true);
	        this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        }
        
        void helloworld() {
        	this.out.println("Connected to acecorp.hxmserver.");
        	this.out.println("Type 'quit' to leave.");
        	this.out.flush();
        }
        
        public boolean send(String msg) {
        	try {
        		if(this.isDisconnected) throw new Exception("Client disconnected");
        		this.out.println(msg);
        		this.out.flush();
        		return true;
        	}
        	catch(Exception e) {
        		System.out.println("Error transmitting data to client: " + clientSocket.getRemoteSocketAddress());
        	}
        	return false;
        }
        
        void init() throws IOException {
        	helloworld();
        	while(!isDisconnected) {       	
            	char[] buffer = new char[36];
            	int bytesRead = 0;
    	        try {
	    	        	
		        	bytesRead = in.read(buffer, 0, buffer.length);
		        	if(bytesRead <= 0) {
		        		System.out.println("zero bytes read");
		        		continue;
		        	}
		        	
		        	String resp = new String(buffer);
		        	System.out.println("Received message: " + resp);
		        	
		        	if(resp != null) {
		        		if(resp.equals("quit")) {
			        		System.out.println("Quitting server");
			        		break;
		        		}
		        	}
    	        }
    	        catch(IOException e) {
    	        	System.out.println("Error occured while reading data: " + e);
    	        	isDisconnected = true;
    	        }
        	}
        }
        
        public void start() {
        	Thread t = new Thread(new Runnable() {		
				@Override
				public void run() {
					try {
						init();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} finally {
			            try {
							in.close();
				            out.close();
				            clientSocket.close();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			});
        	t.start();
        }
    }
    
    public static void main(String[] args) throws IOException {
    		    	
    	final HxmServer server = new HxmServer(6652);  
    	final HxmServerCallback callback = new HxmServerCallback() {
	    	@Override
	    	public void onStarted() {
	    		// TODO Auto-generated method stub
	    		super.onStarted();
	    	}
	    	@Override
	    	public void onDisconnected() {
	    		// TODO Auto-generated method stub
	    		super.onStarted();
	    	}
	    	@Override
	    	public void onDataSend(String msg) {
	    		// TODO Auto-generated method stub
	    		super.onStarted();
	    	}
	    	
		};
		
		server.start(callback);  
		server.waitforstart();
		
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
//        HxmClient client = new HxmClient();
//        client.startConnection("127.0.0.1", 6652);
//        client.listen();      
//        client.send("hello server");

		
//		Scanner sc = new Scanner(System.in);
//		String input;
//		while((input = sc.nextLine()) != null) {
//			if(input.equals("quit")) break;
//		}
//		sc.close();  
    }
}