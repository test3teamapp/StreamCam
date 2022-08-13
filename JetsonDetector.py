import time

import cv2
import os

from multiprocessing import Process
from multiprocessing import Queue
from enum import Enum

# for jetson
import jetson.inference
import jetson.utils
import sys
import cv2

_DEBUG = True


class DETECT_PROCESS_STATE(Enum):
    STOPPED = 1
    RUNNING = 2


class JetsonDetector:
    """Class for proccessing images on coral TPU """

    # variables here are common to all instances of the class #

    def __init__(self, sharedImageQueue: Queue):
        self.detectProcess = Process()
        # create queues for accessing the state variable from the new process
        # and send the images received by the TCP process for processing
        self.imageQueue = sharedImageQueue
        self.stateQueue = Queue()
        self.startDetectProcess = False
        self.detectProcessState = DETECT_PROCESS_STATE.STOPPED
        self.stateQueue.put(self.detectProcessState)

    def append_objs_to_img(self, cv2_im, inference_size, objs, labels):
        height, width, channels = cv2_im.shape
        scale_x, scale_y = width / \
            inference_size[0], height / inference_size[1]
        for obj in objs:
            bbox = obj.bbox.scale(scale_x, scale_y)
            x0, y0 = int(bbox.xmin), int(bbox.ymin)
            x1, y1 = int(bbox.xmax), int(bbox.ymax)

            percent = int(100 * obj.score)
            label = '{}% {}'.format(percent, labels.get(obj.id, obj.id))

            cv2_im = cv2.rectangle(cv2_im, (x0, y0), (x1, y1), (0, 255, 0), 2)
            cv2_im = cv2.putText(cv2_im, label, (x0, y0+30),
                                 cv2.FONT_HERSHEY_SIMPLEX, 1.0, (255, 0, 0), 2)
        return cv2_im

    def cudaFromCV(self, cv_img):

        #print('OpenCV image size: ' + str(cv_img.shape))
        #print('OpenCV image type: ' + str(cv_img.dtype))

        # convert to CUDA (cv2 images are numpy arrays, in BGR format)
        bgr_img = jetson.utils.cudaFromNumpy(cv_img, isBGR=True)

        #print('BGR image: ')
        #print(bgr_img)

        # convert from BGR -> RGB
        rgb_img = jetson.utils.cudaAllocMapped(width=bgr_img.width,
                                       height=bgr_img.height,
                                       format='rgb8')

        jetson.utils.cudaConvertColor(bgr_img, rgb_img)

        #print('RGB image: ')
        #print(rgb_img)

        return rgb_img


    def detectInImageProcess(self, imgQ: Queue, stateQ: Queue):

        is_headless = ""
        threshold = 0.4
        networkName = "ssd-mobilenet-v2"

        # create video output object
        output = jetson.utils.videoOutput(
            "display://0")  # 'my_video.mp4' for file
        # load the object detection network
        net = jetson.inference.detectNet(
            networkName, sys.argv, threshold=0.5)

        overlayConfig = "box,labels,conf"

        self.detectProcessState = DETECT_PROCESS_STATE.RUNNING
        stateQ.put(self.detectProcessState)

        while (self.startDetectProcess):
            if (not imgQ.empty()):
                # prepare image
                cv2_img = imgQ.get()
                cudaImage = self.cudaFromCV(cv2_img)
                # self.my_print(f"received image size : {cv2_im.size}")
                self.my_print(f"received image dimentions : {cv2_img.shape}")
                # detect objects in the image (with overlay)
                detections = net.Detect(cudaImage, overlay=overlayConfig)

                # print the detections
                print("detected {:d} objects in image".format(
                    len(detections)))

                for detection in detections:
                    print(detection)

                # render the image
                output.Render(cudaImage)

                # update the title bar
                output.SetStatus("{:s} | Network {:.0f} FPS".format(
                    networkName, net.GetNetworkFPS()))

                # print out performance info
                net.PrintProfilerTimes()

        self.detectProcessState = DETECT_PROCESS_STATE.STOPPED
        stateQ.put(self.detectProcessState)
        logging.info("Detect process : finishing")
        print("Detect process : finishing")

    def create_DetectProcess(self):
        # check if TCP Process is running

        # get the last item from the queue. the latest self.tcpState value
        state = DETECT_PROCESS_STATE.STOPPED
        while (not self.stateQueue.empty()):
            state = self.stateQueue.get()
        print(f"create_DetectProcess : latest state from queue : {state}")
        if (state == DETECT_PROCESS_STATE.STOPPED):
            self.startDetectProcess = True
            self.detectProcess = Process(
                target=self.detectInImageProcess, args=(self.imageQueue, self.stateQueue,))
            self.detectProcess.start()

    def terminate_DetectProcess(self):
        self.startDetectProcess = False
        self.detectProcess.terminate()
        self.detectProcess.kill()

    def my_print(self, str):
        if (_DEBUG):
            print(str)
