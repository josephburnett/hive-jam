import asyncio
import queue
import threading
import time
import websockets

from pythonosc import dispatcher
from pythonosc import osc_message_builder
from pythonosc import osc_server
from pythonosc import udp_client

dispatcher = dispatcher.Dispatcher()
dispatcher.map("/state", print)

server = osc_server.ThreadingOSCUDPServer(('127.0.0.1', 4560), dispatcher)
server_thread = threading.Thread(target=server.serve_forever)
server_thread.daemon = True
server_thread.start()
print("Serving OSC on {}".format(server.server_address))

client = udp_client.UDPClient('127.0.0.1', 4557)

q = queue.Queue()
async def producer():
    msg = q.get()
    print("Producing message {}".format(msg))
    return msg

async def consumer(msg):
    print("Consuming message {}".format(msg))
    msg = osc_message_builder.OscMessageBuilder(address = msg)
    client.send(msg.build())

async def handler(websocket, path):
    print("Client connected")
    try:
        while True:
            print("DEBUG Handling message")
            listener_task = asyncio.ensure_future(websocket.recv())
            producer_task = asyncio.ensure_future(producer())
            print("DEBUG awaiting listener_task and producer_task")
            done, pending = await asyncio.wait(
                [listener_task],  #, producer_task],
                return_when=asyncio.FIRST_COMPLETED)
            
            if listener_task in done:
                message = listener_task.result()
                print("DEBUG awaiting consumer")
                await consumer(message)
            else:
                listener_task.cancel()
                
            if producer_task in done:
                message = producer_task.result()
                print("DEBUG websocket.send")
                await websocket.send(message)
            else:
                producer_task.cancel()
    finally:
        print("Client disconnected")

async def handler2(websocket, path):
    print("Client connected (2)")
    try:
        while True:
            message = await websocket.recv()
            print("Received message {}".format(message))
            await websocket.send(message)
    finally:
        print("Client disconnected (2)")

start_server = websockets.serve(handler2, '127.0.0.1', 4550)
print("Starting websockets server")
asyncio.get_event_loop().run_until_complete(start_server)
print("Running bridge")
asyncio.get_event_loop().run_forever()

print("Shutting down OSC server")
server.shutdown()
server.server_close()

