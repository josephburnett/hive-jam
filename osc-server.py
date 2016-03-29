from pythonosc import dispatcher
from pythonosc import osc_server

dispatcher = dispatcher.Dispatcher()
dispatcher.map("/state", print)

server = osc_server.ThreadingOSCUDPServer(('127.0.0.1', 4560), dispatcher)

print("Serving on {}".format(server.server_address))
server.serve_forever()
