# TIME

define :res do Rational('1/8') end

define :verbosity do 1 end

live_loop :main do
  use_bpm 150
  use_cue_logging (verbosity > 1) ? true : false
  use_debug (verbosity > 0) ? true : false
  wait res
  root = _state.get_state :root
  if root.nil?
    puts "[ERROR] no root state"
    next
  end
  t = tick
  _dispatch.dispatch t
end

# USER STATE FUNCTIONS

define :save_state do |filename|
  _state.save_state filename
end

define :load_state do |filename|
  _state.load_state filename
end

# STATE

defonce :_state do
  SonicJam::State.new
end

# DISPATCH

define :_dispatch do
  def to_proc(&proc)
    proc
  end
  SonicJam::Dispatch.new(_state, "1/32",
                         to_proc { |fx, **args| with_fx fx, **args },
                         to_proc { |s, **args| synth s, **args },
                         to_proc { |s, **args| sample s, **args })
end


define :set_state do |ns, state|
  if _ns_ok(ns)
    _state.set_state state
    send_state_json("*", ns)
  end
end

define :drop_state do |ns|
  if _ns_ok(ns)
    _state.drop_state ns
    send_state_json("*", ns)
  end
end

define :get_state_json do |ns|
  return JSON.dump(_state.get_state(ns))
end

define :send_state_json do |client_id, ns|
  jam_client.send("/state", JSON.dump([client_id, ns, get_state_json(ns)]))
end

define :_ns_ok do |ns|
  if not ns or not ns.is_a? Symbol
    puts "ns #{ns} is not a symbol"
    return false
  end
  return true
end

# SERVER

defonce :jam_server do
  SonicPi::OSC::UDPServer.new(4559, use_decoder_cache: true)
end

defonce :jam_client do
  SonicPi::OSC::UDPClient.new("127.0.0.1", 4560, use_encoder_cache: true)
end

jam_server.add_method("/drop-state") do |args|
  assert(args.length == 2)
  client_id = args[0]
  ns = args[1].intern
  drop_state ns
end

jam_server.add_method("/set-state") do |args|
  assert(args.length == 3)
  client_id = args[0]
  ns = args[1].to_sym
  state = JSON.parse(args[2], symbolize_names: true)
  set_state ns, state
end

jam_server.add_method("/get-state") do |args|
  assert(args.length == 2)
  client_id = args[0]
  ns = args[1].to_sym
  send_state_json(client_id, ns)
end

jam_server.add_method("/get-samples") do |args|
  assert(args.length == 1)
  client_id = args[0]
  a = all_sample_names
  done = false
  while not done
    b = a.take(10)
    jam_client.send("/samples", JSON.dump([client_id, JSON.dump(Array.new(b))]))
    a = a.drop(10)
    if a.count == 0
      done = true
    end
  end
end

jam_server.add_method("/get-synths") do |args|
  assert(args.length == 1)
  client_id = args[0]
  a = synth_names
  done = false
  while not done
    b = a.take(10)
    jam_client.send("/synths", JSON.dump([client_id, JSON.dump(Array.new(b))]))
    a = a.drop(10)
    if a.count == 0
      done = true
    end
  end
end

jam_server.add_method("/ping") do |args|
  assert(args.length == 1)
  client_id = args[0]
  jam_client.send("/pong", JSON.dump([client_id]))
end