require 'thread'

# TIME

define :res do Rational('1/32') end

live_loop :main do
  use_bpm 110
  wait res
  root = _state[:root]
  if root.nil?
    puts "[ERROR] no root state"
    next
  end
  t = tick
  bpc = Rational(root[:bpc])
  tpc = bpc / res
  boundary = t % tpc.ceil
  i = (t / tpc).ceil
  #puts "[DEBUG] t: #{t} bpc: #{bpc} tpc: #{tpc} boundary: #{boundary} i: #{i}"
  if boundary != 0
    next
  end
  root[:tracks].each do |track|
    on = (bools *track[:beats])[i]
    if on
      dispatch track
    end
  end
end

# DISPATCH

define :dispatch do |track|
  
  #puts "[DEBUG] dispatching track #{track}"
  
  type = track[:type]
  if type.nil?
    puts "[ERROR] no type for track #{track}"
    return
  end
  params = track[:params]
  if params.nil?
    puts "[ERROR] no params for track #{track}"
    return
  end
  
  if type == "sample"
    s = track[:sample].to_sym
    if s.nil?
      puts "[ERROR] no sample for track #{track}"
      return
    end
    fx = track[:fx]
    if not fx.nil?
      with_fx fx.to_sym do
        sample s, **params
      end
    else
      sample s, **params
    end
    return
  end
  
  if type == "play"
    n = track[:note]
    if n.nil?
      puts "[ERROR] no note for track #{track}"
    end
    play n, **params
    return
  end
end

# STATE

define :mutex do
  Mutex.new
end

defonce :_state do
  {}
end

define :set_state do |ns, state|
  if _ns_ok(ns)
    mutex.synchronize {
      _state[ns] = state
    }
    send_state_json("*", ns)
  end
end

define :drop_state do |ns|
  if _ns_ok(ns)
    mutex.synchronize {
      _state.delete(ns)
    }
    send_state_json("*", ns)
  end
end

define :drop_all_state do
  mutex.synchronize {
    _state.clear
  }
end

define :get_state_json do |ns|
  mutex.synchronize {
    return JSON.dump(_state[ns])
  }
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
  ns = args[1].intern
  state = JSON.parse(args[2], symbolize_names: true)
  set_state ns, state
end

jam_server.add_method("/get-state") do |args|
  assert(args.length == 2)
  client_id = args[0]
  ns = args[1].to_sym
  send_state_json(client_id, ns)
end

jam_server.add_method("/ping") do |args|
  assert(args.length == 1)
  drop_all_state
  client_id = args[0]
  jam_client.send("/pong", JSON.dump([client_id]))
end
