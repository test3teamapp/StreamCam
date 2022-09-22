package com.cang.streamcam

import android.content.Context
import android.os.AsyncTask
import android.os.StrictMode
import android.util.Log
import com.cang.streamcam.Utils.NetUtils
import com.cang.streamcam.gps.LocationProvider
import java.io.DataOutputStream
import java.io.IOException
import java.net.*

public class ConnectionHandler private constructor() {

    private lateinit var connectedIpArray: ArrayList<String>
    private lateinit var broadcastIP: InetAddress
    private lateinit var imageClientIP: InetAddress
    private var udpSocket = DatagramSocket()
    private var tcpSocket = Socket()
    var conState = ConnectionState.ALL_CLOSED

    class SendTcpDataAsyncTask : AsyncTask<Any, Any, Any>() {

        private fun writeIntTo4BytesToBuffer(data: Int): ByteArray {
            var buffer = ByteArray(4)
            buffer[0] = (data shr 0).toByte()
            buffer[1] = (data shr 8).toByte()
            buffer[2] = (data shr 16).toByte()
            buffer[3] = (data shr 24).toByte()
            return buffer
        }

        override fun onPreExecute() {
            super.onPreExecute()
            // ...
        }

        override fun onPostExecute(result: Any?) {
            super.onPostExecute(result)
            // ...
        }

        override fun doInBackground(vararg p0: Any?): Any {

            var tcpSocket : Socket =  p0[0] as Socket
            var jpgbytes : ByteArray = p0[1] as ByteArray

            try {
                val networkWriter = DataOutputStream(tcpSocket!!.getOutputStream())
                // add the size in the first 16 bytes
                val newArray = writeIntTo4BytesToBuffer(jpgbytes.size) + jpgbytes
                networkWriter.write(newArray)
                networkWriter.flush()
                //println("fun sendTCP: ${jpgbytes.size}  bytes sent to: $imageClientIP:$TCP_PORT")
            } catch (e: IOException) {
                Log.e(TAG, "sendTCP: IOException: " + e.message)
                return 1
            }

            return 0
        }
    }

    public fun createSockets() {
        findIPs()
    }

    public fun destroySockets() {
        closeUDPSocket()
        closeTCPSocket()
        conState = ConnectionState.ALL_CLOSED
    }

    public fun isTcpConnected(): Boolean {
        return tcpSocket.isConnected && (!tcpSocket.isClosed)
    }

    private fun findIPs() {
        // get addresses of connected devices
        //connectedIpArray = NetUtils.getArpLiveIps(true)
        //Log.d(TAG, "Connected ips : $connectedIpArray")
        // get broadcast address net
        broadcastIP =
            NetUtils.getBroadcast(NetUtils.getIpAddress())//InetAddress.getByName("192.168.1.12")//NetUtils.getBroadcast(NetUtils.getIpAddress())
        Log.d(TAG, "Broadcast ip : $broadcastIP")
        // open udp socket
        openUDPSocket(broadcastIP)

    }

    private fun openUDPSocket(inetAddress: InetAddress) {
        try {
            //Open a port to send the package
            if (udpSocket == null) {
                udpSocket = DatagramSocket()
            }
            if (udpSocket != null) {
                Log.d(TAG, "openUDPSocket: socket not null")
                if (udpSocket.isClosed) {
                    Log.d(TAG, "openUDPSocket: socket was closed")
                    udpSocket = DatagramSocket()
                    udpSocket.broadcast = true
                } else if (!udpSocket.isConnected) {
                    Log.d(TAG, "openUDPSocket: socket was not connected")
                    udpSocket = DatagramSocket()
                    //udpSocket.connect(inetAddress, UDP_PORT)
                    udpSocket.broadcast = true
                }
                conState = ConnectionState.UDP_OPEN
                // get remote addresses of image clients
                sendUDP("tcp")
            }
        } catch (e: Exception) {
            Log.e(TAG, "openUDPSocket: Exception: " + e.message)
        }
    }

    private fun closeUDPSocket() {
        try {
            //Close the port
            if (!udpSocket.isClosed) {
                udpSocket.disconnect()
                udpSocket.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "closeUDPSocket: IOException: " + e.message)
        }
    }

    private fun openTCPSocket(inetAddress: InetAddress) {
        var retries = 0
        val maxRetries = 1
        conState = ConnectionState.TCP_CONNECTING

        try {
            //Open a port to send the package
            if (tcpSocket == null) {
                println("tcpSocket was null.")
                println("Opening tcp connection @ = $inetAddress")
                tcpSocket = Socket(inetAddress.hostAddress, TCP_PORT)
                tcpSocket.connect(InetSocketAddress(imageClientIP, TCP_PORT))

            }
            if (tcpSocket != null) {
                if (tcpSocket.isClosed) {
                    println("tcpSocket was closed.")
                    println("Opening tcp connection @ = $inetAddress")
                    tcpSocket = Socket(inetAddress.hostAddress, TCP_PORT)
                    tcpSocket.connect(InetSocketAddress(imageClientIP, TCP_PORT))

                } else if (!tcpSocket.isConnected) {
                    println("tcpSocket was open but not connected.")
                    tcpSocket.connect(InetSocketAddress(imageClientIP, TCP_PORT))

                } else if (tcpSocket.isInputShutdown || tcpSocket.isOutputShutdown) {
                    println("tcpSocket had input/output shutdown.")
                    println("Closing and opening again tcp connection @ = $inetAddress")
                    tcpSocket.close()
                    tcpSocket = Socket(inetAddress.hostAddress, TCP_PORT)
                    tcpSocket.connect(InetSocketAddress(imageClientIP, TCP_PORT))
                }

                conState = ConnectionState.TCP_CONNECTED
            }
        } catch (e: Exception) {
            Log.e(TAG, "openTCPSocket: Exception: " + e.message)
            // try once more
            if (retries < maxRetries) {
                retries++
                try {
                    if (e.message.toString().compareTo("Socket closed") == 0) {
                        println("tcpSocket was -- actually -- closed.")
                        println("Opening tcp connection @ = $inetAddress")
                        tcpSocket = Socket(inetAddress.hostAddress, TCP_PORT)
                        tcpSocket.connect(InetSocketAddress(imageClientIP, TCP_PORT))
                    }
                }catch (e: Exception) {
                    Log.e(TAG, "openTCPSocket: Exception: " + e.message)
                    conState = ConnectionState.TCP_DISCONNECTED
                }
            }
            conState = ConnectionState.TCP_DISCONNECTED
        }
    }

    private fun closeTCPSocket() {
        try {
            //Close the port
            if (!tcpSocket.isClosed) {
                tcpSocket.close()
            }
            conState = ConnectionState.TCP_DISCONNECTED
        } catch (e: Exception) {
            Log.e(TAG, "closeTCPSocket: IOException: " + e.message)
        }
    }

    /**

     */
    public fun sendTCP(jpgbytes: ByteArray) {

        if (!isTcpConnected()) {
            sendUDP("tcp")
            return
        }

        SendTcpDataAsyncTask().execute(tcpSocket, jpgbytes)
    /*
        // Hack Prevent crash (sending should be done using an async task)
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        try {
            val networkWriter = DataOutputStream(tcpSocket!!.getOutputStream())
            // add the size in the first 16 bytes
            val newArray = writeIntTo4BytesToBuffer(jpgbytes.size) + jpgbytes
            networkWriter.write(newArray)
            networkWriter.flush()
            //println("fun sendTCP: ${jpgbytes.size}  bytes sent to: $imageClientIP:$TCP_PORT")
        } catch (e: IOException) {
            Log.e(TAG, "sendTCP: IOException: " + e.message)
            closeTCPSocket();

        }
     */
    }

    fun sendUDP(messageStr: String) {
        // Hack Prevent crash (sending should be done using an async task)
        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        try {
            val sendData = messageStr.toByteArray()
            val sendPacket = DatagramPacket(sendData, sendData.size, broadcastIP, UDP_PORT)
            udpSocket.send(sendPacket)
            //println("fun sendBroadcast: \"$messageStr\" sent to: $broadcastIP:${UDP_PORT}")

            if (messageStr == "tcp" && (conState != ConnectionState.TCP_CONNECTION_REQUEST_SENT) && (conState != ConnectionState.TCP_CONNECTING)) {
                // wait for responces
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)
                udpSocket.soTimeout = 2000 // set timeout 2 secs
                conState = ConnectionState.TCP_CONNECTION_REQUEST_SENT
                try {
                    udpSocket.receive(packet)
                    val stringData = String(packet.data, packet.offset, packet.length)
                    println("UDP packet received = $stringData")
                    // check if the udp we received is about the tcp connection request
                    // first part of return string should be "tcp" (the request)
                    if (stringData.split(":")[0].compareTo("tcp") == 0) {
                        println("TCP details received")
                        // get remote tcp address (and port, but for now port is static/just for one client)
                        imageClientIP = InetAddress.getByName(stringData.split(":")[1])
                        // try to open tcp
                        openTCPSocket(imageClientIP)
                    }
                } catch (e: Exception) {
                    // timeout exception.
                    println("Timeout reached!!! " + e);
                    conState = ConnectionState.TCP_DISCONNECTED

                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "sendUDP: Exception: " + e.message)
            //TODO CAN UDP SOCKET GO DOWN ?????
            //TODO RE-OPEN IT ???
        }
    }

    enum class ConnectionState {
        ALL_CLOSED, UDP_OPEN, TCP_CONNECTION_REQUEST_SENT, TCP_CONNECTING, TCP_CONNECTED, TCP_DISCONNECTED
    }

    companion object {
        private const val TAG = "ConnectionHandler"
        private const val UDP_PORT = 20001
        private const val TCP_PORT = 20002

        private var singletonObject: ConnectionHandler? = null

        fun getInstance(): ConnectionHandler {
            if (singletonObject == null) {
                singletonObject = ConnectionHandler()
            }

            return singletonObject as ConnectionHandler
        }
    }
}