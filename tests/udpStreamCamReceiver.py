import socket
import cv2
#import io
import numpy as np
import threading
import logging
#from turbojpeg import TurboJPEG, TJPF_GRAY, TJSAMP_GRAY

localIP     = "192.168.1.11" # receive UDP broadcast by using '' as address
localUDPPort   = 20001
localTCPPort = 20002
bufferSize  = 96666
latestReceivedSizeOfImage = 0

msgFromServer       = "Hello UDP Client"
bytesToSend         = str.encode(msgFromServer)

def thread_UDPServer(ipaddr, port):

    logging.info("UDP Thread @ %s:%s starting", ipaddr, port)

    # Create a datagram socket (NOT GREAT FOR RECEIVING IMAGES)
    UDPServerSocket = socket.socket(family=socket.AF_INET, type=socket.SOCK_DGRAM)

    # Bind to address and ip
    UDPServerSocket.bind(('', localUDPPort)) # using '' for broacast address

    print("UDP server up and listening")
    # Listen for incoming datagrams

    while(True):
        bytesAddressPair = UDPServerSocket.recvfrom(bufferSize)
        message = bytesAddressPair[0]
        address = bytesAddressPair[1]

        messageLen = len(message)
        clientMsg = "UDP Message :{}".format(message)
        #clientIP  = "UDP Client IP Address:{}".format(address)

        #print(clientMsg)
        #print(clientIP)
        messageStr = message.decode('utf-8','ingore')
        if (messageStr == "tcp"):
            print(clientMsg)
            # Sending a reply to client
            responceMsg = f"{localIP}:{localTCPPort}"
            UDPServerSocket.sendto(str.encode(responceMsg), address)
        if (messageStr.startswith('size:')):
            parts = messageStr.split(":")
            latestReceivedSizeOfImage = int(parts[1])

    UDPServerSocket.close()
    logging.info("UDP Thread : finishing")


def recvSome(sock, count):
    buf = b''
    while count:
        newbuf = sock.recv(count)
        if not newbuf: return b'\x00'
        buf += newbuf
        count -= len(newbuf)
    return buf

def recvall(sock):
    buf = b''
    while True:
        newbuf = sock.recv(1024)
        if not newbuf:
            break
        buf += newbuf

    return buf

def thread_TCPServer(ipaddr, port):

    logging.info("TCP Thread @ %s:%s starting", ipaddr, port)

    # If you run an interactive ipython session, and want to use highgui windows, do cv2.startWindowThread() first.
    # In detail: HighGUI is a simplified interface to display images and video from OpenCV code.
    cv2.startWindowThread()
    cv2.namedWindow("preview")
    cv2.moveWindow("preview", 20,20)

    # Use default library installation
    #jpeg = TurboJPEG()

    # TCP socket
    TCPServerSocket = socket.socket(family=socket.AF_INET, type=socket.SOCK_STREAM)
    TCPServerSocket.bind(('', localTCPPort))
    # wait
    TCPServerSocket.listen()
    # accepts TCP connection
    print("TCP server up and listening")
    TCPconnection, addr = TCPServerSocket.accept()
    print(f"TCP server accepted connection from {addr}")

    while(True):
        try:
            lengthAsBytes = recvSome(TCPconnection,4)
            intLength = int.from_bytes(lengthAsBytes, "little")
            print(f"image size in bytes: {intLength}")
            if (intLength > 0):
                imageData = recvSome(TCPconnection,intLength)
                
                if (len(imageData) > 1000): # arbitrary logical number for a jpg image of resonalble size
                    #display image
                    #buffer = io.BytesIO(message)
                    #buffer.seek(0)
                    #inp = np.asarray(bytearray(message), dtype=np.uint8)
                    i = cv2.imdecode(np.frombuffer(imageData, dtype=np.uint8), cv2.IMREAD_COLOR)
                    #i = jpeg.decode(message)
                    cv2.imshow("preview", i)
                    #cv2.waitKey(0)
        except BaseException as err:
            print(f"Unexpected {err}, {type(err)}")

    TCPServerSocket.close()
    logging.info("TCP Thread : finishing")


logging.info("Main    : before creating threads")
udpServerThread = threading.Thread(target=thread_UDPServer, args=('',localUDPPort))
tcpServerThread = threading.Thread(target=thread_TCPServer, args=(localIP,localTCPPort))
logging.info("Main    : before running threads")
udpServerThread.start()
tcpServerThread.start()
udpServerThread.join()
tcpServerThread.join()

