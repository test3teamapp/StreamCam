
import socket
from time import time
import cv2
#import io
import numpy as np
import threading
import logging
#from turbojpeg import TurboJPEG, TJPF_GRAY, TJSAMP_GRAY
from multiprocessing import Process
import time

localIP = "10.132.0.2"  # receive UDP broadcast by using '' as address
localUDPPort = 20001
localTCPPort = 20002
bufferSize = 96666
latestReceivedSizeOfImage = 0

msgFromServer = "Hello UDP Client"
bytesToSend = str.encode(msgFromServer)


# https://stackoverflow.com/questions/48024720/python-how-to-check-if-socket-is-still-connected
def is_socket_closed(sock: socket.socket) -> bool:
    try:
        # this will try to read bytes without blocking and also without removing them from buffer (peek only)
        data = sock.recv(16, socket.MSG_DONTWAIT | socket.MSG_PEEK)
        if len(data) == 0:
            return True
    except BlockingIOError:
        return False  # socket is open and reading from it would block
    except ConnectionResetError:
        return True  # socket was closed for some other reason
    except Exception as e:
        logging.exception(
            "unexpected exception when checking if a socket is closed")
        return False
    return False


#### Alternative using a class to controll TCP connection ######

class TCPConnectionHandler:
    """Class for spawning , controlling a process for openning a TCP connection for receiving images """

    # variables here are common to all instances of the class #

    def recvSome(sock, count):
        buf = b''
        while count:
            newbuf = sock.recv(count)
            if not newbuf:
                return b'\x00'
            buf += newbuf
            count -= len(newbuf)
        return buf

    def process_TCPServer(self, ipaddr, port):

        logging.info("TCP process @ %s:%s starting", ipaddr, port)

        # If you run an interactive ipython session, and want to use highgui windows, do cv2.startWindowThread() first.
        # In detail: HighGUI is a simplified interface to display images and video from OpenCV code.
        cv2.startWindowThread()
        cv2.namedWindow("preview")
        cv2.moveWindow("preview", 20, 20)

        # Use default library installation
        #jpeg = TurboJPEG()

        # TCP socket
        TCPServerSocket = socket.socket(
            family=socket.AF_INET, type=socket.SOCK_STREAM)
        TCPServerSocket.bind(('', localTCPPort))
        # wait
        TCPServerSocket.listen()
        # accepts TCP connection
        print("TCP server up and listening")
        TCPconnection, addr = TCPServerSocket.accept()
        print(f"TCP server accepted connection from {addr}")

        while(self.startTCP):
            try:
                lengthAsBytes = recvSome(TCPconnection, 4)
                intLength = int.from_bytes(lengthAsBytes, "little")
                print(f"image size in bytes: {intLength}")
                if (intLength > 0):
                    imageData = recvSome(TCPconnection, intLength)

                    # arbitrary logical number for a jpg image of resonalble size
                    if (len(imageData) > 1000):
                        # display image
                        #buffer = io.BytesIO(message)
                        # buffer.seek(0)
                        #inp = np.asarray(bytearray(message), dtype=np.uint8)
                        i = cv2.imdecode(np.frombuffer(
                            imageData, dtype=np.uint8), cv2.IMREAD_COLOR)
                        #i = jpeg.decode(message)
                        cv2.imshow("preview", i)
                        # cv2.waitKey(0)
                else:
                    # if length of image buffer is 0, check if connection is closed
                    if (is_socket_closed(TCPconnection)):
                        self.startTCP = False
                        break

            except BaseException as err:
                print(f"Unexpected {err}, {type(err)}")

        TCPServerSocket.close()
        logging.info("TCP process : finishing")
        print("TCP process : finishing")

    def create_TCPProcess(self):
        self.startTCP = True
        self.tcpProcess = Process(
            target=self.process_TCPServer, args=(localIP, localTCPPort,))
        self.tcpProcess.start()

    def terminate_TCPProcess(self):
        self.startTCP = False
        self.tcpProcess.terminate()
        self.tcpProcess.kill()

    def __init__(self):
        self.startTCP = False
        self.tcpProcess = Process(
            target=self.process_TCPServer, args=(localIP, localTCPPort,))


def thread_UDPServer(ipaddr, port):

    logging.info("UDP Thread @ %s:%s starting", ipaddr, port)

    myTCPConnectionHandler = TCPConnectionHandler()

    # Create a datagram socket (NOT GREAT FOR RECEIVING IMAGES)
    UDPServerSocket = socket.socket(
        family=socket.AF_INET, type=socket.SOCK_DGRAM)

    # Bind to address and ip
    UDPServerSocket.bind(('', localUDPPort))  # using '' for broacast address

    print("UDP server up and listening")
    # Listen for incoming datagrams

    while(True):
        bytesAddressPair = UDPServerSocket.recvfrom(bufferSize)
        message = bytesAddressPair[0]
        address = bytesAddressPair[1]

        messageLen = len(message)
        clientMsg = "UDP Message :{}".format(message)
        #clientIP  = "UDP Client IP Address:{}".format(address)

        # print(clientMsg)
        # print(clientIP)
        messageStr = message.decode('utf-8', 'ingore')
        if (messageStr == "tcp"):
            print(f"User requested TCP details for connection")
            # the app wants to connect. Make sure there is no TCP process running and blocking the port
            if (myTCPConnectionHandler.startTCP):
                print(
                    f"TCP server was running. Shutting it down... {myTCPConnectionHandler.startTCP}")
                myTCPConnectionHandler.terminate_TCPProcess()
                # setting startTCP to False should exit the while loop of TCP server
                # The process should then join and end
                # wait for a second untill all this is done
                # time.sleep(1) # Sleep for 1 second
                print(
                    f"Is TCP server running : {myTCPConnectionHandler.startTCP}")
                # start a new TCP process
                print(f"Starting a new TCP server process")
                myTCPConnectionHandler.create_TCPProcess()
            else:
                print(f"No TCP server was running. Starting a new process")
                myTCPConnectionHandler.create_TCPProcess()
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
        if not newbuf:
            return b'\x00'
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
    cv2.moveWindow("preview", 20, 20)

    # Use default library installation
    #jpeg = TurboJPEG()

    # TCP socket
    TCPServerSocket = socket.socket(
        family=socket.AF_INET, type=socket.SOCK_STREAM)
    TCPServerSocket.bind(('', localTCPPort))
    # wait
    TCPServerSocket.listen()
    # accepts TCP connection
    print("TCP server up and listening")
    TCPconnection, addr = TCPServerSocket.accept()
    print(f"TCP server accepted connection from {addr}")

    while(True):
        try:
            lengthAsBytes = recvSome(TCPconnection, 4)
            intLength = int.from_bytes(lengthAsBytes, "little")
            print(f"image size in bytes: {intLength}")
            if (intLength > 0):
                imageData = recvSome(TCPconnection, intLength)

                if (len(imageData) > 1000):  # arbitrary logical number for a jpg image of resonalble size
                    # display image
                    #buffer = io.BytesIO(message)
                    # buffer.seek(0)
                    #inp = np.asarray(bytearray(message), dtype=np.uint8)
                    i = cv2.imdecode(np.frombuffer(
                        imageData, dtype=np.uint8), cv2.IMREAD_COLOR)
                    #i = jpeg.decode(message)
                    cv2.imshow("preview", i)
                    # cv2.waitKey(0)
        except BaseException as err:
            print(f"Unexpected {err}, {type(err)}")

    TCPServerSocket.close()
    logging.info("TCP Thread : finishing")


##### our entry point of the program #######
if __name__ == '__main__':

    logging.info("Main    : before creating threads / processes")
    udpServerThread = threading.Thread(target=thread_UDPServer, args=(
        '', localUDPPort))  # using '' for ip adress to receive broadcast packets
    #tcpServerThread = threading.Thread(target=thread_TCPServer, args=(localIP,localTCPPort))
    logging.info("Main    : before running threads")
    udpServerThread.start() # The udpServerThread will spawn a TCP server process when necessary
    # tcpServerThread.start()
    udpServerThread.join()
    # tcpServerThread.join()
