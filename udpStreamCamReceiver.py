import socket
import cv2
#import io
import numpy as np
from turbojpeg import TurboJPEG, TJPF_GRAY, TJSAMP_GRAY

localIP     = "192.168.1.12" # receive broadcast by usin '' as address 
localPort   = 20001
bufferSize  = 96666

msgFromServer       = "Hello UDP Client"
bytesToSend         = str.encode(msgFromServer)

# Create a datagram socket
UDPServerSocket = socket.socket(family=socket.AF_INET, type=socket.SOCK_DGRAM)

# Bind to address and ip
UDPServerSocket.bind(('', localPort))

print("UDP server up and listening")
# Listen for incoming datagrams

while(True):

    bytesAddressPair = UDPServerSocket.recvfrom(bufferSize)
    message = bytesAddressPair[0]
    address = bytesAddressPair[1]

    messageLen = len(message)
    clientMsg = "Message Size:{}".format(messageLen)
    clientIP  = "Client IP Address:{}".format(address)
    
    print(clientMsg)
    #print(clientIP)

    # Use default library installation
    #jpeg = TurboJPEG()

    # If you run an interactive ipython session, and want to use highgui windows, do cv2.startWindowThread() first.
    # In detail: HighGUI is a simplified interface to display images and video from OpenCV code.
    cv2.startWindowThread()
    cv2.namedWindow("preview")
    # Sending a reply to client
    #UDPServerSocket.sendto(bytesToSend, address)
    if (messageLen > 20):
       #display image       
       #buffer = io.BytesIO(message)
       #buffer.seek(0)
       #inp = np.asarray(bytearray(message), dtype=np.uint8)
       i = cv2.imdecode(np.frombuffer(message, dtype=np.uint8), cv2.IMREAD_COLOR)
       #i = jpeg.decode(message)       
       cv2.imshow("preview", i)       
       #cv2.waitKey(0)
    