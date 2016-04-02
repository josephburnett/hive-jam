import sys

from pythonosc import osc_message_builder
from pythonosc import udp_client

if len(sys.argv) < 4:
  print("Usage: osc-client.py <hostname> <port> <path> [<args>]")
  exit(1)

(hostname, port, address) = sys.argv[1:4]

client = udp_client.UDPClient(hostname, int(port))

msg = osc_message_builder.OscMessageBuilder(address = address)
for arg in sys.argv[4:]:
  msg.add_arg(arg)
msg = msg.build()

print("Sending message {}".format(msg))
client.send(msg)
