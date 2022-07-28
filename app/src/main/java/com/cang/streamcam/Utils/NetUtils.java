package com.cang.streamcam.Utils;

//taken from
// https://github.com/rkp13/android-testhotspot/blob/master/TestHotspot/src/com/mady/wifi/api/wifiAddresses.java#L120

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;


public class NetUtils {

    /**
     * Method to Ping  IP Address
     *
     * @param onlyReachables Whether we care for the ones currently connected or not
     * @return ArrayList of IPs
     */
    public static ArrayList<String> getArpLiveIps(boolean onlyReachables) {
        BufferedReader bufRead = null;
        ArrayList<String> result = null;

        try {
            result = new ArrayList<String>();
            bufRead = new BufferedReader(new FileReader("/proc/net/arp"));
            String fileLine;
            while ((fileLine = bufRead.readLine()) != null) {


                String[] splitted = fileLine.split(" +");

                if ((splitted != null) && (splitted.length >= 4)) {

                    String mac = splitted[3];
                    if (mac.matches("..:..:..:..:..:..")) {
                        boolean isReachable = pingCmd(splitted[0]);/**
                         * Method to Ping  IP Address
                         * @return true if the IP address is reachable
                         */
                        if (!onlyReachables || isReachable) {
                            result.add(splitted[0]);
                        }
                    }
                }
            }
        } catch (Exception e) {

        } finally {
            try {
                bufRead.close();
            } catch (IOException e) {

            }
        }

        return result;
    }

    /**
     * Method to Ping  IP Address
     *
     * @param addr IP address you want to ping it
     * @return true if the IP address is reachable
     */
    public static boolean pingCmd(String addr){
        try {
            String ping = "ping  -c 1 -W 1 " + addr;
            Runtime run = Runtime.getRuntime();
            Process pro = run.exec(ping);
            try {
                pro.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            int exit = pro.exitValue();
            if (exit == 0) {
                return true;
            } else {
                //ip address is not reachable
                return false;
            }
        }
        catch (IOException e) {
        }
        return false;
    }
}
