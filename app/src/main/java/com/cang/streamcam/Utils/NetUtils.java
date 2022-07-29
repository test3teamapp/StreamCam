package com.cang.streamcam.Utils;

//taken from
// https://github.com/rkp13/android-testhotspot/blob/master/TestHotspot/src/com/mady/wifi/api/wifiAddresses.java#L120

import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;


public class NetUtils {

    private static String TAG = "NetUtils";

    /**
     * Method to get IP of device (either wifi/eth0 or as Access Point)
     *
     * @return InetAddress device IP
     */

    public static InetAddress getIpAddress() {
        InetAddress inetAddress = null;
        InetAddress myAddr = null;

        try {
            for (Enumeration<NetworkInterface> networkInterface = NetworkInterface.getNetworkInterfaces();
                 networkInterface.hasMoreElements();) {

                NetworkInterface singleInterface = networkInterface.nextElement();

                for (Enumeration<InetAddress> IpAddresses = singleInterface.getInetAddresses(); IpAddresses
                        .hasMoreElements();) {
                    inetAddress = IpAddresses.nextElement();

                    if (!inetAddress.isLoopbackAddress() && (singleInterface.getDisplayName()
                            .contains("wlan0") ||
                            singleInterface.getDisplayName().contains("eth0") ||
                            singleInterface.getDisplayName().contains("ap0"))) {

                        myAddr = inetAddress;
                    }
                }
            }

        } catch (SocketException ex) {
            Log.e(TAG, ex.toString());
        }
        return myAddr;
    }

    /**
     * Method to get the broadcast address for the net we are connected
     *
     * @return InetAddress broadcast IP
     */

    public static InetAddress getBroadcast(InetAddress inetAddr) {

        NetworkInterface temp;
        InetAddress iAddr = null;
        try {
            temp = NetworkInterface.getByInetAddress(inetAddr);
            List<InterfaceAddress> addresses = temp.getInterfaceAddresses();

            for (InterfaceAddress inetAddress: addresses)

                iAddr = inetAddress.getBroadcast();
            Log.d(TAG, "iAddr=" + iAddr);
            return iAddr;

        } catch (SocketException e) {

            e.printStackTrace();
            Log.d(TAG, "getBroadcast" + e.getMessage());
        }
        return null;
    }


    /**
     * Method to getIPs of devices connected to mobile hotspot
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
                if (bufRead != null) {
                    bufRead.close();
                }
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



