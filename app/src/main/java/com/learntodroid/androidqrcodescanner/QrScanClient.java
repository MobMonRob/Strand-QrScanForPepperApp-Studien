package com.learntodroid.androidqrcodescanner;

import android.os.AsyncTask;

import java.net.*;
import java.io.*;
import java.util.Date;

/**
 * This program demonstrates a simple TCP/IP socket client.
 *
 * @author www.codejava.net
 */
public class QrScanClient<vacStatus> extends AsyncTask<Void,Void,Object> {

    private boolean vacStatus;

    public QrScanClient(boolean vacStatus) {
        this.vacStatus = vacStatus;
    }

    @Override
    protected Object doInBackground(Void... voids) {
        String hostname = "172.16.17.23";
        int port = 1755;

        try (Socket socket = new Socket(hostname, port)) {

            OutputStream output = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(output, true);

            writer.println(vacStatus);
            output.close();
            writer.close();
        } catch (UnknownHostException ex) {

            System.out.println("Server not found: " + ex.getMessage());

        } catch (IOException ex) {

            System.out.println("I/O error: " + ex.getMessage());
        }
        return null;
    }

}