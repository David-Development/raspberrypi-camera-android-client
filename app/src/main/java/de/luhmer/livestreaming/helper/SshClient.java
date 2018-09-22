package de.luhmer.livestreaming.helper;

import android.os.AsyncTask;
import android.util.Log;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by david on 06.07.17.
 */

public class SshClient extends AsyncTask<Void, Void, Exception> {

    private static final String TAG = SshClient.class.getCanonicalName();

    private String username;
    private String password;
    private String ip;
    private Callback callback;
    private String command;
    private int timeout;

    public SshClient(String username, String password, String ip, Callback callback, String command, int timeout) {
        this.username = username;
        this.password = password;
        this.ip = ip;
        this.callback = callback;
        this.command = command;
        this.timeout = timeout;
    }

    @Override
    protected Exception doInBackground(Void... params) {
        try {
            JSch jsch = new JSch();

            Session session = jsch.getSession(username, ip, 22);
            session.setPassword(password);

            //session.setUserInfo(ui);

            // It must not be recommended, but if you want to skip host-key check,
            // invoke following,
            session.setConfig("StrictHostKeyChecking", "no");

            //session.connect();
            session.connect(30000);   // making a connection with timeout.

            //Channel channel = session.openChannel("shell");
            Channel channel = session.openChannel("exec");

            //String command = "shutdown -h now";
            ((ChannelExec)channel).setCommand("sudo -S -p '' " + command);

            InputStream in = channel.getInputStream();
            OutputStream out=channel.getOutputStream();
            ((ChannelExec)channel).setErrStream(System.err);

            channel.connect(30000);

            out.write((password+"\n").getBytes());
            out.flush();

            int counter = 0;

            byte[] tmp = new byte[1024];
            while(counter < timeout) { // Wait max. 15 Seconds
                while(in.available() > 0){
                    int i=in.read(tmp, 0, 1024);
                    if(i<0)break;
                    System.out.print(new String(tmp, 0, i));
                }
                if(channel.isClosed()){
                    System.out.println("exit-status: "+channel.getExitStatus());
                    break;
                }
                try {
                    Thread.sleep(1000);
                } catch(Exception ee){
                    ee.printStackTrace();
                    return ee;
                }

                counter++;
            }
            channel.disconnect();
            session.disconnect();

        } catch(Exception e) {
            e.printStackTrace();
            Log.d(TAG, e.getMessage());
            return e;
        }
        return null;
    }

    @Override
    protected void onPostExecute(Exception e) {
        callback.onFinish(e);
        super.onPostExecute(e);
    }

    public interface Callback {
        void onFinish(Exception exception);
    }
}
