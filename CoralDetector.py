import time

import cv2
import os

from multiprocessing import Process
from multiprocessing import Queue
from enum import Enum

# for coral
from pycoral.adapters.common import input_size
from pycoral.adapters.detect import get_objects
from pycoral.utils.dataset import read_label_file
from pycoral.utils.edgetpu import make_interpreter
from pycoral.utils.edgetpu import run_inference

import tflite_runtime.interpreter as tflite

class DETECT_PROCESS_STATE(Enum):
    STOPPED = 1
    RUNNING = 2

class CoralDetector:
    """Class for proccessing images on coral TPU """

    # variables here are common to all instances of the class #


    def __init__(self, sharedImageQueue:Queue):
        self.detectProcess = Process()
        # create queues for accessing the state variable from the new process
        # and send the images received by the TCP process for processing
        self.imageQueue = sharedImageQueue
        self.stateQueue = Queue()
        self.startDetectProcess = False
        self.detectProcessState = DETECT_PROCESS_STATE.STOPPED
        self.stateQueue.put(self.detectProcessState)


    def append_objs_to_img(self,cv2_im, inference_size, objs, labels):
        height, width, channels = cv2_im.shape
        scale_x, scale_y = width / inference_size[0], height / inference_size[1]
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


    def detectInImageProcess(self, imgQ:Queue, stateQ:Queue):
        self.detectProcessState = DETECT_PROCESS_STATE.RUNNING
        stateQ.put(self.detectProcessState)
        labels = read_label_file("test_data/coco_labels.txt") #if args.labels else {}
        interpreter = make_interpreter("test_data/ssd_mobilenet_v2_coco_quant_postprocess_edgetpu.tflite")
        threshold = 0.1
        top_k = 3
        interpreter.allocate_tensors()
        inference_size = input_size(interpreter)

        while(self.startDetectProcess):
            if (not imgQ.empty()):
                #prepare image
                cv2_im = imgQ.get()
                cv2_im_rgb = cv2.cvtColor(cv2_im, cv2.COLOR_BGR2RGB)
                cv2_im_rgb = cv2.resize(cv2_im_rgb, inference_size)
                # inference
                start = time.perf_counter()
                run_inference(interpreter, cv2_im_rgb.tobytes())
                inference_time = time.perf_counter() - start
                #get results
                objs = get_objects(interpreter, threshold)[:top_k]

                print('%.2f ms' % (inference_time * 1000))

                #print('-------RESULTS--------')
                #if not objs:
                #    print('No objects detected')

                #for obj in objs:
                #    print(labels.get(obj.id, obj.id))
                #    print('  id:    ', obj.id)
                #    print('  score: ', obj.score)
                #    print('  bbox:  ', obj.bbox)

                cv2_im = self.append_objs_to_img(cv2_im, inference_size, objs, labels)
                cv2.imshow('frame', cv2_im)
                if cv2.waitKey(1) & 0xFF == ord('q'):
                    break

        self.detectProcessState = DETECT_PROCESS_STATE.STOPPED
        stateQ.put(self.detectProcessState)
        cv2.destroyAllWindows()
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
